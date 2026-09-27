import 'package:flutter/foundation.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/domain/media/media.dart';

sealed class RemoteMangaSearchUiState {
  const RemoteMangaSearchUiState();
}

final class RemoteMangaSearchIdle extends RemoteMangaSearchUiState {
  const RemoteMangaSearchIdle();
}

final class RemoteMangaSearchLoading extends RemoteMangaSearchUiState {
  const RemoteMangaSearchLoading();
}

final class RemoteMangaSearchReady extends RemoteMangaSearchUiState {
  const RemoteMangaSearchReady(this.results);

  final List<Media> results;
}

final class RemoteMangaSearchFailure extends RemoteMangaSearchUiState {
  const RemoteMangaSearchFailure();
}

/// Presentation state holder for the explicit remote-manga search workflow.
final class RemoteMangaSearchViewModel extends ChangeNotifier {
  RemoteMangaSearchViewModel({required SearchManga searchManga})
    : _searchManga = searchManga,
      sources = searchManga.options {
    _selectedSource = sources.isEmpty ? null : sources.first;
  }

  final SearchManga _searchManga;
  final List<MangaSearchOption> sources;

  MangaSearchOption? _selectedSource;
  MangaSearchOption? get selectedSource => _selectedSource;

  RemoteMangaSearchUiState _state = const RemoteMangaSearchIdle();
  RemoteMangaSearchUiState get state => _state;

  bool _disposed = false;

  void selectSource(SourceId? sourceId) {
    if (sourceId == null ||
        state is RemoteMangaSearchLoading ||
        sourceId == _selectedSource?.id) {
      return;
    }
    _selectedSource = sources.firstWhere((source) => source.id == sourceId);
    _publish(const RemoteMangaSearchIdle());
  }

  Future<void> search(String rawQuery) async {
    final source = _selectedSource;
    if (source == null || state is RemoteMangaSearchLoading) return;

    final query = rawQuery.trim();
    if (query.isEmpty) return;

    _publish(const RemoteMangaSearchLoading());
    try {
      final results = await _searchManga.execute(
        sourceId: source.id,
        query: query,
      );
      _publish(RemoteMangaSearchReady(results));
    } catch (_) {
      _publish(const RemoteMangaSearchFailure());
    }
  }

  void _publish(RemoteMangaSearchUiState state) {
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
