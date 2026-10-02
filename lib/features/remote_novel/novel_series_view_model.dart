import 'package:flutter/foundation.dart';
import 'package:hikari/domain/media/novel.dart';

enum NovelSeriesStatus { loading, ready, error }

@immutable
final class NovelSeriesUiState {
  const NovelSeriesUiState({
    this.status = NovelSeriesStatus.loading,
    this.details,
  });

  final NovelSeriesStatus status;
  final NovelDetails? details;
  bool get initialLoading =>
      status == NovelSeriesStatus.loading && details == null;
  bool get refreshing => status == NovelSeriesStatus.loading && details != null;
  bool get failed => status == NovelSeriesStatus.error && details == null;
  bool get refreshFailed =>
      status == NovelSeriesStatus.error && details != null;
}

final class NovelSeriesViewModel extends ChangeNotifier {
  NovelSeriesViewModel(this._loadDetails);

  final Future<NovelDetails> Function() _loadDetails;

  NovelSeriesUiState _state = const NovelSeriesUiState();
  NovelSeriesUiState get state => _state;

  int _generation = 0;
  bool _disposed = false;

  Future<void> load() async {
    final generation = ++_generation;
    _publish(
      NovelSeriesUiState(
        status: NovelSeriesStatus.loading,
        details: _state.details,
      ),
    );
    try {
      final details = await Future.sync(_loadDetails);
      if (_disposed || generation != _generation) return;
      _publish(
        NovelSeriesUiState(status: NovelSeriesStatus.ready, details: details),
      );
    } catch (_) {
      if (_disposed || generation != _generation) return;
      _publish(
        NovelSeriesUiState(
          status: NovelSeriesStatus.error,
          details: _state.details,
        ),
      );
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
