import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';

@immutable
final class HomeUiState {
  const HomeUiState({
    this.loading = false,
    this.libraryItems = const [],
    this.error,
  });

  final bool loading;
  final List<Media> libraryItems;
  final Object? error;
}

/// Supplies Home with real library-backed media without teaching the view about
/// persistence or Drift.
final class HomeViewModel extends ChangeNotifier {
  HomeViewModel(LibraryRepository? repository) : _repository = repository {
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
      _acceptEntries(entries);
    } catch (error) {
      if (_disposed || _streamRevision != revision) return;
      _acceptError(error);
    }
  }

  Future<void> reload() async {
    final repository = _repository;
    if (repository == null || _disposed) return;
    _publish(HomeUiState(loading: true, libraryItems: _state.libraryItems));
    try {
      _acceptEntries(await repository.loadAll());
    } catch (error) {
      _acceptError(error);
    }
  }

  void _acceptStreamEntries(List<LibraryEntry> entries) {
    _streamRevision++;
    _acceptEntries(entries);
  }

  void _acceptStreamError(Object error) {
    _streamRevision++;
    _acceptError(error);
  }

  void _acceptEntries(List<LibraryEntry> entries) {
    _publish(
      HomeUiState(
        libraryItems: List.unmodifiable(entries.map((entry) => entry.media)),
      ),
    );
  }

  void _acceptError(Object error) {
    _publish(HomeUiState(libraryItems: _state.libraryItems, error: error));
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
