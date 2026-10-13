import 'dart:convert';
import 'dart:typed_data';

import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/progress/series_continuation.dart';

/// Reconstructible remote series chapter sequences, never user progress or
/// durable downloads. Chapters are stored in canonical *reading* order.
/// Source-private chapter references must remain valid after app restart.
final class RemoteChapterListSnapshot<T> {
  RemoteChapterListSnapshot({
    required List<T> chapters,
    required this.fetchedAt,
    required this.chapterListOrder,
  }) : chapters = List.unmodifiable(chapters);

  /// Canonical reading order, independent of display order.
  final List<T> chapters;
  final DateTime fetchedAt;
  final ChapterListOrder chapterListOrder;
}

final class ReadRemoteChapterList {
  ReadRemoteChapterList(this._cache, {DateTime Function()? clock})
    : _clock = clock ?? DateTime.now;

  final DateTime Function() _clock;
  static const freshness = Duration(hours: 1);
  bool isFresh(DateTime fetchedAt) {
    final age = _clock().toUtc().difference(fetchedAt);
    return !age.isNegative && age < freshness;
  }

  static const cacheNamespace = 'remote-chapter-list-v1';
  static const _version = 1;
  static const _maxChapters = 50000;
  static const _maxBytes = 2 * 1024 * 1024;

  final ByteCache _cache;
  final Map<String, int> _latestRequests = {};
  int _nextRequest = 0;

  /// Prevent an older source response from overwriting a newer request's
  /// snapshot. This tracks only active requests, not a permanent per-series map.
  int beginRefresh(SourceMediaRef series, MediaType type) {
    final request = ++_nextRequest;
    _latestRequests[_key(series, _type(type))] = request;
    return request;
  }

  void finishRefresh(SourceMediaRef series, MediaType type, int request) {
    final key = _key(series, _type(type));
    if (_latestRequests[key] == request) _latestRequests.remove(key);
  }

  Future<List<MangaChapter>?> readManga(SourceMediaRef series) async =>
      (await readMangaSnapshot(series))?.chapters;

  Future<RemoteChapterListSnapshot<MangaChapter>?> readMangaSnapshot(
    SourceMediaRef series,
  ) async {
    final data = await _read(series, 'manga');
    if (data == null) return null;
    try {
      final chapters = <MangaChapter>[];
      for (final item in data['chapters'] as List<dynamic>) {
        if (item is! List<dynamic> ||
            item.length != 6 ||
            item[0] is! String ||
            item[1] is! String ||
            (item[2] != null && item[2] is! String) ||
            (item[3] != null && item[3] is! num) ||
            (item[4] != null && item[4] is! int) ||
            item[5] is! bool) {
          return null;
        }
        chapters.add(
          MangaChapter(
            source: SourceMediaRef(
              sourceId: series.sourceId,
              itemId: item[0] as String,
            ),
            title: item[1] as String,
            scanlator: item[2] as String?,
            chapterNumber: (item[3] as num?)?.toDouble(),
            uploadedAt: item[4] == null
                ? null
                : DateTime.fromMillisecondsSinceEpoch(
                    item[4] as int,
                    isUtc: true,
                  ),
            canReadPages: item[5] as bool,
          ),
        );
      }
      _validate(series, chapters.map((chapter) => chapter.source));
      return RemoteChapterListSnapshot(
        chapters: chapters,
        fetchedAt: DateTime.fromMillisecondsSinceEpoch(
          data['fetchedAt'] as int,
          isUtc: true,
        ),
        chapterListOrder: data['reverseSourceOrder'] == true
            ? ChapterListOrder.reverseReadingOrder
            : ChapterListOrder.readingOrder,
      );
    } catch (_) {
      return null;
    }
  }

  Future<List<NovelChapter>?> readNovel(SourceMediaRef series) async =>
      (await readNovelSnapshot(series))?.chapters;

  Future<RemoteChapterListSnapshot<NovelChapter>?> readNovelSnapshot(
    SourceMediaRef series,
  ) async {
    final data = await _read(series, 'novel');
    if (data == null) return null;
    try {
      final chapters = <NovelChapter>[];
      for (final item in data['chapters'] as List<dynamic>) {
        if (item is! List<dynamic> ||
            item.length != 6 ||
            item[0] is! String ||
            item[1] is! String ||
            (item[2] != null && item[2] is! num) ||
            (item[3] != null && item[3] is! int) ||
            (item[4] != null && item[4] is! String) ||
            item[5] is! List<dynamic> ||
            (item[5] as List<dynamic>).any(
              (scanlator) => scanlator is! String,
            )) {
          return null;
        }
        chapters.add(
          NovelChapter(
            source: SourceMediaRef(
              sourceId: series.sourceId,
              itemId: item[0] as String,
            ),
            title: item[1] as String,
            chapterNumber: (item[2] as num?)?.toDouble(),
            releaseDate: item[3] == null
                ? null
                : DateTime.fromMillisecondsSinceEpoch(
                    item[3] as int,
                    isUtc: true,
                  ),
            releaseLabel: item[4] as String?,
            scanlators: (item[5] as List<dynamic>).cast<String>(),
          ),
        );
      }
      _validate(series, chapters.map((chapter) => chapter.source));
      return RemoteChapterListSnapshot(
        chapters: chapters,
        fetchedAt: DateTime.fromMillisecondsSinceEpoch(
          data['fetchedAt'] as int,
          isUtc: true,
        ),
        chapterListOrder: data['reverseSourceOrder'] == true
            ? ChapterListOrder.reverseReadingOrder
            : ChapterListOrder.readingOrder,
      );
    } catch (_) {
      return null;
    }
  }

