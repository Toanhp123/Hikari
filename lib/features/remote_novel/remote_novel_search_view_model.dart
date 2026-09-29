import 'package:flutter/foundation.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';

sealed class RemoteNovelSearchUiState {
  const RemoteNovelSearchUiState();
}

final class RemoteNovelSearchIdle extends RemoteNovelSearchUiState {
  const RemoteNovelSearchIdle();
}

final class RemoteNovelSearchLoading extends RemoteNovelSearchUiState {
  const RemoteNovelSearchLoading();
}

final class RemoteNovelSearchReady extends RemoteNovelSearchUiState {
  const RemoteNovelSearchReady(
    this.results, {
    required this.hasNextPage,
    required this.page,
    this.loadingMore = false,
    this.pageFailed = false,
  });

  final List<NovelPreview> results;
  final bool? hasNextPage;
  final int page;
  final bool loadingMore;
  final bool pageFailed;
}

final class RemoteNovelSearchFailure extends RemoteNovelSearchUiState {
  const RemoteNovelSearchFailure();
}

final class RemoteNovelSearchViewModel extends ChangeNotifier {
  RemoteNovelSearchViewModel(this._searchNovels)
    : sources = _searchNovels.options {
    _selectedSource = sources.isEmpty ? null : sources.first;
  }

  final SearchNovels _searchNovels;
  final List<NovelSearchOption> sources;
  NovelSearchOption? _selectedSource;
  NovelSearchOption? get selectedSource => _selectedSource;

  RemoteNovelSearchUiState _state = const RemoteNovelSearchIdle();
  RemoteNovelSearchUiState get state => _state;

  int _generation = 0;
  bool _disposed = false;
  String _query = '';

  void selectSource(SourceId? id) {
    if (id == null || id == _selectedSource?.id) return;
    _selectedSource = sources.firstWhere((source) => source.id == id);
    _generation++;
    _query = '';
    _publish(const RemoteNovelSearchIdle());
  }

  Future<void> search(String query) async {
    final source = _selectedSource;
    if (source == null) return;
    final normalized = query.trim();
    if (normalized.isEmpty) {
      _generation++;
      _query = '';
      _publish(const RemoteNovelSearchIdle());
      return;
    }

    _query = normalized;
    final generation = ++_generation;
    _publish(const RemoteNovelSearchLoading());
    try {
      final result = await _searchNovels.execute(
        sourceId: source.id,
        query: _query,
      );
      if (generation != _generation) return;
      _publish(
        RemoteNovelSearchReady(
          result.results,
          hasNextPage: result.results.isEmpty ? false : result.hasNextPage,
          page: result.page,
        ),
      );
    } catch (_) {
      if (generation == _generation) {
        _publish(const RemoteNovelSearchFailure());
      }
    }
  }

  Future<void> retry() => search(_query);

  Future<void> loadMore() async {
    final previous = _state;
    if (previous is! RemoteNovelSearchReady ||
        previous.hasNextPage == false ||
        previous.loadingMore) {
      return;
    }

    final generation = _generation;
    _publish(
      RemoteNovelSearchReady(
        previous.results,
        hasNextPage: previous.hasNextPage,
        page: previous.page,
        loadingMore: true,
      ),
    );
    try {
      final result = await _searchNovels.execute(
        sourceId: _selectedSource!.id,
        query: _query,
        page: previous.page + 1,
      );
      if (generation != _generation) return;
      final seen = previous.results.map((item) => item.media.source).toSet();
      _publish(
        RemoteNovelSearchReady(
          List.unmodifiable([
            ...previous.results,
            ...result.results.where((item) => seen.add(item.media.source)),
          ]),
          hasNextPage: result.results.isEmpty ? false : result.hasNextPage,
          page: result.page,
        ),
      );
    } catch (_) {
      if (generation != _generation) return;
      _publish(
        RemoteNovelSearchReady(
          previous.results,
          hasNextPage: previous.hasNextPage,
          page: previous.page,
          pageFailed: true,
        ),
      );
    }
  }

  void _publish(RemoteNovelSearchUiState state) {
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
