import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/mangadex/mangadex_client.dart';

final class MangaDexSource
    implements MangaSearchSource, MangaChapterSource, MangaPageSource {
  MangaDexSource({http.Client? client, DateTime Function()? now})
    : _client = MangaDexClient(client: client),
      _now = now ?? DateTime.now;
  static const sourceId = SourceId('mangadex');
  static final _uuid = RegExp(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
  );
  final MangaDexClient _client;
  final DateTime Function() _now;
  _ChapterPageSession? _chapterSession;
  @override
  SourceId get id => sourceId;
  @override
  String get name => 'MangaDex';
  void close() {
    _chapterSession = null;
    _client.close();
  }

  static Map<String, dynamic> _expectObject(Object? value) {
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Invalid MangaDex object.');
    }
    return value;
  }

  static String _expectText(Object? value) {
    if (value is! String || value.trim().isEmpty) {
      throw const FormatException('Missing MangaDex text.');
    }
    return value;
  }

  static List<dynamic> _expectList(Object? value) {
    if (value is! List) throw const FormatException('Missing MangaDex list.');
    return value;
  }

  static String _expectUuid(Object? value) {
    final text = _expectText(value);
    if (!_uuid.hasMatch(text)) {
      throw const FormatException('Invalid MangaDex UUID.');
    }
    return text;
  }

  String _requireOwnReference(SourceMediaRef ref) {
    if (ref.sourceId != id) throw ArgumentError('Wrong source.');
    return _expectUuid(ref.itemId);
  }

  @override
  Future<List<Media>> search(String query) async {
    if (query.trim().isEmpty) return [];
    final body = await _client.getApi('/manga', {
      'title': query.trim(),
      'limit': '20',
      'availableTranslatedLanguage[]': ['en'],
      'hasAvailableChapters': 'true',
      'contentRating[]': ['safe', 'suggestive'],
    });
    final data = _expectList(body['data']);
    if (data.length > 20) {
      throw const FormatException('Excessive MangaDex search results.');
    }
    return data.map((value) {
      final row = _expectObject(value);
      final titles = _expectObject(_expectObject(row['attributes'])['title']);
      final keys = titles.keys.toList()..sort();
      final title =
          titles['en'] is String && (titles['en'] as String).trim().isNotEmpty
          ? titles['en'] as String
          : keys
                .map((key) => titles[key])
                .whereType<String>()
                .firstWhere(
                  (t) => t.trim().isNotEmpty,
                  orElse: () => 'Untitled manga',
                );
      return Media(
        title: title,
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: id, itemId: _expectUuid(row['id'])),
      );
    }).toList();
  }

  @override
  Future<List<MangaChapter>> chapters(SourceMediaRef manga) async {
    final uuid = _requireOwnReference(manga);
    final chapters = <MangaChapter>[];
    var offset = 0;
    for (var request = 0; request < 20; request++) {
      final body = await _client.getApi('/manga/$uuid/feed', {
        'limit': '500',
        'offset': '$offset',
        'translatedLanguage[]': ['en'],
        'includes[]': ['scanlation_group'],
        'order[volume]': 'asc',
        'order[chapter]': 'asc',
        'includeFuturePublishAt': '0',
        'contentRating[]': ['safe', 'suggestive'],
      });
      final data = _expectList(body['data']);
      final total = body['total'];
      if (total is! int || total < 0 || total > 10000 || data.length > 500) {
        throw const FormatException('Invalid or excessive MangaDex feed.');
      }
      for (final value in data) {
        final row = _expectObject(value),
            attributes = _expectObject(_expectObject(value)['attributes']);
        final chapterId = _expectUuid(row['id']);
        final count = attributes['pages'];
        if (count is! int || count < 0) {
          throw const FormatException('Invalid chapter page count.');
        }
        if (attributes['isUnavailable'] != null &&
            attributes['isUnavailable'] is! bool) {
          throw const FormatException('Invalid availability.');
        }
        final readable = DateTime.tryParse(
          _expectText(attributes['readableAt']),
        );
        if (readable == null) {
          throw const FormatException('Invalid readable date.');
        }
        final externalUrl = attributes['externalUrl'];
        if (externalUrl != null && externalUrl is! String) {
          throw const FormatException('Invalid external chapter URL.');
        }
        if (attributes['isUnavailable'] == true ||
            readable.isAfter(_now()) ||
            attributes['translatedLanguage'] != 'en' ||
            (externalUrl == null && count == 0)) {
          continue;
        }
        final groups = <String>[];
        for (final relationship in _expectList(row['relationships'])) {
          final relation = _expectObject(relationship);
          if (relation['type'] == 'scanlation_group' &&
              relation['attributes'] != null) {
            groups.add(
              _expectText(_expectObject(relation['attributes'])['name']),
            );
          }
        }
        final parts = <String>[];
        for (final field in ['volume', 'chapter', 'title']) {
          final text = attributes[field];
          if (text != null && text is! String) {
            throw const FormatException('Invalid chapter title.');
          }
          if (text is String && text.trim().isNotEmpty) {
            parts.add(
              '${field == 'volume'
                  ? 'Vol. '
                  : field == 'chapter'
                  ? 'Ch. '
                  : ''}${text.trim()}',
            );
          }
        }
        chapters.add(
          MangaChapter(
            title: parts.isEmpty ? 'Oneshot' : parts.join(' · '),
            source: SourceMediaRef(sourceId: id, itemId: chapterId),
            scanlator: groups.isEmpty ? null : groups.join(', '),
            canReadPages: externalUrl == null,
          ),
        );
      }
      offset += data.length;
      if (offset >= total) return chapters;
      if (data.isEmpty) {
        throw const FormatException('MangaDex pagination made no progress.');
      }
    }
    throw StateError('MangaDex chapter feed exceeds pagination limit.');
  }

  Future<_ChapterPageSession> _resolveChapterSession(String chapter) async {
    final cached = _chapterSession;
    if (cached != null &&
        cached.id == chapter &&
        _now().difference(cached.resolvedAt) < const Duration(minutes: 15)) {
      return cached;
    }
    final body = await _client.getApi('/at-home/server/$chapter');
    final base = _expectText(body['baseUrl']);
    final uri = Uri.tryParse(base);
    if (uri == null ||
        uri.scheme != 'https' ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment) {
      throw const FormatException('Invalid image host.');
    }
    final metadata = _expectObject(body['chapter']);
    final hash = _expectText(metadata['hash']);
    final files = _expectList(metadata['data']).map(_expectText).toList();
    if (files.isEmpty ||
        files.length > 10000 ||
        [hash, ...files].any(
          (s) =>
              s.contains('/') ||
              s.contains('\\') ||
              s == '..' ||
              s == '.' ||
              s.contains('%') ||
              s.contains('?') ||
              s.contains('#'),
        )) {
      throw const FormatException('Invalid chapter images.');
    }
    return _chapterSession = _ChapterPageSession(
      chapter,
      base,
      hash,
      files,
      _now(),
    );
  }

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async {
    final chapter = _requireOwnReference(readable);
    final session = await _resolveChapterSession(chapter);
    return List.generate(
      session.files.length,
      (index) => SourceMediaRef(sourceId: id, itemId: '$chapter/$index'),
    );
  }

  void _reportImage(Uri url, http.Response? response, Duration duration) {
    if (url.host == 'mangadex.org' || url.host.endsWith('.mangadex.org')) {
      return;
    }
    unawaited(() async {
      try {
        final report =
            http.Request(
                'POST',
                Uri.parse('https://api.mangadex.network/report'),
              )
              ..headers['Content-Type'] = 'application/json'
              ..body = jsonEncode({
                'url': url.toString(),
                'success': response?.statusCode == 200,
                'cached':
                    response?.headers['x-cache']?.startsWith('HIT') ?? false,
                'bytes': response?.bodyBytes.length ?? 0,
                'duration': duration.inMilliseconds,
              });
        await _client.request(report, timeout: const Duration(seconds: 5));
      } catch (_) {
        // MangaDex@Home reporting is best-effort and never blocks page display.
      }
    }());
  }

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async {
    if (page.sourceId != id) throw ArgumentError('Wrong source.');
    final parts = page.itemId.split('/');
    if (parts.length != 2) {
      throw const FormatException('Invalid page reference.');
    }
    final chapter = _expectUuid(parts[0]);
    final index = int.tryParse(parts[1]);
    if (index == null || index < 0) {
      throw const FormatException('Invalid page index.');
    }
    for (var attempt = 0; attempt < 2; attempt++) {
      final session = await _resolveChapterSession(chapter);
      if (index >= session.files.length) {
        throw StateError('Page no longer available.');
      }
      final url = Uri.parse(
        '${session.base}/data/${session.hash}/${session.files[index]}',
      );
      final watch = Stopwatch()..start();
      http.Response? response;
      try {
        response = await _client.request(
          http.Request('GET', url),
          cap: 32 * 1024 * 1024,
        );
      } finally {
        watch.stop();
        if (response == null || response.statusCode != 200) {
          _chapterSession = null;
        }
        _reportImage(url, response, watch.elapsed);
      }
      if (response.statusCode == 403 && attempt == 0) continue;
      _client.checkStatus(response.statusCode);
      if (response.statusCode != 200) {
        throw StateError(
          'MangaDex image returned HTTP ${response.statusCode}.',
        );
      }
      return response.bodyBytes;
    }
    throw StateError('MangaDex image unavailable.');
  }
}

final class _ChapterPageSession {
  const _ChapterPageSession(
    this.id,
    this.base,
    this.hash,
    this.files,
    this.resolvedAt,
  );
  final String id, base, hash;
  final List<String> files;
  final DateTime resolvedAt;
}
