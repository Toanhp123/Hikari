import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/read_remote_chapter_list.dart';
import 'package:hikari/core/ui/patterns/chapter_list_filter.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';

/// Route-local chapter selection. Catalog metadata never enters this owner.
final class CatalogChaptersViewModel extends ChangeNotifier {
  CatalogChaptersViewModel({
    required this.openMedia,
    required this.chapterLists,
    required this.loadReadingTarget,
  });

  final OpenMedia openMedia;
  final ReadRemoteChapterList chapterLists;
  final LoadSeriesReadingTarget loadReadingTarget;
  Media? media;
  MediaMetadata? metadata;
  String? sourceName;
  List<MangaChapter> manga = const [];
  List<NovelChapter> novel = const [];
  ChapterListOrder order = ChapterListOrder.readingOrder;
  bool loading = false;
  bool failed = false;
  bool hasChapters = false;
  DateTime? fetchedAt;
  bool fromCache = false;
  bool get cacheStale =>
      fromCache && fetchedAt != null && !chapterLists.isFresh(fetchedAt!);
  bool direct = false;
  bool opening = false;
  String searchQuery = '';
  bool reverseSourceOrder = false;
  SourceMediaRef? primary;
  bool isContinuation = false;
  int _generation = 0;
  int _readingGeneration = 0;
  int _openingGeneration = 0;
  bool _disposed = false;

  List<MangaChapter> get mangaSequence => manga;
  List<NovelChapter> get novelSequence => novel;
  List<MangaChapter> get displayManga =>
      _display(manga, (c) => c.title, (c) => c.chapterNumber);
  List<NovelChapter> get displayNovel =>
      _display(novel, (c) => c.title, (c) => c.chapterNumber);

  List<T> _display<T>(
    List<T> sequence,
    String Function(T) title,
    double? Function(T) number,
  ) {
    Iterable<T> result = order == ChapterListOrder.reverseReadingOrder
        ? sequence.reversed
        : sequence;
    final query = searchQuery.trim().toLowerCase();
    if (query.isNotEmpty) {
      result = result.where(
        (c) => matchesChapterQuery(
          query,
          title: title(c),
          chapterNumber: number(c),
        ),
      );
    }
    if (reverseSourceOrder) result = result.toList().reversed;
    return List.unmodifiable(result);
  }

  void setSearchQuery(String query) {
    searchQuery = query;
    _notify();
  }

  void toggleSourceOrder() {
    reverseSourceOrder = !reverseSourceOrder;
    _notify();
  }

  Future<void> select(Media selected, MediaMetadata? preview) async {
    if (_disposed) return;
    if (preview?.cover case final cover?) {
      if (cover.sourceId != selected.source.sourceId) {
        throw ArgumentError('Foreign preview cover.');
      }
    }
    _generation++;
    _openingGeneration++;
    _readingGeneration++;
    media = selected;
    metadata = preview;
    sourceName = openMedia.sourceNameFor(selected.source.sourceId);
    manga = const [];
    novel = const [];
    hasChapters = false;
    fetchedAt = null;
    fromCache = false;
    direct = false;
    opening = false;
    searchQuery = '';
    reverseSourceOrder = false;
    primary = null;
    isContinuation = false;
    await load();
  }

  bool _current(int generation) => !_disposed && generation == _generation;

  Future<void> load({bool forceRefresh = false}) async {
    final selected = media;
    if (_disposed || selected == null) return;
    final generation = ++_generation;
    loading = true;
    failed = false;
    _notify();
    if (!forceRefresh && !hasChapters) {
      if (selected.type == MediaType.manga) {
        final snapshot = await chapterLists.readMangaSnapshot(selected.source);
        if (!_current(generation)) return;
        if (snapshot != null) {
          manga = snapshot.chapters;
          order = snapshot.chapterListOrder;
          hasChapters = true;
          fetchedAt = snapshot.fetchedAt;
          fromCache = true;
          loading = cacheStale;
        }
      } else {
        final snapshot = await chapterLists.readNovelSnapshot(selected.source);
        if (!_current(generation)) return;
        if (snapshot != null) {
          novel = snapshot.chapters;
          order = snapshot.chapterListOrder;
          hasChapters = true;
          fetchedAt = snapshot.fetchedAt;
          fromCache = true;
          loading = cacheStale;
        }
      }
      if (hasChapters) {
        _notify();
        unawaited(refreshReadingTarget());
        if (!loading) return;
      }
    }
    MediaOpenTarget? target;
    try {
      target = await openMedia.execute(selected, previewMetadata: metadata);
      if (!_current(generation)) return;
      switch (target) {
        case MangaSeriesOpenTarget():
          sourceName = target.seriesSource.name;
          final details = await target.loadDetails();
          if (!_current(generation)) return;
          manga = details.chaptersInReadingOrder;
          metadata = details.metadata;
          order = details.chapterListOrder;
          hasChapters = true;
        case NovelSeriesOpenTarget():
          sourceName = target.source.name;
          final details = await target.loadDetails();
          if (!_current(generation)) return;
          novel = details.chaptersInReadingOrder;
          metadata = details.metadata;
          order = details.chapterListOrder;
          hasChapters = true;
        default:
          direct = true;
      }
      fromCache = false;
      fetchedAt = null;
    } catch (_) {
      if (_current(generation)) failed = true;
    } finally {
      // Target belongs to this operation, including stale/disposed operations.
      try {
        await target?.release();
      } catch (_) {
        if (_current(generation)) failed = true;
      }
      if (_current(generation)) {
        loading = false;
        _notify();
        unawaited(refreshReadingTarget());
      }
    }
  }

  Future<void> refreshReadingTarget() async {
    final selected = media;
    if (_disposed || selected == null || !hasChapters) return;
    final generation = _generation;
    final readingGeneration = ++_readingGeneration;
    final sequence = selected.type == MediaType.manga
        ? manga.where((c) => c.canReadPages).map((c) => c.source).toList()
        : novel.map((c) => c.source).toList();
    primary = sequence.firstOrNull;
    isContinuation = false;
    _notify();
    try {
      final target = await loadReadingTarget.execute(selected.source, sequence);
      if (!_current(generation) || readingGeneration != _readingGeneration) {
        return;
      }
      primary = sequence.contains(target.chapter)
          ? target.chapter
          : sequence.firstOrNull;
      isContinuation =
          sequence.contains(target.chapter) && target.isContinuation;
      _notify();
    } catch (_) {
      // First readable chapter remains valid when progress lookup fails.
    }
  }

  Future<void> open(
    Future<void> Function(Media selected, bool Function() canNavigate)
    operation,
  ) async {
    final selected = media;
    if (_disposed || selected == null || opening) return;
    final openingGeneration = ++_openingGeneration;
    bool current() => !_disposed && openingGeneration == _openingGeneration;
    opening = true;
    _notify();
    try {
      await operation(selected, current);
    } finally {
      if (current()) {
        opening = false;
        _notify();
        unawaited(refreshReadingTarget());
      }
    }
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    _openingGeneration++;
    super.dispose();
  }
}
