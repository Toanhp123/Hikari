import 'package:flutter/foundation.dart';
import 'package:hikari/domain/media/local_media_scan_result.dart';
import 'package:hikari/domain/media/media.dart';

enum LocalMediaStatus { initial, loading, ready, error }

enum LocalMediaFailure { unavailable, accessLost, pickerFailed }

@immutable
final class LocalMediaUiState {
  const LocalMediaUiState({
    this.status = LocalMediaStatus.initial,
    this.media = const [],
    this.hasScanResult = false,
    this.rootName,
    this.artwork = const {},
    this.failure,
    this.filter,
  });

  final LocalMediaStatus status;
  final List<Media> media;
  final bool hasScanResult;
  final String? rootName;
  final Map<SourceMediaRef, SourceMediaRef> artwork;
  final LocalMediaFailure? failure;
  final MediaType? filter;

  List<Media> get visibleMedia => filter == null
      ? media
      : media.where((item) => item.type == filter).toList();

  bool get refreshing => status == LocalMediaStatus.loading && hasScanResult;
  bool get refreshFailed => status == LocalMediaStatus.error && hasScanResult;
  bool get busy => status == LocalMediaStatus.loading;
}

/// Presentation state holder for the local-media discovery screen.
final class LocalMediaViewModel extends ChangeNotifier {
  LocalMediaViewModel(this._scanSelectedRoot, this._chooseRoot);

  final Future<LocalMediaScanResult?> Function() _scanSelectedRoot;
  final Future<bool> Function() _chooseRoot;

  LocalMediaUiState _state = const LocalMediaUiState();
  LocalMediaUiState get state => _state;

  bool _disposed = false;
  int _generation = 0;

  Future<void> scan({bool rootChanged = false}) async {
    if (!rootChanged && _state.busy) return;
    final generation = ++_generation;
    final staleState = rootChanged ? const LocalMediaUiState() : _state;
    _publish(
      LocalMediaUiState(
        status: LocalMediaStatus.loading,
        media: staleState.media,
        hasScanResult: staleState.hasScanResult,
        rootName: staleState.rootName,
        artwork: staleState.artwork,
        filter: staleState.filter,
      ),
    );
    await _loadSelectedRoot(generation, staleState: staleState);
  }

  Future<void> chooseRoot() async {
    if (_state.busy) return;

    final generation = ++_generation;
    final previousState = _state;
    _publish(
      LocalMediaUiState(
        status: LocalMediaStatus.loading,
        media: _state.media,
        hasScanResult: _state.hasScanResult,
        rootName: _state.rootName,
        artwork: _state.artwork,
        filter: _state.filter,
      ),
    );
    try {
      final selected = await _chooseRoot();
      if (_disposed || generation != _generation) return;
      if (!selected) {
        _publish(previousState);
        return;
      }

      // A newly selected root owns a different result set; never restore data
      // from the previous root if its first scan fails.
      _publish(const LocalMediaUiState(status: LocalMediaStatus.loading));
      await _loadSelectedRoot(
        generation,
        staleState: const LocalMediaUiState(),
      );
    } catch (error) {
      if (_disposed || generation != _generation) return;
      // The picker failed before committing a replacement root. Keep the
      // previous root snapshot, exactly as on user cancellation.
      _publish(
        LocalMediaUiState(
          status: LocalMediaStatus.error,
          media: previousState.media,
          hasScanResult: previousState.hasScanResult,
          rootName: previousState.rootName,
          artwork: previousState.artwork,
          filter: _state.filter,
          failure: LocalMediaFailure.pickerFailed,
        ),
      );
    }
  }

  void selectFilter(MediaType? type) {
    if (_state.filter == type) return;
    _publish(
      LocalMediaUiState(
        status: _state.status,
        media: _state.media,
        hasScanResult: _state.hasScanResult,
        rootName: _state.rootName,
        artwork: _state.artwork,
        failure: _state.failure,
        filter: type,
      ),
    );
  }

  Future<void> _loadSelectedRoot(
    int generation, {
    required LocalMediaUiState staleState,
  }) async {
    try {
      final result = await _scanSelectedRoot();
      if (_disposed || generation != _generation) return;
      _publish(
        result == null
            ? const LocalMediaUiState()
            : LocalMediaUiState(
                status: LocalMediaStatus.ready,
                media: result.media,
                hasScanResult: true,
                rootName: result.rootName,
                artwork: result.artwork,
                filter: _state.filter,
              ),
      );
    } catch (error) {
      if (_disposed || generation != _generation) return;
      _publish(
        staleState.hasScanResult
            ? LocalMediaUiState(
                status: LocalMediaStatus.error,
                media: staleState.media,
                hasScanResult: true,
                rootName: staleState.rootName,
                artwork: staleState.artwork,
                filter: _state.filter,
                failure: _failureForError(error),
              )
            : LocalMediaUiState(
                status: LocalMediaStatus.error,
                failure: _failureForError(error),
              ),
      );
    }
  }

  static LocalMediaFailure _failureForError(Object error) =>
      error is LocalMediaAccessException
      ? LocalMediaFailure.accessLost
      : LocalMediaFailure.unavailable;

  void _publish(LocalMediaUiState state) {
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
