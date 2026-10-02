import 'package:flutter/foundation.dart';
import 'package:hikari/domain/media/media.dart';

enum LocalMediaStatus { initial, loading, ready, error }

@immutable
final class LocalMediaUiState {
  const LocalMediaUiState({
    this.status = LocalMediaStatus.initial,
    this.media = const [],
    this.hasScanResult = false,
  });

  final LocalMediaStatus status;
  final List<Media> media;
  final bool hasScanResult;

  bool get refreshing => status == LocalMediaStatus.loading && hasScanResult;
  bool get refreshFailed => status == LocalMediaStatus.error && hasScanResult;
  bool get busy => status == LocalMediaStatus.loading;
}

/// Presentation state holder for the local-media discovery screen.
final class LocalMediaViewModel extends ChangeNotifier {
  LocalMediaViewModel(this._scanSelectedRoot, this._chooseRoot);

  final Future<List<Media>?> Function() _scanSelectedRoot;
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
    } catch (_) {
      if (_disposed || generation != _generation) return;
      _publish(const LocalMediaUiState(status: LocalMediaStatus.error));
    }
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
                media: List.unmodifiable(result),
                hasScanResult: true,
              ),
      );
    } catch (_) {
      if (_disposed || generation != _generation) return;
      _publish(
        staleState.hasScanResult
            ? LocalMediaUiState(
                status: LocalMediaStatus.error,
                media: staleState.media,
                hasScanResult: true,
              )
            : const LocalMediaUiState(status: LocalMediaStatus.error),
      );
    }
  }

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
