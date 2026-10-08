import 'package:flutter/foundation.dart';

import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';

enum SourceSearchFilter {
  all(null),
  anime(MediaType.anime),
  manga(MediaType.manga),
  novel(MediaType.lightNovel);

  const SourceSearchFilter(this.mediaType);

  final MediaType? mediaType;
}

enum SourceSearchStatus { idle, loading, ready, empty, error }

final class SourceSearchResult {
  const SourceSearchResult({
    required this.media,
    this.sourceName,
    this.metadata,
  });

  final Media media;
  final String? sourceName;
  final MediaMetadata? metadata;
}

@immutable
final class SourceSearchUiState {
  const SourceSearchUiState({
    this.query = '',
    this.inputQuery = '',
    this.filter = SourceSearchFilter.all,
    this.status = SourceSearchStatus.idle,
    this.results = const [],
    this.failedSourceCount = 0,
  });

  /// Query that produced the currently displayed results.
  final String query;

  /// Edited text; changes alone never call remote sources.
  final String inputQuery;
  final SourceSearchFilter filter;
  final SourceSearchStatus status;
  final List<SourceSearchResult> results;
  final int failedSourceCount;

  SourceSearchUiState copyWith({
    String? query,
    String? inputQuery,
    SourceSearchFilter? filter,
    SourceSearchStatus? status,
    List<SourceSearchResult>? results,
    int? failedSourceCount,
  }) {
    return SourceSearchUiState(
      query: query ?? this.query,
      inputQuery: inputQuery ?? this.inputQuery,
      filter: filter ?? this.filter,
      status: status ?? this.status,
      results: results ?? this.results,
      failedSourceCount: failedSourceCount ?? this.failedSourceCount,
    );
  }
}

/// Owns source-search state and fans one query out to relevant configured sources.
final class SourceSearchViewModel extends ChangeNotifier {
  SourceSearchViewModel({
    this._searchManga,
    this._searchNovels,
    this._scanLocalMedia,
    String initialQuery = '',
    SourceSearchFilter initialFilter = SourceSearchFilter.all,
    this._sourceId,
    this._sourceIds,
    this.sourceTimeout = const Duration(seconds: 12),
    this.maxConcurrentSources = 4,
  }) : assert(maxConcurrentSources > 0),
       _state = SourceSearchUiState(
         query: initialQuery.trim(),
         inputQuery: initialQuery.trim(),
         filter: initialFilter,
       );

  final SearchManga? _searchManga;
  final SearchNovels? _searchNovels;
  final Future<List<Media>?> Function()? _scanLocalMedia;
  final SourceId? _sourceId;
  final Set<SourceId>? _sourceIds;

  /// Bounds how long the UI waits; it does not cancel a running extension.
  final Duration sourceTimeout;

  /// Upper bound on concurrently awaited source requests.
  final int maxConcurrentSources;

  SourceSearchUiState _state;
  SourceSearchUiState get state => _state;

  bool _disposed = false;
  int _generation = 0;
  Future<List<Media>>? _localCatalogFuture;

  void updateQuery(String rawQuery) {
    final inputQuery = rawQuery.trim();
    if (_state.inputQuery == inputQuery) return;
    // Editing invalidates even an in-flight submitted request.
    ++_generation;
    _publish(
      _state.copyWith(
        inputQuery: inputQuery,
        status: SourceSearchStatus.idle,
        results: const [],
        failedSourceCount: 0,
      ),
    );
  }

  Future<void> selectFilter(SourceSearchFilter filter) async {
    if (_state.filter == filter) return;
    ++_generation;
    _publish(
      _state.copyWith(
        filter: filter,
        results: const [],
        status: SourceSearchStatus.idle,
        failedSourceCount: 0,
      ),
    );
    if (_state.query.isNotEmpty && _state.inputQuery == _state.query) {
      await submitQuery(_state.query);
    }
  }

  /// Kept as an alias for existing callers of the programmatic search API.
  Future<void> search(String rawQuery) => submitQuery(rawQuery);

