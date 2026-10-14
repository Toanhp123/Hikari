import 'dart:async';

import 'package:flutter/foundation.dart';

import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/core/ui/patterns/chapter_list_filter.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/progress/series_continuation.dart';

enum MangaSeriesStatus { loading, ready, error }

@immutable
final class MangaSeriesUiState {
  const MangaSeriesUiState({
    this.status = MangaSeriesStatus.loading,
    this.details,
  });
  final MangaSeriesStatus status;
  final MangaSeriesDetails? details;
  bool get initialLoading =>
      status == MangaSeriesStatus.loading && details == null;
  bool get refreshing => status == MangaSeriesStatus.loading && details != null;
  bool get failed => status == MangaSeriesStatus.error && details == null;
  bool get refreshFailed =>
      status == MangaSeriesStatus.error && details != null;
}

final class MangaSeriesViewModel extends ChangeNotifier {
  MangaSeriesViewModel(
    this._loadDetails, {
    this._loadReadingTarget,
    this.series,
    this._readCachedDetails,
    MediaMetadata? previewMetadata,
  }) : _previewMetadata = previewMetadata {
    if (previewMetadata?.cover case final cover?) {
      if (series != null && cover.sourceId != series!.sourceId) {
        throw ArgumentError.value(
          previewMetadata,
          'previewMetadata',
          'Preview cover belongs to another source.',
        );
      }
    }
  }
  MediaMetadata? _previewMetadata;
  MediaMetadata? get effectiveMetadata =>
      _state.details?.metadata ?? _previewMetadata;
  final SourceMediaRef? series;
  Future<SeriesReadingTarget> Function(List<SourceMediaRef>)?
  _loadReadingTarget;
  MangaChapter? _primaryChapter;
  MangaChapter? get primaryChapter => _primaryChapter;
  bool _isContinuation = false;
  bool get isContinuation => _isContinuation;
  int _continuationGeneration = 0;

  Future<void> refreshReadingTarget() async {
    if (_disposed || _state.details == null) return;
    final generation = ++_continuationGeneration;
    final sequence = _readingSequence;
    final readable = sequence
        .where((chapter) => chapter.canReadPages)
        .toList(growable: false);
    final fallback = readable.firstOrNull;
    if (_primaryChapter != fallback || _isContinuation) {
      _primaryChapter = fallback;
      _isContinuation = false;
      notifyListeners();
    }
    final resolver = _loadReadingTarget;
    SeriesReadingTarget target;
    try {
      target = resolver == null
          ? SeriesReadingTarget(readable.firstOrNull?.source)
          : await resolver(
              readable.map((chapter) => chapter.source).toList(growable: false),
            );
    } catch (_) {
      target = SeriesReadingTarget(readable.firstOrNull?.source);
    }
    if (_disposed ||
        generation != _continuationGeneration ||
        !identical(sequence, _readingSequence)) {
      return;
    }
    final matched = readable
        .where((chapter) => chapter.source == target.chapter)
        .firstOrNull;
    final selected = matched ?? fallback;
    final continuation = matched != null && target.isContinuation;
    if (_primaryChapter == selected && _isContinuation == continuation) return;
    _primaryChapter = selected;
    _isContinuation = continuation;
    notifyListeners();
  }

  Future<MangaSeriesDetails> Function() _loadDetails;
  Future<MangaSeriesDetails?> Function()? _readCachedDetails;

  void updateDependencies(
    Future<MangaSeriesDetails> Function() loadDetails,
    Future<SeriesReadingTarget> Function(List<SourceMediaRef>)? readingTarget, {
    required bool ownerChanged,
    Future<MangaSeriesDetails?> Function()? readCachedDetails,
  }) {
    if (_disposed) return;
    _loadDetails = loadDetails;
    _readCachedDetails = readCachedDetails;
    _loadReadingTarget = readingTarget;
    if (ownerChanged) {
      // A new provider must not retain the previous provider's metadata,
      // even if its first request fails. Ordinary refreshes keep their snapshot.
      _previewMetadata = null;
      _state = const MangaSeriesUiState();
      _readingSequence = const [];
      _displayChapters = const [];
      _primaryChapter = null;
      _isContinuation = false;
      unawaited(load(useCached: true));
    }
  }

