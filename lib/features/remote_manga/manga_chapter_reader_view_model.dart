import 'package:flutter/foundation.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';

@immutable
final class MangaChapterReaderState {
  const MangaChapterReaderState({
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
  }) : _chapters = _normalize(chaptersInReadingOrder) {
    final index = _chapters.indexWhere(
      (chapter) => chapter.source == initialTarget.chapter.source,
    );
    if (initialTarget.chapter.source.itemId.isEmpty || index < 0) {
      throw ArgumentError(
        'Initial manga chapter must have one sequence entry.',
      );
    }
    _state = MangaChapterReaderState(
      target: initialTarget,
      chapterIndex: index,
    );
  }

  final List<MangaChapter> _chapters;
  final OpenMangaChapter _openChapter;
  late MangaChapterReaderState _state;
  MangaChapterReaderState get state => _state;
  bool get canOpenPrevious => _state.chapterIndex > 0;
  bool get canOpenNext => _state.chapterIndex < _chapters.length - 1;
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

  Future<void> previous() => _move(-1);
  Future<void> next() => _move(1);

  Future<void> _move(int delta) async {
    if (_closed || _state.openingAdjacent) return;
    final index = _state.chapterIndex + delta;
    if (index < 0 || index >= _chapters.length) return;
    final generation = ++_generation;
    _publish(
      MangaChapterReaderState(
        target: _state.target,
        chapterIndex: _state.chapterIndex,
        openingAdjacent: true,
      ),
    );
    try {
      final target = await _openChapter.execute(_chapters[index]);
      if (_closed || generation != _generation) return;
      _publish(MangaChapterReaderState(target: target, chapterIndex: index));
    } catch (_) {
      if (_closed || generation != _generation) return;
      _publish(
        MangaChapterReaderState(
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

  void _publish(MangaChapterReaderState state) {
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