  Future<void> submitQuery(String rawQuery) async {
    final query = rawQuery.trim();
    if (query.isEmpty) {
      _generation++;
      _publish(
        SourceSearchUiState(
          filter: _state.filter,
          status: SourceSearchStatus.idle,
        ),
      );
      return;
    }

    final generation = ++_generation;
    _publish(
      _state.copyWith(
        query: query,
        inputQuery: query,
        results: const [],
        status: SourceSearchStatus.loading,
        failedSourceCount: 0,
      ),
    );

    final filter = _state.filter;
    final tasks = <Future<_SearchBatch> Function()>[
      if (filter.mediaType == null || filter.mediaType == MediaType.manga)
        ..._mangaTasks(query),
      if (filter.mediaType == null || filter.mediaType == MediaType.lightNovel)
        ..._novelTasks(query),
      if (_sourceId == null && _sourceIds == null && _scanLocalMedia != null)
        () => _searchLocal(query, filter),
    ];

    if (tasks.isEmpty) {
      if (_disposed || generation != _generation) return;
      _publish(
        _state.copyWith(
          query: query,
          results: const [],
          status: _sourceId == null
              ? SourceSearchStatus.empty
              : SourceSearchStatus.error,
          failedSourceCount: _sourceId == null ? 0 : 1,
        ),
      );
      return;
    }

    // Bounded workers publish completed batches without waiting for the slowest
    // source. A stale generation never publishes results from an older query.
    var next = 0;
    var completed = 0;
    var failures = 0;
    final accumulated = <SourceSearchResult>[];

    Future<void> worker() async {
      while (next < tasks.length && !_disposed && generation == _generation) {
        final task = tasks[next++];
        final batch = await task().timeout(
          sourceTimeout,
          onTimeout: () => const _SearchBatch([], failed: true),
        );
        if (_disposed || generation != _generation) return;
        completed++;
        if (batch.failed) failures++;
        accumulated.addAll(batch.results);
        final results = _deduplicate(accumulated);
        final allFinished = completed == tasks.length;
        _publish(
          _state.copyWith(
            results: results,
            failedSourceCount: failures,
            status: !allFinished
                ? SourceSearchStatus.loading
                : results.isNotEmpty
                ? SourceSearchStatus.ready
                : failures == tasks.length
                ? SourceSearchStatus.error
                : SourceSearchStatus.empty,
          ),
        );
      }
    }

    await Future.wait([
      for (var i = 0; i < maxConcurrentSources && i < tasks.length; i++)
        worker(),
    ]);
  }

  Future<void> retry() => submitQuery(_state.query);

  Iterable<Future<_SearchBatch> Function()> _mangaTasks(String query) sync* {
    final search = _searchManga;
    if (search == null) return;
    for (final source in search.options.where(
      (source) =>
          (_sourceId == null || source.id == _sourceId) &&
          (_sourceIds == null || _sourceIds.contains(source.id)),
    )) {
      yield () => _guardSource(() async {
        final page = await search.execute(sourceId: source.id, query: query);
        return page.results
            .map(
              (preview) => SourceSearchResult(
                media: preview.media,
                metadata: preview.metadata,
                sourceName: _sourcePresentationLabel(
                  source.displayName,
                  source.languageCode,
                ),
              ),
            )
            .toList(growable: false);
      });
    }
  }

  Iterable<Future<_SearchBatch> Function()> _novelTasks(String query) sync* {
    final search = _searchNovels;
    if (search == null) return;
    for (final source in search.options.where(
      (source) =>
          (_sourceId == null || source.id == _sourceId) &&
          (_sourceIds == null || _sourceIds.contains(source.id)),
    )) {
      yield () => _guardSource(() async {
        final page = await search.execute(sourceId: source.id, query: query);
        return page.results
            .map(
              (preview) => SourceSearchResult(
                media: preview.media,
                metadata: preview.metadata,
                sourceName: _sourcePresentationLabel(
                  source.displayName,
                  source.languageCode,
                ),
              ),
            )
            .toList(growable: false);
      });
    }
  }

  Future<_SearchBatch> _searchLocal(String query, SourceSearchFilter filter) {
    return _guardSource(() async {
      final catalog = await _localCatalog();
      final normalized = query.toLowerCase();
      return catalog
          .where((media) => _matchesFilter(media.type, filter))
          .where((media) => media.title.toLowerCase().contains(normalized))
          .map((media) => SourceSearchResult(media: media))
          .toList(growable: false);
    });
  }

  Future<List<Media>> _localCatalog() {
    final cached = _localCatalogFuture;
    if (cached != null) return cached;

    late final Future<List<Media>> future;
    future = Future.sync(_scanLocalMedia!)
        .then((media) {
          return List<Media>.unmodifiable(media ?? const <Media>[]);
        })
        .catchError((Object error, StackTrace stack) {
          if (identical(_localCatalogFuture, future)) {
            _localCatalogFuture = null;
          }
          Error.throwWithStackTrace(error, stack);
        });
    _localCatalogFuture = future;
    return future;
  }

  Future<_SearchBatch> _guardSource(
    Future<List<SourceSearchResult>> Function() operation,
  ) async {
    try {
      return _SearchBatch(await operation());
    } catch (_) {
      return const _SearchBatch([], failed: true);
    }
  }

  List<SourceSearchResult> _deduplicate(Iterable<SourceSearchResult> results) {
    final seen = <SourceMediaRef>{};
    return List.unmodifiable(
      results.where((result) => seen.add(result.media.source)),
    );
  }

  void _publish(SourceSearchUiState state) {
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

String _sourcePresentationLabel(String displayName, String? languageCode) =>
    switch (languageCode) {
      'all' => '$displayName · Multiple languages',
      final language? => '$displayName · ${language.toUpperCase()}',
      null => displayName,
    };

bool _matchesFilter(MediaType type, SourceSearchFilter filter) =>
    filter.mediaType == null || type == filter.mediaType;

final class _SearchBatch {
  const _SearchBatch(this.results, {this.failed = false});

  final List<SourceSearchResult> results;
  final bool failed;
}