  MangaSeriesUiState _state = const MangaSeriesUiState();
  MangaSeriesUiState get state => _state;
  String _searchQuery = '';
  String get searchQuery => _searchQuery;
  bool _reverseSourceOrder = false;
  bool get reverseSourceOrder => _reverseSourceOrder;
  List<MangaChapter> _displayChapters = const [];
  List<MangaChapter> get displayChapters => _displayChapters;
  List<MangaChapter> _readingSequence = const [];
  List<MangaChapter> get readingSequence => _readingSequence;
  int _generation = 0;
  bool _disposed = false;

  void setSearchQuery(String query) {
    if (_disposed || query == _searchQuery) return;
    final previous = _searchQuery.trim().toLowerCase();
    _searchQuery = query;
    if (previous != query.trim().toLowerCase()) _deriveChapters();
    notifyListeners();
  }

  void toggleSourceOrder() {
    if (_disposed) return;
    _reverseSourceOrder = !_reverseSourceOrder;
    _deriveChapters();
    notifyListeners();
  }

  void _deriveChapters() {
    final chapters = _state.details?.chapters ?? const <MangaChapter>[];
    final query = _searchQuery.trim().toLowerCase();
    final filtered = query.isEmpty
        ? chapters
        : chapters
              .where(
                (chapter) => matchesChapterQuery(
                  query,
                  title: chapter.title,
                  chapterNumber: chapter.chapterNumber,
                ),
              )
              .toList(growable: false);
    _displayChapters = _reverseSourceOrder
        ? List.unmodifiable(filtered.reversed)
        : List.unmodifiable(filtered);
  }

  Future<void> load({bool useCached = false}) async {
    if (_disposed) return;
    final generation = ++_generation;
    _continuationGeneration++;
    _primaryChapter = null;
    _isContinuation = false;
    _publish(
      MangaSeriesUiState(
        status: MangaSeriesStatus.loading,
        details: _state.details,
      ),
    );
    try {
      if (useCached && _state.details == null && _readCachedDetails != null) {
        // Initial navigation presents evictable chapters first. Manual refresh
        // skips the cache and loads full metadata directly from the source.
        MangaSeriesDetails? cachedDetails;
        try {
          final snapshot = await _readCachedDetails!();
          if (snapshot != null && series != null) {
            validateSeriesChapterSequence(
              series!,
              snapshot.chapters.map((chapter) => chapter.source),
            );
          }
          cachedDetails = snapshot;
        } catch (_) {
          // A corrupt or evicted snapshot must not block the source.
        }
        if (_disposed || generation != _generation) return;
        if (cachedDetails != null) {
          _state = MangaSeriesUiState(
            status: MangaSeriesStatus.ready,
            details: cachedDetails,
          );
          _readingSequence = cachedDetails.chaptersInReadingOrder;
          _deriveChapters();
          notifyListeners();
          unawaited(refreshReadingTarget());
          // Native Source Detail still needs the source's full metadata.
          // Revalidate without discarding the cached chapter navigation.
          _publish(
            MangaSeriesUiState(
              status: MangaSeriesStatus.loading,
              details: cachedDetails,
            ),
          );
        }
      }
      final details = await Future.sync(_loadDetails);
      if (_disposed || generation != _generation) return;
      if (series case final parent?) {
        validateSeriesChapterSequence(
          parent,
          details.chapters.map((chapter) => chapter.source),
        );
      }
      _previewMetadata = null;
      _state = MangaSeriesUiState(
        status: MangaSeriesStatus.ready,
        details: details,
      );
      _readingSequence = details.chaptersInReadingOrder;
      _deriveChapters();
      _primaryChapter = null;
      _isContinuation = false;
      notifyListeners();
      unawaited(refreshReadingTarget());
    } catch (_) {
      if (_disposed || generation != _generation) return;
      _publish(
        MangaSeriesUiState(
          status: MangaSeriesStatus.error,
          details: _state.details,
        ),
      );
      if (_state.details != null) unawaited(refreshReadingTarget());
    }
  }

  void _publish(MangaSeriesUiState state) {
    if (_disposed) return;
    _state = state;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    super.dispose();
  }
}
