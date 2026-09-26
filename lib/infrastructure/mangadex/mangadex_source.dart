import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';

final class MangaDexSource
    implements MediaSearchSource, MangaChapterSource, MangaPageSource {
  MangaDexSource({http.Client? client, DateTime Function()? now})
    : _client = client ?? http.Client(),
      _ownsClient = client == null,
      _now = now ?? DateTime.now;
  static const sourceId = SourceId('mangadex');
  static const _headers = {
    'User-Agent': 'Hikari/0.1 (experimental manga reader)',
  };
  static final _uuid = RegExp(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
  );
  final http.Client _client;
  final bool _ownsClient;
  final DateTime Function() _now;
  _ChapterSession? _session;
  Future<void> _queue = Future.value();
  DateTime? _lastApi;
  DateTime? _lastAtHome;
  @override
  SourceId get id => sourceId;
  @override
  String get name => 'MangaDex';
  bool _closed = false;
  final _pending = <Completer<void>>{};

  void close() {
    _closed = true;
    for (final abort in _pending) {
      if (!abort.isCompleted) abort.complete();
    }
    _session = null;
    if (_ownsClient) _client.close();
  }

  static Map<String, dynamic> _object(Object? value) {
    if (value is! Map<String, dynamic>) {
      throw const FormatException('Invalid MangaDex object.');
    }
    return value;
  }

  static String _string(Object? value) {
    if (value is! String || value.trim().isEmpty) {
      throw const FormatException('Missing MangaDex text.');
    }
    return value;
  }

  static List<dynamic> _list(Object? value) {
    if (value is! List) throw const FormatException('Missing MangaDex list.');
    return value;
  }

  static String _id(Object? value) {
    final text = _string(value);
    if (!_uuid.hasMatch(text)) {
      throw const FormatException('Invalid MangaDex UUID.');
    }
    return text;
  }

  String _reference(SourceMediaRef ref) {
    if (ref.sourceId != id) throw ArgumentError('Wrong source.');
    return _id(ref.itemId);
  }

  static void _status(int status) {
    if (status < 200 || status >= 300) {
      throw StateError(
        status == 429
            ? 'MangaDex rate limit (429). Wait before trying again.'
            : 'MangaDex request failed (HTTP $status).',
      );
    }
  }

  Future<http.Response> _request(
    http.Request request, {
    int cap = 8 * 1024 * 1024,
    Duration timeout = const Duration(seconds: 30),
  }) async {
    if (_closed) throw StateError('MangaDex source is closed.');
    final abort = Completer<void>();
    _pending.add(abort);
    final outgoing =
        http.AbortableRequest(
            request.method,
            request.url,
            abortTrigger: abort.future,
          )
          ..headers.addAll(request.headers)
          ..headers.addAll(_headers)
          ..bodyBytes = request.bodyBytes;
    final timer = Timer(timeout, () {
      if (!abort.isCompleted) abort.complete();
    });
    try {
      return await (() async {
        final response = await _client.send(outgoing);
        final bytes = BytesBuilder(copy: false);
        await for (final chunk in response.stream) {
          if (bytes.length + chunk.length > cap) {
            throw StateError('MangaDex response exceeds size limit.');
          }
          bytes.add(chunk);
        }
        return http.Response.bytes(
          bytes.takeBytes(),
          response.statusCode,
          headers: response.headers,
        );
      })().timeout(timeout);
    } finally {
      timer.cancel();
      if (!abort.isCompleted) abort.complete();
      _pending.remove(abort);
    }
  }

  Future<Map<String, dynamic>> _api(
    String path, [
    Map<String, dynamic>? query,
  ]) {
    final result = _queue.then((_) async {
      final atHome = path.startsWith('/at-home/');
      final now = DateTime.now();
      var wait = _lastApi == null
          ? Duration.zero
          : const Duration(milliseconds: 250) - now.difference(_lastApi!);
      if (atHome && _lastAtHome != null) {
        final homeWait =
            const Duration(milliseconds: 1500) - now.difference(_lastAtHome!);
        if (homeWait > wait) wait = homeWait;
      }
      if (wait > Duration.zero) await Future<void>.delayed(wait);
      _lastApi = DateTime.now();
      if (atHome) _lastAtHome = _lastApi;
      final response = await _request(
        http.Request('GET', Uri.https('api.mangadex.org', path, query)),
      );
      _status(response.statusCode);
      final body = _object(jsonDecode(utf8.decode(response.bodyBytes)));
      if (body['result'] != 'ok') {
        throw const FormatException('MangaDex response was not successful.');
      }
      return body;
    });
    _queue = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return result;
  }

  @override
  Future<List<Media>> search(String query) async {
    if (query.trim().isEmpty) return [];
    final body = await _api('/manga', {
      'title': query.trim(),
      'limit': '20',
      'availableTranslatedLanguage[]': ['en'],
      'hasAvailableChapters': 'true',
      'contentRating[]': ['safe', 'suggestive'],
    });
    final data = _list(body['data']);
    if (data.length > 20) {
      throw const FormatException('Excessive MangaDex search results.');
    }
    return data.map((value) {
      final row = _object(value);
      final titles = _object(_object(row['attributes'])['title']);
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
        source: SourceMediaRef(sourceId: id, itemId: _id(row['id'])),
      );
    }).toList();
  }

  @override
  Future<List<MangaChapter>> chapters(SourceMediaRef manga) async {
    final uuid = _reference(manga);
    final chapters = <MangaChapter>[];
    var offset = 0;
    for (var request = 0; request < 20; request++) {
      final body = await _api('/manga/$uuid/feed', {
        'limit': '500',
        'offset': '$offset',
        'translatedLanguage[]': ['en'],
        'includes[]': ['scanlation_group'],
        'order[volume]': 'asc',
        'order[chapter]': 'asc',
        'includeFuturePublishAt': '0',
        'contentRating[]': ['safe', 'suggestive'],
      });
      final data = _list(body['data']);
      final total = body['total'];
      if (total is! int || total < 0 || total > 10000 || data.length > 500) {
        throw const FormatException('Invalid or excessive MangaDex feed.');
      }
      for (final value in data) {
        final row = _object(value),
            attributes = _object(_object(value)['attributes']);
        final chapterId = _id(row['id']);
        final count = attributes['pages'];
        if (count is! int || count < 0) {
          throw const FormatException('Invalid chapter page count.');
        }
        if (attributes['isUnavailable'] != null &&
            attributes['isUnavailable'] is! bool) {
          throw const FormatException('Invalid availability.');
        }
        final readable = DateTime.tryParse(_string(attributes['readableAt']));
        if (readable == null) {
          throw const FormatException('Invalid readable date.');
        }
        if (attributes['externalUrl'] != null ||
            attributes['isUnavailable'] == true ||
            count == 0 ||
            readable.isAfter(_now()) ||
            attributes['translatedLanguage'] != 'en') {
          continue;
        }
        final groups = <String>[];
        for (final relationship in _list(row['relationships'])) {
          final relation = _object(relationship);
          if (relation['type'] == 'scanlation_group' &&
              relation['attributes'] != null) {
            groups.add(_string(_object(relation['attributes'])['name']));
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

  Future<_ChapterSession> _resolve(String chapter) async {
    final cached = _session;
    if (cached != null &&
        cached.id == chapter &&
        _now().difference(cached.resolvedAt) < const Duration(minutes: 15)) {
      return cached;
    }
    final body = await _api('/at-home/server/$chapter');
    final base = _string(body['baseUrl']);
    final uri = Uri.tryParse(base);
    if (uri == null ||
        uri.scheme != 'https' ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.hasQuery ||
        uri.hasFragment) {
      throw const FormatException('Invalid image host.');
    }
    final metadata = _object(body['chapter']);
    final hash = _string(metadata['hash']);
    final files = _list(metadata['data']).map(_string).toList();
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
    return _session = _ChapterSession(chapter, base, hash, files, _now());
  }

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async {
    final chapter = _reference(readable);
    final session = await _resolve(chapter);
    return List.generate(
      session.files.length,
      (index) => SourceMediaRef(sourceId: id, itemId: '$chapter/$index'),
    );
  }

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async {
    if (page.sourceId != id) throw ArgumentError('Wrong source.');
    final parts = page.itemId.split('/');
    if (parts.length != 2) {
      throw const FormatException('Invalid page reference.');
    }
    final chapter = _id(parts[0]);
    final index = int.tryParse(parts[1]);
    if (index == null || index < 0) {
      throw const FormatException('Invalid page index.');
    }
    for (var attempt = 0; attempt < 2; attempt++) {
      final session = await _resolve(chapter);
      if (index >= session.files.length) {
        throw StateError('Page no longer available.');
      }
      final url = Uri.parse(
        '${session.base}/data/${session.hash}/${session.files[index]}',
      );
      final watch = Stopwatch()..start();
      http.Response? response;
      try {
        response = await _request(
          http.Request('GET', url),
          cap: 32 * 1024 * 1024,
        );
      } finally {
        watch.stop();
        if (response == null || response.statusCode != 200) _session = null;
        if (url.host != 'mangadex.org' && !url.host.endsWith('.mangadex.org')) {
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
                        response?.headers['x-cache']?.startsWith('HIT') ??
                        false,
                    'bytes': response?.bodyBytes.length ?? 0,
                    'duration': watch.elapsedMilliseconds,
                  });
            await _request(report, timeout: const Duration(seconds: 5));
          } catch (_) {
            /* Reporting must not discard a downloaded page. */
          }
        }
      }
      if (response.statusCode == 403 && attempt == 0) continue;
      _status(response.statusCode);
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

final class _ChapterSession {
  const _ChapterSession(
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
