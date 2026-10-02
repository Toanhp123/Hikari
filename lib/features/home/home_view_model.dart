import 'dart:async';

import 'package:flutter/foundation.dart';

import 'package:hikari/application/catalog/discover_catalog.dart';
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
final class ContinueReadingItem {
  const ContinueReadingItem({
    required this.media,
    required this.progress,
    this.position,
  });

  final Media media;
  final double progress;
  final ProgressPosition? position;
}

@immutable
final class HomeUiState {
  const HomeUiState({
    this.loading = false,
    this.libraryItems = const [],
    this.continueItems = const [],
    this.error,
    this.progressError,
    this.catalogDiscovery,
    this.catalogError,
    this.selectedFilter = HomeFilterType.all,
  });

  final bool loading;
  final List<Media> libraryItems;
  final List<ContinueReadingItem> continueItems;
  final Object? error;
  final Object? progressError;
  final CatalogDiscovery? catalogDiscovery;
  final Object? catalogError;
  final HomeFilterType selectedFilter;

  HomeUiState copyWith({
    bool? loading,
    List<Media>? libraryItems,
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
    this._progressRepository,
    DiscoverCatalog? discoverCatalog,
  }) : _repository = repository,
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
  final ProgressRepository? _progressRepository;
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

  Future<void> reloadCatalog() async {
    final discoverCatalog = _discoverCatalog;
    if (discoverCatalog == null || _disposed) return;
    final revision = ++_catalogRevision;
    _publish(_state.copyWith(clearCatalogError: true));
    try {
      final discovery = await discoverCatalog.execute();
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
    final continueItems = <({ContinueReadingItem item, DateTime updatedAt})>[];
    Object? progressError;
    final progressRepository = _progressRepository;
    if (progressRepository != null) {
      for (final item in media) {
        try {
          final progress = await progressRepository.load(item.source);
          if (progress == null || progress.completed) continue;
          final fraction = switch (progress.position) {
            VideoPosition(:final position, :final duration)
                when duration > Duration.zero =>
              position.inMilliseconds / duration.inMilliseconds,
            PagePosition(:final pageIndex, :final pageCount)
                when pageCount > 0 =>
              (pageIndex + 1) / pageCount,
            TextPosition(:final progression) => progression,
            DocumentPosition(:final progression, :final totalProgression) =>
              totalProgression ?? progression ?? 0.0,
            _ => null,
          };
          if (fraction != null) {
            continueItems.add((
              item: ContinueReadingItem(
                media: item,
                progress: fraction,
                position: progress.position,
              ),
              updatedAt: progress.updatedAt,
            ));
          }
        } catch (error) {
          progressError ??= error;
        }
      }
    }
    if (_disposed || _streamRevision != revision) return;
    continueItems.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    _publish(
      _state.copyWith(
        loading: false,
        libraryItems: media,
        continueItems: List.unmodifiable(
          continueItems.map((entry) => entry.item),
        ),
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
