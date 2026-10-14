import 'dart:async';

import 'package:flutter/foundation.dart';

import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/application/progress/load_continue_reading.dart';
import 'package:hikari/domain/progress/continue_reading_item.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';

enum HomeFilterType {
  all(null),
  anime(MediaType.anime),
  manga(MediaType.manga),
  novel(MediaType.lightNovel);

  const HomeFilterType(this.mediaType);

  final MediaType? mediaType;
}

@immutable
final class HomeUiState {
  const HomeUiState({
    this.loading = false,
    this.libraryItems = const [],
    this.artworkByMedia = const {},
    this.hasLibrarySnapshot = false,
    this.continueItems = const [],
    this.error,
    this.progressError,
    this.catalogDiscovery,
    this.catalogError,
    this.selectedFilter = HomeFilterType.all,
  });

  final bool loading;
  final List<Media> libraryItems;
  final Map<SourceMediaRef, SourceMediaRef> artworkByMedia;
  final bool hasLibrarySnapshot;
  final List<ContinueReadingItem> continueItems;
  final Object? error;
  final Object? progressError;
  final CatalogDiscovery? catalogDiscovery;
  final Object? catalogError;
  final HomeFilterType selectedFilter;

  HomeUiState copyWith({
    bool? loading,
    List<Media>? libraryItems,
    Map<SourceMediaRef, SourceMediaRef>? artworkByMedia,
    bool? hasLibrarySnapshot,
    List<ContinueReadingItem>? continueItems,
    Object? error,
    bool clearError = false,
    Object? progressError,
    bool clearProgressError = false,
    CatalogDiscovery? catalogDiscovery,
    Object? catalogError,
    bool clearCatalogError = false,
    HomeFilterType? selectedFilter,
  }) {
    return HomeUiState(
      loading: loading ?? this.loading,
      libraryItems: libraryItems ?? this.libraryItems,
      artworkByMedia: artworkByMedia ?? this.artworkByMedia,
      hasLibrarySnapshot: hasLibrarySnapshot ?? this.hasLibrarySnapshot,
      continueItems: continueItems ?? this.continueItems,
      error: clearError ? null : error ?? this.error,
      progressError: clearProgressError
          ? null
          : progressError ?? this.progressError,
      catalogDiscovery: catalogDiscovery ?? this.catalogDiscovery,
      catalogError: clearCatalogError
          ? null
          : catalogError ?? this.catalogError,
      selectedFilter: selectedFilter ?? this.selectedFilter,
    );
  }
}

/// Supplies Home with real library-backed media without teaching the view about
/// persistence or Drift.
final class HomeViewModel extends ChangeNotifier {
  HomeViewModel(
    LibraryRepository? repository, {
    LoadContinueReading? continueReading,
    ProgressRepository? progressRepository,
    DiscoverCatalog? discoverCatalog,
  }) : _repository = repository,
       _continueReading =
           continueReading ??
           (progressRepository == null
               ? null
               : LoadContinueReading(progressRepository)),
       _discoverCatalog = discoverCatalog {
    if (repository is ObservableLibraryRepository) {
      _state = const HomeUiState(loading: true);
      _subscription = repository.watchAll().listen(
        _acceptStreamEntries,
        onError: _acceptStreamError,
      );
      unawaited(_loadInitialSnapshot());
    } else if (repository != null) {
      unawaited(reload());
    }
    if (discoverCatalog != null) unawaited(reloadCatalog());
  }

  final LibraryRepository? _repository;
  final LoadContinueReading? _continueReading;
  final DiscoverCatalog? _discoverCatalog;
  StreamSubscription<List<LibraryEntry>>? _subscription;

  HomeUiState _state = const HomeUiState();
  HomeUiState get state => _state;
  bool _disposed = false;
  int _streamRevision = 0;
  int _catalogRevision = 0;

  List<HomeFilterType> get availableFilters {
    final mediaTypes = _state.libraryItems.map((item) => item.type).toSet();
    if (mediaTypes.length <= 1) return const [];
    return [
      HomeFilterType.all,
      if (mediaTypes.contains(MediaType.anime)) HomeFilterType.anime,
      if (mediaTypes.contains(MediaType.manga)) HomeFilterType.manga,
      if (mediaTypes.contains(MediaType.lightNovel)) HomeFilterType.novel,
    ];
  }

