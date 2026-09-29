import 'package:flutter/foundation.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';

final class RemoteNovelSearchViewModel extends ChangeNotifier {
  RemoteNovelSearchViewModel(this.searchNovels)
    : sources = searchNovels.options {
    selectedSource = sources.isEmpty ? null : sources.first;
  }
  final SearchNovels searchNovels;
  final List<NovelSearchOption> sources;
  NovelSearchOption? selectedSource;
  List<NovelPreview> results = const [];
  bool loading = false, loadingMore = false, failed = false, pageFailed = false;
  bool searched = false;
  bool? hasNextPage = false;
  int page = 0;
  int _generation = 0;
  bool _disposed = false;
  String _query = '';

  void selectSource(SourceId? id) {
    if (id == null || id == selectedSource?.id) return;
    selectedSource = sources.firstWhere((source) => source.id == id);
    _generation++;
    results = const [];
    loading = loadingMore = failed = pageFailed = searched = false;
    hasNextPage = false;
    page = 0;
    _notify();
  }

  Future<void> search(String query) async {
    if (selectedSource == null) return;
    if (query.trim().isEmpty) {
      _generation++;
      _query = '';
      results = const [];
      loading = loadingMore = failed = pageFailed = searched = false;
      page = 0;
      hasNextPage = false;
      _notify();
      return;
    }
    _query = query.trim();
    final generation = ++_generation;
    results = const [];
    loading = true;
    loadingMore = failed = pageFailed = false;
    searched = true;
    page = 0;
    hasNextPage = false;
    _notify();
    await _load(generation, 1);
  }

  Future<void> retry() => search(_query);
  Future<void> loadMore() async {
    if (loading || loadingMore || !searched || failed || hasNextPage == false) {
      return;
    }
    loadingMore = true;
    pageFailed = false;
    _notify();
    await _load(_generation, page + 1);
  }

  Future<void> _load(int generation, int requestedPage) async {
    try {
      final result = await searchNovels.execute(
        sourceId: selectedSource!.id,
        query: _query,
        page: requestedPage,
      );
      if (generation != _generation || _disposed) return;
      final seen = results.map((item) => item.media.source).toSet();
      results = List.unmodifiable([
        ...results,
        ...result.results.where((item) => seen.add(item.media.source)),
      ]);
      page = result.page;
      hasNextPage = result.results.isEmpty ? false : result.hasNextPage;
    } catch (_) {
      if (generation != _generation || _disposed) return;
      if (requestedPage == 1) {
        failed = true;
      } else {
        pageFailed = true;
      }
    }
    if (generation != _generation || _disposed) return;
    loading = loadingMore = false;
    _notify();
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    super.dispose();
  }
}
