import 'package:flutter/foundation.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/reading.dart';

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
  const RemoteMangaSearchReady(
    this.results, {
    required this.hasNextPage,
    required this.page,
    this.loadingMore = false,
    this.pageFailed = false,
  });
  final List<MangaPreview> results;
  final bool hasNextPage;
  final int page;
  final bool loadingMore;
  final bool pageFailed;
}

final class RemoteMangaSearchFailure extends RemoteMangaSearchUiState {
  const RemoteMangaSearchFailure();
}

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
  int _generation = 0;
  String _query = '';

  void selectSource(SourceId? sourceId) {
    if (sourceId == null || sourceId == _selectedSource?.id) return;
    _selectedSource = sources.firstWhere((source) => source.id == sourceId);
    _generation++;
    _query = '';
    _publish(const RemoteMangaSearchIdle());
  }

  Future<void> search(String rawQuery) async {
    final source = _selectedSource;
    if (source == null) return;
    if (rawQuery.trim().isEmpty) {
      _generation++;
      _query = '';
      _publish(const RemoteMangaSearchIdle());
      return;
    }
    _query = rawQuery.trim();
    final generation = ++_generation;
    _publish(const RemoteMangaSearchLoading());
    try {
      final result = await _searchManga.execute(
        sourceId: source.id,
        query: _query,
      );
      if (generation != _generation) return;
      _publish(
        RemoteMangaSearchReady(
          result.results,
          hasNextPage: result.hasNextPage && result.results.isNotEmpty,
          page: result.page,
        ),
      );
    } catch (_) {
      if (generation == _generation) _publish(const RemoteMangaSearchFailure());
    }
  }

  Future<void> retry() => search(_query);

  Future<void> loadMore() async {
    final previous = _state;
    if (previous is! RemoteMangaSearchReady ||
        !previous.hasNextPage ||
        previous.loadingMore) {
      return;
    }
    final generation = _generation;
    _publish(
      RemoteMangaSearchReady(
        previous.results,
        hasNextPage: previous.hasNextPage,
        page: previous.page,
        loadingMore: true,
      ),
    );
    try {
      final result = await _searchManga.execute(
        sourceId: _selectedSource!.id,
        query: _query,
        page: previous.page + 1,
      );
      if (generation != _generation) return;
      final seen = previous.results.map((item) => item.media.source).toSet();
      _publish(
        RemoteMangaSearchReady(
          List.unmodifiable([
            ...previous.results,
            ...result.results.where((item) => seen.add(item.media.source)),
          ]),
          hasNextPage: result.hasNextPage && result.results.isNotEmpty,
          page: result.page,
        ),
      );
    } catch (_) {
      if (generation != _generation) return;
      _publish(
        RemoteMangaSearchReady(
          previous.results,
          hasNextPage: previous.hasNextPage,
          page: previous.page,
          pageFailed: true,
        ),
      );
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
    _generation++;
    super.dispose();
  }
}
