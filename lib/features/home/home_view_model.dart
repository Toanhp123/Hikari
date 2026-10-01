import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/home/widgets/continue_shelf.dart';

@immutable
final class HomeUiState {
  const HomeUiState({
    this.loading = false,
    this.libraryItems = const [],
    this.continueItems = const [],
    this.error,
    this.progressError,
  });

  final bool loading;
  final List<Media> libraryItems;
  final List<ContinueReadingItem> continueItems;
  final Object? error;
  final Object? progressError;
}

/// Supplies Home with real library-backed media without teaching the view about
/// persistence or Drift.
final class HomeViewModel extends ChangeNotifier {
  HomeViewModel(LibraryRepository? repository, {this._progressRepository})
    : _repository = repository {
    if (repository == null) return;
    if (repository is ObservableLibraryRepository) {
      _state = const HomeUiState(loading: true);
      _subscription = repository.watchAll().listen(
        _acceptStreamEntries,
        onError: _acceptStreamError,
      );
      unawaited(_loadInitialSnapshot());
    } else {
      unawaited(reload());
    }
  }

  final LibraryRepository? _repository;
  final ProgressRepository? _progressRepository;
  StreamSubscription<List<LibraryEntry>>? _subscription;

  HomeUiState _state = const HomeUiState();
  HomeUiState get state => _state;
  bool _disposed = false;
  int _streamRevision = 0;

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
    _publish(
      HomeUiState(
        loading: true,
        libraryItems: _state.libraryItems,
        continueItems: _state.continueItems,
        error: _state.error,
        progressError: _state.progressError,
      ),
    );
    try {
      final entries = await repository.loadAll();
      if (_disposed || _streamRevision != revision) return;
      await _acceptEntries(entries, revision: revision);
    } catch (error) {
      if (_disposed || _streamRevision != revision) return;
      _acceptError(error);
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
          final pair = switch (progress.position) {
            VideoPosition(:final position, :final duration)
                when duration > Duration.zero =>
              (
                progress: position.inMilliseconds / duration.inMilliseconds,
                label: 'Resume video',
              ),
            PagePosition(:final pageIndex, :final pageCount)
                when pageCount > 0 =>
              (
                progress: (pageIndex + 1) / pageCount,
                label: 'Page ${pageIndex + 1} of $pageCount',
              ),
            TextPosition(:final progression) => (
              progress: progression,
              label: '${(progression * 100).round()}% read',
            ),
            DocumentPosition(:final progression, :final totalProgression) => (
              progress: totalProgression ?? progression ?? 0.0,
              label: 'Resume reading',
            ),
            _ => null,
          };
          if (pair != null) {
            continueItems.add((
              item: ContinueReadingItem(
                media: item,
                progress: pair.progress,
                progressLabel: pair.label,
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
      HomeUiState(
        libraryItems: media,
        continueItems: List.unmodifiable(
          continueItems.map((entry) => entry.item),
        ),
        progressError: progressError,
      ),
    );
  }

  void _acceptError(Object error) {
    _publish(
      HomeUiState(
        libraryItems: _state.libraryItems,
        continueItems: _state.continueItems,
        error: error,
        progressError: _state.progressError,
      ),
    );
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
