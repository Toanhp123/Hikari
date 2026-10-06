import 'package:flutter/foundation.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/prefetch_manga_chapter.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';

@immutable
final class MangaChapterReaderUiState {
  const MangaChapterReaderUiState({
    required this.target,
    required this.chapterIndex,
    this.openingAdjacent = false,
  });

  final MangaChapterOpenTarget target;
  final int chapterIndex;
  final bool openingAdjacent;
}

final class MangaChapterReaderViewModel extends ChangeNotifier {
  MangaChapterReaderViewModel({
    required MangaChapterOpenTarget initialTarget,
    required List<MangaChapter> chaptersInReadingOrder,
    required this._openChapter,
    this._pageListLoader,
    this._chapterPrefetch,
  }) : _chapters = _normalize(chaptersInReadingOrder) {
    final index = _chapters.indexWhere(
      (chapter) => chapter.source == initialTarget.chapter.source,
    );
    if (initialTarget.chapter.source.itemId.isEmpty || index < 0) {
      throw ArgumentError(
        'Initial manga chapter must have one sequence entry.',
      );
    }
    _state = MangaChapterReaderUiState(
      target: initialTarget,
      chapterIndex: index,
    );
  }

  final List<MangaChapter> _chapters;
  final OpenMangaChapter _openChapter;
  final PrefetchMangaChapter? _chapterPrefetch;
  final Future<List<SourceMediaRef>> Function(
    MangaPageSource source,
    SourceMediaRef chapter,
  )?
  _pageListLoader;
  late MangaChapterReaderUiState _state;
  MangaChapterReaderUiState get state => _state;
  bool get canOpenPrevious => _state.chapterIndex > 0;
  bool get canOpenNext => _state.chapterIndex < _chapters.length - 1;
  MangaChapter? get nextChapter =>
      canOpenNext ? _chapters[_state.chapterIndex + 1] : null;
  int _generation = 0;
  bool _closed = false;

  static List<MangaChapter> _normalize(List<MangaChapter> chapters) {
    _validate(chapters.map((chapter) => chapter.source));
    return List.unmodifiable(chapters.where((chapter) => chapter.canReadPages));
  }

  static void _validate(Iterable<SourceMediaRef> refs) {
    final seen = <SourceMediaRef>{};
    for (final ref in refs) {
      if (ref.itemId.isEmpty || !seen.add(ref)) {
        throw ArgumentError(
          'Chapter sequence has missing or duplicate source references.',
        );
      }
    }
  }

  Future<void> prefetchNextChapter({
    required MangaChapterOpenTarget target,
    required int displayedIndex,
  }) async {
    final chapter = nextChapter;
    if (_closed ||
        _state.openingAdjacent ||
        !identical(target, _state.target) ||
        chapter == null ||
        displayedIndex < 0 ||
        displayedIndex >= target.pages.length ||
        target.pages.length - displayedIndex > 5) {
      return;
    }
    await _chapterPrefetch?.execute(chapter);
  }

  void cancelChapterPrefetch() => _chapterPrefetch?.cancelPending();

  void discardChapterPrefetch() => _chapterPrefetch?.discard();

  Future<void> openPrevious() => _move(-1);
  Future<void> openNext() => _move(1);

  Future<void> _move(int delta) async {
    if (_closed || _state.openingAdjacent) return;
    final index = _state.chapterIndex + delta;
    if (index < 0 || index >= _chapters.length) return;
    final generation = ++_generation;
    _publish(
      MangaChapterReaderUiState(
        target: _state.target,
        chapterIndex: _state.chapterIndex,
        openingAdjacent: true,
      ),
    );
    try {
      final target = await _openChapter.execute(
        _chapters[index],
        pageListLoader: _pageListLoader,
      );
      if (_closed || generation != _generation) return;
      _publish(MangaChapterReaderUiState(target: target, chapterIndex: index));
    } catch (_) {
      if (_closed || generation != _generation) return;
      _publish(
        MangaChapterReaderUiState(
          target: _state.target,
          chapterIndex: _state.chapterIndex,
        ),
      );
      rethrow;
    }
  }

  void close() {
    if (_closed) return;
    _closed = true;
    _generation++;
    discardChapterPrefetch();
  }

  void _publish(MangaChapterReaderUiState state) {
    if (_closed) return;
    _state = state;
    notifyListeners();
  }

  @override
  void dispose() {
    close();
    super.dispose();
  }
}
