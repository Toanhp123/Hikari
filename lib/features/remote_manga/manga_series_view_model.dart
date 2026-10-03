import 'package:flutter/foundation.dart';
import 'package:hikari/domain/media/manga.dart';

enum MangaSeriesStatus { loading, ready, error }

@immutable
final class MangaSeriesUiState {
  const MangaSeriesUiState({
    this.status = MangaSeriesStatus.loading,
    this.details,
  });

  final MangaSeriesStatus status;
  final MangaSeriesDetails? details;
  bool get initialLoading =>
      status == MangaSeriesStatus.loading && details == null;
  bool get refreshing => status == MangaSeriesStatus.loading && details != null;
  bool get failed => status == MangaSeriesStatus.error && details == null;
  bool get refreshFailed =>
      status == MangaSeriesStatus.error && details != null;
}

final class MangaSeriesViewModel extends ChangeNotifier {
  MangaSeriesViewModel(this._loadDetails);

  final Future<MangaSeriesDetails> Function() _loadDetails;

  MangaSeriesUiState _state = const MangaSeriesUiState();
  MangaSeriesUiState get state => _state;

  int _generation = 0;
  bool _disposed = false;

  Future<void> load() async {
    final generation = ++_generation;
    _publish(
      MangaSeriesUiState(
        status: MangaSeriesStatus.loading,
        details: _state.details,
      ),
    );
    try {
      final details = await Future.sync(_loadDetails);
      if (_disposed || generation != _generation) return;
      _publish(
        MangaSeriesUiState(status: MangaSeriesStatus.ready, details: details),
      );
    } catch (_) {
      if (_disposed || generation != _generation) return;
      _publish(
        MangaSeriesUiState(
          status: MangaSeriesStatus.error,
          details: _state.details,
        ),
      );
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
