import 'package:flutter/foundation.dart';
import 'package:hikari/domain/media/manga.dart';

sealed class MangaSeriesUiState {
  const MangaSeriesUiState();
}

final class MangaSeriesLoading extends MangaSeriesUiState {
  const MangaSeriesLoading();
}

final class MangaSeriesReady extends MangaSeriesUiState {
  const MangaSeriesReady(this.details);

  final MangaSeriesDetails details;
}

final class MangaSeriesFailure extends MangaSeriesUiState {
  const MangaSeriesFailure();
}

final class MangaSeriesViewModel extends ChangeNotifier {
  MangaSeriesViewModel(this._loadDetails);

  final Future<MangaSeriesDetails> Function() _loadDetails;

  MangaSeriesUiState _state = const MangaSeriesLoading();
  MangaSeriesUiState get state => _state;

  int _generation = 0;
  bool _disposed = false;

  Future<void> load() async {
    final generation = ++_generation;
    _publish(const MangaSeriesLoading());
    try {
      final details = await Future.sync(_loadDetails);
      if (_disposed || generation != _generation) return;
      _publish(MangaSeriesReady(details));
    } catch (_) {
      if (_disposed || generation != _generation) return;
      _publish(const MangaSeriesFailure());
    }
  }

  void _publish(MangaSeriesUiState state) {
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
