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
    this.filter = SourceSearchFilter.all,
    this.status = SourceSearchStatus.idle,
    this.results = const [],
    this.failedSourceCount = 0,
  });

  final String query;
  final SourceSearchFilter filter;
  final SourceSearchStatus status;
  final List<SourceSearchResult> results;
  final int failedSourceCount;

  SourceSearchUiState copyWith({
    String? query,
    SourceSearchFilter? filter,
    SourceSearchStatus? status,
    List<SourceSearchResult>? results,
    int? failedSourceCount,
  }) {
    return SourceSearchUiState(
      query: query ?? this.query,
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
  }) : _state = SourceSearchUiState(
         query: initialQuery.trim(),
         filter: initialFilter,
       );

  final SearchManga? _searchManga;
  final SearchNovels? _searchNovels;
  final Future<List<Media>?> Function()? _scanLocalMedia;
  final SourceId? _sourceId;
  final Set<SourceId>? _sourceIds;

  SourceSearchUiState _state;
  SourceSearchUiState get state => _state;

  bool _disposed = false;
  int _generation = 0;
  Future<List<Media>>? _localCatalogFuture;

  Future<void> selectFilter(SourceSearchFilter filter) async {
    if (_state.filter == filter) return;
    _publish(_state.copyWith(filter: filter));
    if (_state.query.isNotEmpty) {
      await search(_state.query);
    }
  }

  Future<void> search(String rawQuery) async {
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
        status: SourceSearchStatus.loading,
        failedSourceCount: 0,
      ),
    );

    final filter = _state.filter;
    final tasks = <Future<_SearchBatch>>[
      if (filter.mediaType == null || filter.mediaType == MediaType.manga)
        ..._mangaTasks(query),
      if (filter.mediaType == null || filter.mediaType == MediaType.lightNovel)
        ..._novelTasks(query),
      if (_sourceId == null && _sourceIds == null && _scanLocalMedia != null)
        _searchLocal(query, filter),
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

    final batches = await Future.wait(tasks);
    if (_disposed || generation != _generation) return;

    final failedSourceCount = batches.where((batch) => batch.failed).length;
    final results = _deduplicate(batches.expand((batch) => batch.results));

    if (results.isEmpty && failedSourceCount == batches.length) {
      _publish(
        _state.copyWith(
          query: query,
          results: const [],
          status: SourceSearchStatus.error,
          failedSourceCount: failedSourceCount,
        ),
      );
      return;
    }

    _publish(
      _state.copyWith(
        query: query,
        results: results,
        status: results.isEmpty
            ? SourceSearchStatus.empty
            : SourceSearchStatus.ready,
        failedSourceCount: failedSourceCount,
      ),
    );
  }

  Future<void> retry() => search(_state.query);

  Iterable<Future<_SearchBatch>> _mangaTasks(String query) sync* {
    final search = _searchManga;
    if (search == null) return;
    for (final source in search.options.where(
      (source) =>
          (_sourceId == null || source.id == _sourceId) &&
          (_sourceIds == null || _sourceIds.contains(source.id)),
    )) {
      yield _guardSource(() async {
        final page = await search.execute(sourceId: source.id, query: query);
        return page.results
            .map(
              (preview) => SourceSearchResult(
                media: preview.media,
                metadata: preview.metadata,
                sourceName: source.displayName,
              ),
            )
            .toList(growable: false);
      });
    }
  }

  Iterable<Future<_SearchBatch>> _novelTasks(String query) sync* {
    final search = _searchNovels;
    if (search == null) return;
    for (final source in search.options.where(
      (source) =>
          (_sourceId == null || source.id == _sourceId) &&
          (_sourceIds == null || _sourceIds.contains(source.id)),
    )) {
      yield _guardSource(() async {
        final page = await search.execute(sourceId: source.id, query: query);
        return page.results
            .map(
              (preview) => SourceSearchResult(
                media: preview.media,
                metadata: preview.metadata,
                sourceName: source.displayName,
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

bool _matchesFilter(MediaType type, SourceSearchFilter filter) =>
    filter.mediaType == null || type == filter.mediaType;

final class _SearchBatch {
  const _SearchBatch(this.results, {this.failed = false});

  final List<SourceSearchResult> results;
  final bool failed;
}
