import 'dart:convert';

import 'package:flutter/services.dart';

import 'package:html/parser.dart' as html;

import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/reading/safe_html.dart';

class LnReaderNovelSource
    implements
        NovelSearchSource,
        NovelSeriesSource,
        NovelChapterSource,
        ArtworkSource,
        MediaSourcePresentation {
  LnReaderNovelSource(
    this.pluginId,
    this.name,
    this.channel, {
    this.site,
    String? language,
  }) : languageCode = _sourceLanguage(language);

  final String? site;

  @override
  String get displayName => name;

  @override
  final String? languageCode;

  @override
  String? get presentationGroupId => null;

  // The manifest has human language names (e.g. "Vietnamese"), while the
  // shared picker expects a language code. Unknown values must not be guessed.
  static String? _sourceLanguage(String? language) {
    final normalized = language?.trim().toLowerCase().replaceAll('_', '-');
    if (normalized == null || normalized.isEmpty) return null;
    const names = {
      'english': 'en',
      'vietnamese': 'vi',
      'tiếng việt': 'vi',
      'chinese': 'zh',
      'chinese, 汉语, 漢語': 'zh',
      'japanese': 'ja',
      'korean': 'ko',
      'español': 'es',
      'spanish': 'es',
      'français': 'fr',
      'french': 'fr',
      'português': 'pt',
      'portuguese': 'pt',
      'russian': 'ru',
      'русский': 'ru',
      'arabic': 'ar',
      'العربية': 'ar',
      'turkish': 'tr',
      'türkçe': 'tr',
      'thai': 'th',
      'ภาษาไทย': 'th',
      'indonesian': 'id',
      'bahasa indonesia': 'id',
      'german': 'de',
      'deutsch': 'de',
      'italian': 'it',
      'hindi': 'hi',
      'हिन्दी': 'hi',
      'polish': 'pl',
    };
    if (names.containsKey(normalized)) return names[normalized];
    return RegExp(r'^[a-z]{2,3}(?:-[a-z0-9]{2,8})*$').hasMatch(normalized)
        ? normalizeSourceLanguageCode(normalized)
        : null;
  }

  String _validPath(String value) {
    if (value.isEmpty ||
        value.length > 8192 ||
        RegExp(r'[\x00-\x20\x7f]').hasMatch(value)) {
      throw const FormatException('Invalid source path');
    }
    return value;
  }

  String _resourceUrl(String path, [String? chapter]) {
    final base = Uri.parse(site ?? '').resolve(chapter ?? '');
    final uri = base.resolve(path);
    if (uri.scheme != 'https' || uri.host.isEmpty || uri.userInfo.isNotEmpty) {
      throw const FormatException('Invalid resource URL');
    }
    return uri.toString();
  }

  final String pluginId;
  @override
  final String name;
  final MethodChannel channel;
  @override
  SourceId get id => SourceId('lnreader:$pluginId');
  SourceMediaRef _ref(String kind, String path) => SourceMediaRef(
    sourceId: id,
    itemId:
        'lnreader-v1:${base64Url.encode(utf8.encode(jsonEncode([kind, _validPath(path)])))}',
  );
  String _path(SourceMediaRef ref, String kind) {
    if (ref.sourceId != id || !ref.itemId.startsWith('lnreader-v1:')) {
      throw const FormatException('Foreign novel reference');
    }
    final data = jsonDecode(
      utf8.decode(base64Url.decode(ref.itemId.substring(12))),
    );
    if (data is! List ||
        data.length != 2 ||
        data[0] != kind ||
        data[1] is! String) {
      throw const FormatException('Invalid novel reference');
    }
    final path = _validPath(data[1] as String);
    if (_ref(kind, path) != ref) {
      throw const FormatException('Noncanonical reference');
    }
    return path;
  }

  Future<dynamic> _invoke(String method, List<Object> args) async => jsonDecode(
    (await channel.invokeMethod<String>('invoke', {
      'id': pluginId,
      'method': method,
      'args': jsonEncode(args),
    }))!,
  );
  List<String> _names(dynamic value) => value is List
      ? value.cast<String>()
      : value is String
      ? value
            .split(',')
            .map((v) => v.trim())
            .where((v) => v.isNotEmpty)
            .toList()
      : [];
  MediaMetadata _metadata(Map<String, dynamic> row) => MediaMetadata(
    title: row['name'] as String,
    cover: row['cover'] is String
        ? _ref(
            'resource',
            _resourceUrl(row['cover'] as String, row['path'] as String?),
          )
        : null,
    summary: row['summary'] as String?,
    authors: _names(row['author']),
    artists: _names(row['artist']),
    genres: _names(row['genres']),
    rawStatus: row['status'] as String?,
    rating: (row['rating'] as num?)?.toDouble(),
    ratingMax: row['rating'] == null ? null : 5,
    status: switch ((row['status'] as String?)?.toLowerCase()) {
      'ongoing' => PublicationStatus.ongoing,
      'completed' => PublicationStatus.completed,
      'hiatus' => PublicationStatus.onHiatus,
      'cancelled' => PublicationStatus.cancelled,
      _ => PublicationStatus.unknown,
    },
  );
  @override
  Future<NovelSearchPage> search(String query, {int page = 1}) async {
    if (page < 1) throw ArgumentError.value(page);
    final rows = (await _invoke('searchNovels', [query, page])) as List;
    return NovelSearchPage(
      page: page,
      hasNextPage: null,
      results: rows.map((value) {
        final row = Map<String, dynamic>.from(value as Map);
        final metadata = _metadata(row);
        return NovelPreview(
          media: Media(
            title: metadata.title,
            type: MediaType.lightNovel,
            source: _ref('novel', row['path'] as String),
          ),
          metadata: metadata,
        );
      }).toList(),
    );
  }

  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async {
    final path = _path(novel, 'novel');
    final row = Map<String, dynamic>.from(
      await _invoke('parseNovel', [path]) as Map,
    );
    final chapters = List<dynamic>.from(row['chapters'] as List? ?? []);
    final count = row['totalPages'] as int? ?? 1;
    if (count < 1 || count > 100) {
      throw const FormatException('Chapter page limit');
    }
    if (chapters.length > 50000) throw const FormatException('Chapter limit');
    for (var page = 2; page <= count; page++) {
      final next = await _invoke('parsePage', [path, '$page']) as Map;
      chapters.addAll(next['chapters'] as List);
      if (chapters.length > 50000) throw const FormatException('Chapter limit');
    }
    final seen = <String>{};
    return NovelDetails(
      metadata: _metadata(row),
      chapterListOrder: ChapterListOrder.readingOrder,
      chapters: chapters.map((value) {
        final chapter = Map<String, dynamic>.from(value as Map);
        final path = chapter['path'] as String;
        if (!seen.add(path)) {
          throw const FormatException('Duplicate chapter path');
        }
        final release = chapter['releaseTime'] as String?;
        return NovelChapter(
          title: chapter['name'] as String,
          source: _ref('chapter', path),
          chapterNumber: (chapter['chapterNumber'] as num?)?.toDouble(),
          releaseLabel: release,
          releaseDate: release == null ? null : DateTime.tryParse(release),
          scanlators: _names(chapter['scanlator']),
        );
      }).toList(),
    );
  }

  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async {
    final path = _path(chapter, 'chapter');
    final document = html.parseFragment(
      await _invoke('parseChapter', [path]) as String,
    );
    final resources = <String, SourceMediaRef>{};
    for (final image in document.querySelectorAll('img[src]')) {
      try {
        final url = _resourceUrl(image.attributes['src']!, path);
        final key = 'hikari-image-${resources.length}';
        resources[key] = _ref('resource', url);
        image.attributes['src'] = key;
      } on FormatException {
        image.remove();
      }
    }
    return RichReadingContent(
      html: sanitizeNovelHtml(
        document.outerHtml,
        registeredResources: resources.keys.toSet(),
      ),
      resources: resources,
    );
  }

  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async =>
      (await channel.invokeMethod<Uint8List>('readResource', {
        'id': pluginId,
        'url': _path(resource, 'resource'),
      }))!;
  @override
  Future<Uint8List> readArtwork(SourceMediaRef artwork) =>
      readResource(artwork);
}
