import 'package:flutter/foundation.dart';
import 'package:hikari/application/media/open_novel_chapter.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';

@immutable
final class NovelChapterReaderUiState {
  const NovelChapterReaderUiState({
    required this.target,
    required this.chapterIndex,
    this.openingAdjacent = false,
  });

  final NovelChapterOpenTarget target;
  final int chapterIndex;
  final bool openingAdjacent;
}

final class NovelChapterReaderViewModel extends ChangeNotifier {
  NovelChapterReaderViewModel({
    required NovelChapterOpenTarget initialTarget,
    required List<NovelChapter> chaptersInReadingOrder,
    required this._openChapter,
  }) : _chapters = List.unmodifiable(chaptersInReadingOrder) {
    _validate(_chapters.map((chapter) => chapter.source));
    final index = _chapters.indexWhere(
      (chapter) => chapter.source == initialTarget.chapter.source,
    );
    if (initialTarget.chapter.source.itemId.isEmpty || index < 0) {
      throw ArgumentError(
        'Initial novel chapter must have one sequence entry.',
      );
    }
    _state = NovelChapterReaderUiState(
      target: initialTarget,
      chapterIndex: index,
    );
  }

  final List<NovelChapter> _chapters;
  final OpenNovelChapter _openChapter;
  late NovelChapterReaderUiState _state;
  NovelChapterReaderUiState get state => _state;
  bool get canOpenPrevious => _state.chapterIndex > 0;
  bool get canOpenNext => _state.chapterIndex < _chapters.length - 1;
  NovelChapter? get nextChapter =>
      canOpenNext ? _chapters[_state.chapterIndex + 1] : null;
  int _generation = 0;
  bool _closed = false;

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

  Future<void> openPrevious() => _move(-1);
  Future<void> openNext() => _move(1);

  Future<void> _move(int delta) async {
    if (_closed || _state.openingAdjacent) return;
    final index = _state.chapterIndex + delta;
    if (index < 0 || index >= _chapters.length) return;
    final generation = ++_generation;
    _publish(
      NovelChapterReaderUiState(
        target: _state.target,
        chapterIndex: _state.chapterIndex,
        openingAdjacent: true,
      ),
    );
    try {
      final target = await _openChapter.execute(_chapters[index]);
      if (_closed || generation != _generation) return;
      _publish(NovelChapterReaderUiState(target: target, chapterIndex: index));
    } catch (_) {
      if (_closed || generation != _generation) return;
      _publish(
        NovelChapterReaderUiState(
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
  }

  void _publish(NovelChapterReaderUiState state) {
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
