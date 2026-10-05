import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';

final class ReadNovelChapterContent {
  static const cacheNamespace = 'novel-chapter-content-v1';

  ReadNovelChapterContent(this._cache);

  final ByteCache _cache;
  final Map<SourceMediaRef, _ContentFlight> _inFlight = {};

  Future<RichReadingContent> execute(
    NovelChapterSource source,
    SourceMediaRef chapter,
  ) {
    if (chapter.sourceId != source.id) {
      return Future.error(
        StateError('Novel source received foreign chapter reference.'),
      );
    }
    final current = _inFlight[chapter];
    if (current != null) return current.future;
    return _start(source, chapter, reload: false);
  }

  Future<RichReadingContent> reload(
    NovelChapterSource source,
    SourceMediaRef chapter,
  ) {
    if (chapter.sourceId != source.id) {
      return Future.error(
        StateError('Novel source received foreign chapter reference.'),
      );
    }
    final current = _inFlight[chapter];
    if (current?.reload == true) return current!.future;
    return _start(source, chapter, reload: true, predecessor: current?.future);
  }

  Future<RichReadingContent> _start(
    NovelChapterSource source,
    SourceMediaRef chapter, {
    required bool reload,
    Future<RichReadingContent>? predecessor,
  }) {
    final flight = _ContentFlight(reload: reload);
    _inFlight[chapter] = flight;
    flight.future =
        _load(
          source,
          chapter,
          reload: reload,
          predecessor: predecessor,
        ).whenComplete(() {
          if (identical(_inFlight[chapter], flight)) _inFlight.remove(chapter);
        });
    return flight.future;
  }

  Future<RichReadingContent> _load(
    NovelChapterSource source,
    SourceMediaRef chapter, {
    required bool reload,
    Future<RichReadingContent>? predecessor,
  }) async {
    if (predecessor != null) {
      try {
        await predecessor;
      } catch (_) {
        // Fresh reads run even when prior source read failed.
      }
    }
    if (!reload) {
      try {
        final cached = await _cache.read(cacheNamespace, cacheKey(chapter));
        if (cached != null) {
          final content = _decode(cached, chapter);
          if (content != null && _hasContent(content.html)) {
            _validateResources(source, content);
            return content;
          }
        }
      } catch (_) {
        // Cache failures never prevent source reads.
      }
    }
    final content = await source.chapterContent(chapter);
    _validateResources(source, content);
    if (_hasContent(content.html)) {
      try {
        await _cache.write(
          cacheNamespace,
          cacheKey(chapter),
          _encode(content, chapter),
        );
      } catch (_) {
        // Cache failures never hide successfully loaded source content.
      }
    }
    return content;
  }

  static String cacheKey(SourceMediaRef chapter) =>
      jsonEncode([chapter.sourceId.value, chapter.itemId]);

  static bool _hasContent(String html) {
    final text = html
        .replaceAll(RegExp(r'<[^>]*>'), '')
        .replaceAll(
          RegExp(r'&(?:nbsp|#0*160|#x0*a0);', caseSensitive: false),
          ' ',
        )
        .replaceAll(RegExp(r'&#0*32;'), ' ')
        .replaceAll(RegExp(r'&#x0*20;', caseSensitive: false), ' ')
        .trim();
    return text.isNotEmpty;
  }

  static void _validateResources(
    NovelChapterSource source,
    RichReadingContent content,
  ) {
    if (content.resources.values.any((ref) => ref.sourceId != source.id)) {
      throw StateError('Novel source returned foreign resources.');
    }
  }

  static Uint8List _encode(
    RichReadingContent content,
    SourceMediaRef chapter,
  ) => Uint8List.fromList(
    utf8.encode(
      jsonEncode({
        'version': 1,
        'sourceId': chapter.sourceId.value,
        'itemId': chapter.itemId,
        'html': content.html,
        'resources': content.resources.map(
          (key, ref) => MapEntry(key, {
            'sourceId': ref.sourceId.value,
            'itemId': ref.itemId,
          }),
        ),
      }),
    ),
  );

  static RichReadingContent? _decode(Uint8List bytes, SourceMediaRef chapter) {
    try {
      final value = jsonDecode(utf8.decode(bytes));
      if (value is! Map<String, dynamic> ||
          value['version'] != 1 ||
          value['sourceId'] != chapter.sourceId.value ||
          value['itemId'] != chapter.itemId ||
          value['html'] is! String ||
          value['resources'] is! Map<String, dynamic>) {
        return null;
      }
      final resources = <String, SourceMediaRef>{};
      for (final entry
          in (value['resources'] as Map<String, dynamic>).entries) {
        final item = entry.value;
        if (item is! Map<String, dynamic> ||
            item['sourceId'] is! String ||
            item['itemId'] is! String) {
          return null;
        }
        resources[entry.key] = SourceMediaRef(
          sourceId: SourceId(item['sourceId'] as String),
          itemId: item['itemId'] as String,
        );
      }
      return RichReadingContent(
        html: value['html'] as String,
        resources: resources,
      );
    } catch (_) {
      return null;
    }
  }
}

final class _ContentFlight {
  _ContentFlight({required this.reload});
  final bool reload;
  late final Future<RichReadingContent> future;
}
