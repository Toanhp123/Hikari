import 'package:flutter/foundation.dart';
import 'package:hikari/domain/media/novel.dart';

sealed class NovelSeriesUiState {
  const NovelSeriesUiState();
}

final class NovelSeriesLoading extends NovelSeriesUiState {
  const NovelSeriesLoading();
}

final class NovelSeriesReady extends NovelSeriesUiState {
  const NovelSeriesReady(this.details);

  final NovelDetails details;
}

final class NovelSeriesFailure extends NovelSeriesUiState {
  const NovelSeriesFailure();
}

final class NovelSeriesViewModel extends ChangeNotifier {
  NovelSeriesViewModel(this._loadDetails);

  final Future<NovelDetails> Function() _loadDetails;

  NovelSeriesUiState _state = const NovelSeriesLoading();
  NovelSeriesUiState get state => _state;

  int _generation = 0;
  bool _disposed = false;

  Future<void> load() async {
    final generation = ++_generation;
    _publish(const NovelSeriesLoading());
    try {
      final details = await Future.sync(_loadDetails);
      if (_disposed || generation != _generation) return;
      _publish(NovelSeriesReady(details));
    } catch (_) {
      if (_disposed || generation != _generation) return;
      _publish(const NovelSeriesFailure());
    }
  }

  void _publish(NovelSeriesUiState state) {
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
