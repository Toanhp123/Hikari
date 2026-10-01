import 'package:flutter/foundation.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';

enum SearchMediaTypeFilter {
  all('All'),
  anime('Anime'),
  manga('Manga'),
  novel('Light Novels');

  const SearchMediaTypeFilter(this.label);
  final String label;
}

enum UnifiedSearchStatus { idle, loading, ready, empty, error }

final class UnifiedSearchResult {
  const UnifiedSearchResult({
    required this.media,
    required this.sourceName,
    this.metadata,
  });

  final Media media;
  final String sourceName;
  final MediaMetadata? metadata;
}

@immutable
final class UnifiedSearchUiState {
  const UnifiedSearchUiState({
    this.query = '',
    this.filter = SearchMediaTypeFilter.all,
    this.status = UnifiedSearchStatus.idle,
    this.results = const [],
    this.failedSourceCount = 0,
    this.errorMessage,
  });

  final String query;
  final SearchMediaTypeFilter filter;
  final UnifiedSearchStatus status;
  final List<UnifiedSearchResult> results;
  final int failedSourceCount;
  final String? errorMessage;

  List<UnifiedSearchResult> get visibleResults {
    if (filter == SearchMediaTypeFilter.all) return results;
    return results
        .where((result) {
          return switch (filter) {
            SearchMediaTypeFilter.all => true,
            SearchMediaTypeFilter.anime => result.media.type == MediaType.anime,
            SearchMediaTypeFilter.manga => result.media.type == MediaType.manga,
            SearchMediaTypeFilter.novel =>
              result.media.type == MediaType.lightNovel,
          };
        })
        .toList(growable: false);
  }

  UnifiedSearchUiState copyWith({
    String? query,
    SearchMediaTypeFilter? filter,
    UnifiedSearchStatus? status,
    List<UnifiedSearchResult>? results,
    int? failedSourceCount,
    String? errorMessage,
    bool clearError = false,
  }) {
    return UnifiedSearchUiState(
      query: query ?? this.query,
      filter: filter ?? this.filter,
      status: status ?? this.status,
      results: results ?? this.results,
      failedSourceCount: failedSourceCount ?? this.failedSourceCount,
      errorMessage: clearError ? null : errorMessage ?? this.errorMessage,
    );
  }
}

/// Owns unified-search state and fans one query out to configured sources.
final class UnifiedSearchViewModel extends ChangeNotifier {
  factory UnifiedSearchViewModel({
    SearchManga? searchManga,
    SearchNovels? searchNovels,
    Future<List<Media>?> Function()? scanLocalMedia,
    String initialQuery = '',
    SearchMediaTypeFilter initialFilter = SearchMediaTypeFilter.all,
  }) {
    return UnifiedSearchViewModel._(
      searchManga,
      searchNovels,
      scanLocalMedia,
      initialQuery,
      initialFilter,
    );
  }

  UnifiedSearchViewModel._(
    this._searchManga,
    this._searchNovels,
    this._scanLocalMedia,
    String initialQuery,
    SearchMediaTypeFilter initialFilter,
  ) : _state = UnifiedSearchUiState(
        query: initialQuery.trim(),
        filter: initialFilter,
      );

  final SearchManga? _searchManga;
  final SearchNovels? _searchNovels;
  final Future<List<Media>?> Function()? _scanLocalMedia;

  UnifiedSearchUiState _state;
  UnifiedSearchUiState get state => _state;

  bool _disposed = false;
  int _generation = 0;
  Future<List<Media>>? _localCatalogFuture;

  void selectFilter(SearchMediaTypeFilter filter) {
    if (_state.filter == filter) return;
    _publish(_state.copyWith(filter: filter));
  }

  Future<void> search(String rawQuery) async {
    final query = rawQuery.trim();
    if (query.isEmpty) {
      _generation++;
      _publish(
        UnifiedSearchUiState(
          filter: _state.filter,
          status: UnifiedSearchStatus.idle,
        ),
      );
      return;
    }

    final generation = ++_generation;
    _publish(
      _state.copyWith(
        query: query,
        status: UnifiedSearchStatus.loading,
        failedSourceCount: 0,
        clearError: true,
      ),
    );

    final tasks = <Future<_SearchBatch>>[
      ..._mangaTasks(query),
      ..._novelTasks(query),
      if (_scanLocalMedia != null) _searchLocal(query),
    ];

    if (tasks.isEmpty) {
      if (_disposed || generation != _generation) return;
      _publish(
        _state.copyWith(
          query: query,
          results: const [],
          status: UnifiedSearchStatus.empty,
          failedSourceCount: 0,
          clearError: true,
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
          status: UnifiedSearchStatus.error,
          failedSourceCount: failedSourceCount,
          errorMessage: 'All configured search sources failed. Try again.',
        ),
      );
      return;
    }

    _publish(
      _state.copyWith(
        query: query,
        results: results,
        status: results.isEmpty
            ? UnifiedSearchStatus.empty
            : UnifiedSearchStatus.ready,
        failedSourceCount: failedSourceCount,
        clearError: true,
      ),
    );
  }

  Future<void> retry() => search(_state.query);

  /// Invalidates the session-local SAF catalog after a folder change or rescan.
  Future<void> refreshLocalCatalog() async {
    _localCatalogFuture = null;
    if (_state.query.isNotEmpty) await search(_state.query);
  }

  Iterable<Future<_SearchBatch>> _mangaTasks(String query) sync* {
    final search = _searchManga;
    if (search == null) return;
    for (final source in search.options) {
      yield _guardSource(() async {
        final page = await search.execute(sourceId: source.id, query: query);
        return page.results
            .map(
              (preview) => UnifiedSearchResult(
                media: preview.media,
                metadata: preview.metadata,
                sourceName: source.name,
              ),
            )
            .toList(growable: false);
      });
    }
  }

  Iterable<Future<_SearchBatch>> _novelTasks(String query) sync* {
    final search = _searchNovels;
    if (search == null) return;
    for (final source in search.options) {
      yield _guardSource(() async {
        final page = await search.execute(sourceId: source.id, query: query);
        return page.results
            .map(
              (preview) => UnifiedSearchResult(
                media: preview.media,
                metadata: preview.metadata,
                sourceName: source.name,
              ),
            )
            .toList(growable: false);
      });
    }
  }

  Future<_SearchBatch> _searchLocal(String query) {
    return _guardSource(() async {
      final catalog = await _localCatalog();
      final normalized = query.toLowerCase();
      return catalog
          .where((media) => media.title.toLowerCase().contains(normalized))
          .map(
            (media) =>
                UnifiedSearchResult(media: media, sourceName: 'Local media'),
          )
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
    Future<List<UnifiedSearchResult>> Function() operation,
  ) async {
    try {
      return _SearchBatch(await operation());
    } catch (_) {
      return const _SearchBatch([], failed: true);
    }
  }

  List<UnifiedSearchResult> _deduplicate(
    Iterable<UnifiedSearchResult> results,
  ) {
    final seen = <SourceMediaRef>{};
    return List.unmodifiable(
      results.where((result) => seen.add(result.media.source)),
    );
  }

  void _publish(UnifiedSearchUiState state) {
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

final class _SearchBatch {
  const _SearchBatch(this.results, {this.failed = false});

  final List<UnifiedSearchResult> results;
  final bool failed;
}
