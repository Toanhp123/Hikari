import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:hikari/application/progress/load_continue_reading.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/continue_reading_item.dart';

enum LibraryViewMode { grid, list }

enum LibraryStatus { loading, ready, error }

@immutable
final class LibraryUiState {
  const LibraryUiState({
    this.status = LibraryStatus.loading,
    this.entries = const [],
    this.continueItems = const [],
    this.progressError,
    this.mediaType,
    this.viewMode = LibraryViewMode.grid,
    this.error,
  });

  final LibraryStatus status;
  final List<LibraryEntry> entries;
  final List<ContinueReadingItem> continueItems;
  final Object? progressError;
  final MediaType? mediaType;
  final LibraryViewMode viewMode;
  final Object? error;

  List<LibraryEntry> get visibleEntries {
    final type = mediaType;
    if (type == null) return entries;
    return entries
        .where((entry) => entry.media.type == type)
        .toList(growable: false);
  }

  LibraryUiState copyWith({
    LibraryStatus? status,
    List<LibraryEntry>? entries,
    List<ContinueReadingItem>? continueItems,
    Object? progressError,
    bool clearProgressError = false,
    MediaType? mediaType,
    bool clearMediaType = false,
    LibraryViewMode? viewMode,
    Object? error,
    bool clearError = false,
  }) {
    return LibraryUiState(
      status: status ?? this.status,
      entries: entries ?? this.entries,
      continueItems: continueItems ?? this.continueItems,
      progressError: clearProgressError
          ? null
          : progressError ?? this.progressError,
      mediaType: clearMediaType ? null : mediaType ?? this.mediaType,
      viewMode: viewMode ?? this.viewMode,
      error: clearError ? null : error ?? this.error,
    );
  }
}

final class LibraryViewModel extends ChangeNotifier {
  LibraryViewModel(this._repository, {this.loadContinueReading}) {
    final repository = _repository;
    if (repository is ObservableLibraryRepository) {
      _subscription = repository.watchAll().listen(
        _acceptStreamEntries,
        onError: _acceptStreamError,
      );
      unawaited(_loadInitialSnapshot());
    } else {
      unawaited(reload());
    }
  }

  final LibraryRepository _repository;
  final LoadContinueReading? loadContinueReading;
  StreamSubscription<List<LibraryEntry>>? _subscription;

  LibraryUiState _state = const LibraryUiState();
  LibraryUiState get state => _state;
  bool _disposed = false;
  int _streamRevision = 0;
  int _progressRevision = 0;

  Future<void> _loadInitialSnapshot() async {
    final revision = _streamRevision;
    try {
      final entries = await _repository.loadAll();
      if (_disposed || _streamRevision != revision) return;
      _acceptEntries(entries);
    } catch (error) {
      if (_disposed || _streamRevision != revision) return;
      _acceptError(error);
    }
  }

  Future<void> reload() async {
    if (_disposed) return;
    final revision = ++_streamRevision;
    _progressRevision++;
    _publish(_state.copyWith(status: LibraryStatus.loading, clearError: true));
    try {
      final entries = await _repository.loadAll();
      if (_disposed || revision != _streamRevision) return;
      _acceptEntries(entries);
    } catch (error) {
      if (_disposed || revision != _streamRevision) return;
      _acceptError(error);
    }
  }

  void selectMediaType(MediaType? type) {
    if (_state.mediaType == type) return;
    _publish(_state.copyWith(mediaType: type, clearMediaType: type == null));
  }

  void repositoryChanged() {
    if (_repository is! ObservableLibraryRepository) {
      unawaited(reload());
    }
  }

  Future<void> remove(SourceMediaRef media) async {
    await _repository.remove(media);
    repositoryChanged();
  }

  void toggleViewMode() {
    _publish(
      _state.copyWith(
        viewMode: _state.viewMode == LibraryViewMode.grid
            ? LibraryViewMode.list
            : LibraryViewMode.grid,
      ),
    );
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
    final saved = List<LibraryEntry>.unmodifiable(entries);
    _publish(
      _state.copyWith(
        status: LibraryStatus.ready,
        entries: saved,
        continueItems: const [],
        clearProgressError: true,
        clearError: true,
      ),
    );
    unawaited(refreshProgress());
  }

  /// Refresh only the optional progress summary, without refetching Library.
  /// A recent reader return may change progress without changing membership.
  Future<void> refreshProgress() async {
    final revision = ++_progressRevision;
    final loader = loadContinueReading;
    if (_disposed || loader == null || _state.status != LibraryStatus.ready) {
      return;
    }
    final entries = _state.entries;
    try {
      final result = await loader.execute(entries);
      if (_disposed || revision != _progressRevision) return;
      _publish(
        _state.copyWith(
          continueItems: result.items,
          progressError: result.error,
          clearProgressError: result.error == null,
        ),
      );
    } catch (error) {
      if (_disposed || revision != _progressRevision) return;
      _publish(_state.copyWith(progressError: error, continueItems: const []));
    }
  }

  void _acceptError(Object error) {
    _progressRevision++;
    _publish(_state.copyWith(status: LibraryStatus.error, error: error));
  }

  void _publish(LibraryUiState state) {
    if (_disposed) return;
    _state = state;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _progressRevision++;
    _subscription?.cancel();
    super.dispose();
  }
}