  Future<void> persistManga(
    SourceMediaRef series,
    List<MangaChapter> chapters, {
    int? request,
    ChapterListOrder chapterListOrder = ChapterListOrder.readingOrder,
  }) async {
    _validate(series, chapters.map((chapter) => chapter.source));
    await _write(
      series,
      'manga',
      [
        for (final chapter in chapters)
          [
            chapter.source.itemId,
            chapter.title,
            chapter.scanlator,
            chapter.chapterNumber,
            chapter.uploadedAt?.millisecondsSinceEpoch,
            chapter.canReadPages,
          ],
      ],
      request: request,
      chapterListOrder: chapterListOrder,
    );
  }

  Future<void> persistNovel(
    SourceMediaRef series,
    List<NovelChapter> chapters, {
    int? request,
    ChapterListOrder chapterListOrder = ChapterListOrder.readingOrder,
  }) async {
    _validate(series, chapters.map((chapter) => chapter.source));
    await _write(
      series,
      'novel',
      [
        for (final chapter in chapters)
          [
            chapter.source.itemId,
            chapter.title,
            chapter.chapterNumber,
            chapter.releaseDate?.millisecondsSinceEpoch,
            chapter.releaseLabel,
            chapter.scanlators,
          ],
      ],
      request: request,
      chapterListOrder: chapterListOrder,
    );
  }

  Future<Map<String, dynamic>?> _read(
    SourceMediaRef series,
    String type,
  ) async {
    if (!_isRemote(series)) return null;
    try {
      final bytes = await _cache.read(cacheNamespace, _key(series, type));
      if (bytes == null || bytes.length > _maxBytes) return null;
      final data = jsonDecode(utf8.decode(bytes));
      if (data is! Map<String, dynamic> ||
          data['version'] != _version ||
          data['type'] != type ||
          data['source'] != series.sourceId.value ||
          data['series'] != series.itemId ||
          data['fetchedAt'] is! int ||
          (data['reverseSourceOrder'] != null &&
              data['reverseSourceOrder'] is! bool)) {
        return null;
      }
      final chapters = data['chapters'];
      if (chapters is! List<dynamic> || chapters.length > _maxChapters) {
        return null;
      }
      return data;
    } catch (_) {
      // A corrupt or evicted snapshot is never a reader-opening failure.
      return null;
    }
  }

  Future<void> _write(
    SourceMediaRef series,
    String type,
    List<List<Object?>> chapters, {
    int? request,
    required ChapterListOrder chapterListOrder,
  }) async {
    if (!_isRemote(series) || chapters.length > _maxChapters) return;
    final key = _key(series, type);
    if (request != null && _latestRequests[key] != request) return;
    try {
      final bytes = Uint8List.fromList(
        utf8.encode(
          jsonEncode({
            'version': _version,
            'type': type,
            'source': series.sourceId.value,
            'series': series.itemId,
            'fetchedAt': _clock().toUtc().millisecondsSinceEpoch,
            'reverseSourceOrder':
                chapterListOrder == ChapterListOrder.reverseReadingOrder,
            'chapters': chapters,
          }),
        ),
      );
      if (bytes.length <= _maxBytes &&
          (request == null || _latestRequests[key] == request)) {
        // DiskByteCache serializes writes in invocation order. No asynchronous
        // gap may precede this enqueue after checking the request token.
        await _cache.write(cacheNamespace, key, bytes);
      }
    } catch (_) {
      // A successful source fetch must not fail because disk is unavailable.
    }
  }

  static bool _isRemote(SourceMediaRef series) =>
      series.sourceId != SourceId.local &&
      series.sourceId.value.isNotEmpty &&
      series.itemId.isNotEmpty;

  static void _validate(
    SourceMediaRef series,
    Iterable<SourceMediaRef> chapters,
  ) => validateSeriesChapterSequence(series, chapters);

  static String _type(MediaType type) => switch (type) {
    MediaType.manga => 'manga',
    MediaType.lightNovel => 'novel',
    MediaType.anime => throw ArgumentError('Not a chapter-list media type.'),
  };

  static String _key(SourceMediaRef series, String type) =>
      jsonEncode([type, series.sourceId.value, series.itemId]);
}
