import 'package:flutter/foundation.dart';
import 'package:hikari/domain/media/media.dart';

sealed class LocalMediaUiState {
  const LocalMediaUiState();
}

final class LocalMediaInitial extends LocalMediaUiState {
  const LocalMediaInitial();
}

final class LocalMediaLoading extends LocalMediaUiState {
  const LocalMediaLoading();
}

final class LocalMediaReady extends LocalMediaUiState {
  const LocalMediaReady(this.media);

  final List<Media> media;
}

final class LocalMediaFailure extends LocalMediaUiState {
  const LocalMediaFailure(this.error);

  final Object error;
}

/// Presentation state holder for the local-media discovery screen.
final class LocalMediaViewModel extends ChangeNotifier {
  LocalMediaViewModel(this._scanSelectedRoot, this._chooseRoot);

  final Future<List<Media>?> Function() _scanSelectedRoot;
  final Future<bool> Function() _chooseRoot;

  LocalMediaUiState _state = const LocalMediaInitial();
  LocalMediaUiState get state => _state;

  bool _disposed = false;
  int _generation = 0;

  Future<void> scan({bool refresh = false}) async {
    if (!refresh && _state is LocalMediaLoading) return;
    final generation = ++_generation;
    _publish(const LocalMediaLoading());
    await _loadSelectedRoot(generation);
  }

  Future<void> chooseRoot() async {
    if (_state is LocalMediaLoading) return;

    final generation = ++_generation;
    final previousState = _state;
    _publish(const LocalMediaLoading());
    try {
      final selected = await _chooseRoot();
      if (_disposed || generation != _generation) return;
      if (!selected) {
        _publish(previousState);
        return;
      }
      await _loadSelectedRoot(generation);
    } catch (error) {
      if (generation != _generation) return;
      _publish(LocalMediaFailure(error));
    }
  }

  Future<void> _loadSelectedRoot(int generation) async {
    try {
      final result = await _scanSelectedRoot();
      if (_disposed || generation != _generation) return;
      _publish(
        result == null
            ? const LocalMediaInitial()
            : LocalMediaReady(List.unmodifiable(result)),
      );
    } catch (error) {
      if (generation != _generation) return;
      _publish(LocalMediaFailure(error));
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
    super.dispose();
  }
}