  HomeFilterType get effectiveFilter =>
      availableFilters.contains(_state.selectedFilter)
      ? _state.selectedFilter
      : HomeFilterType.all;

  List<Media> get visibleLibraryItems {
    final mediaType = effectiveFilter.mediaType;
    if (mediaType == null) return _state.libraryItems;
    return _state.libraryItems
        .where((item) => item.type == mediaType)
        .toList(growable: false);
  }

  void selectFilter(HomeFilterType filter) {
    if (_state.selectedFilter == filter) return;
    _publish(_state.copyWith(selectedFilter: filter));
  }

  Future<void> _loadInitialSnapshot() async {
    final repository = _repository;
    if (repository == null) return;
    final revision = _streamRevision;
    try {
      final entries = await repository.loadAll();
      if (_disposed || _streamRevision != revision) return;
      await _acceptEntries(entries, revision: revision);
    } catch (error) {
      if (_disposed || _streamRevision != revision) return;
      _acceptError(error);
    }
  }

  Future<void> reload() async {
    final repository = _repository;
    if (repository == null || _disposed) return;
    final revision = ++_streamRevision;
    _publish(_state.copyWith(loading: true));
    try {
      final entries = await repository.loadAll();
      if (_disposed || _streamRevision != revision) return;
      await _acceptEntries(entries, revision: revision);
    } catch (error) {
      if (_disposed || _streamRevision != revision) return;
      _acceptError(error);
    }
  }

  Future<void> refresh() async {
    final tasks = <Future<void>>[];
    if (_repository != null) tasks.add(reload());
    if (_discoverCatalog != null) {
      tasks.add(reloadCatalog(forceRefresh: true));
    }
    await Future.wait(tasks);
  }

  Future<void> reloadCatalog({bool forceRefresh = false}) async {
    final discoverCatalog = _discoverCatalog;
    if (discoverCatalog == null || _disposed) return;
    final revision = ++_catalogRevision;
    _publish(_state.copyWith(clearCatalogError: true));
    try {
      CatalogSnapshot<CatalogDiscovery>? cached;
      if (!forceRefresh) {
        cached = await discoverCatalog.cached();
        if (_disposed || revision != _catalogRevision) return;
        if (cached != null) {
          _publish(
            _state.copyWith(
              catalogDiscovery: cached.data,
              clearCatalogError: true,
            ),
          );
          if (cached.isFresh) return;
        }
      }
      // Stale snapshots are displayed first, but never suppress revalidation.
      final discovery = forceRefresh || cached != null
          ? await discoverCatalog.refresh()
          : await discoverCatalog.execute();
      if (_disposed || revision != _catalogRevision) return;
      _publish(
        _state.copyWith(catalogDiscovery: discovery, clearCatalogError: true),
      );
    } catch (error) {
      if (_disposed || revision != _catalogRevision) return;
      _publish(_state.copyWith(catalogError: error));
    }
  }

  void _acceptStreamEntries(List<LibraryEntry> entries) {
    final revision = ++_streamRevision;
    unawaited(_acceptEntries(entries, revision: revision));
  }

  void _acceptStreamError(Object error) {
    ++_streamRevision;
    _acceptError(error);
  }

  Future<void> _acceptEntries(
    List<LibraryEntry> entries, {
    required int revision,
  }) async {
    final media = List<Media>.unmodifiable(entries.map((entry) => entry.media));
    final artworkByMedia = Map<SourceMediaRef, SourceMediaRef>.unmodifiable({
      for (final entry in entries)
        if (entry.artwork != null) entry.media.source: entry.artwork!,
    });
    var continueItems = const <ContinueReadingItem>[];
    Object? progressError;
    final loader = _continueReading;
    if (loader != null) {
      final result = await loader.execute(entries);
      continueItems = result.items;
      progressError = result.error;
    }
    if (_disposed || _streamRevision != revision) return;
    _publish(
      _state.copyWith(
        loading: false,
        libraryItems: media,
        artworkByMedia: artworkByMedia,
        hasLibrarySnapshot: true,
        continueItems: continueItems,
        progressError: progressError,
        clearProgressError: progressError == null,
        clearError: true,
      ),
    );
  }

  void _acceptError(Object error) {
    _publish(_state.copyWith(loading: false, error: error));
  }

  void _publish(HomeUiState state) {
    if (_disposed) return;
    _state = state;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    unawaited(_subscription?.cancel());
    super.dispose();
  }
}
