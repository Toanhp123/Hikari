import 'package:flutter/foundation.dart';
import 'package:hikari/application/media/open_novel_chapter.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/progress/progress.dart';

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
    this.onChapterActivated,
    this.onProgress,
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
  final Future<void> Function(NovelChapterOpenTarget target)?
  onChapterActivated;
  final Future<void> Function(NovelChapterOpenTarget, ProgressPosition, bool)?
  onProgress;
  late NovelChapterReaderUiState _state;
  NovelChapterReaderUiState get state => _state;
  bool get canOpenPrevious => _state.chapterIndex > 0;
  bool get canOpenNext => _state.chapterIndex < _chapters.length - 1;
  NovelChapter? get nextChapter =>
      canOpenNext ? _chapters[_state.chapterIndex + 1] : null;
  Future<void> _activationTail = Future<void>.value();
  Object? _activationError;
  int _generation = 0;
  bool _closed = false;
  bool _closing = false;

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
    if (_closed || _closing || _state.openingAdjacent) return;
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
      _scheduleChapterActivation(target);
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

  Future<void> Function()? _flushReader;

  void registerFlush(Future<void> Function() flush) => _flushReader = flush;

  void activateCurrentChapter() {
    if (_closed) return;
    _scheduleChapterActivation(_state.target);
  }

  void _scheduleChapterActivation(NovelChapterOpenTarget target) {
    final activate = onChapterActivated;
    if (activate == null) return;
    final previous = _activationTail;
    _activationTail = () async {
      await previous;
      try {
        await activate(target);
        _activationError = null;
      } catch (error) {
        _activationError = error;
      }
    }();
  }

  Future<void> flush() async {
    await (_flushReader?.call() ?? Future<void>.value());
    await _activationTail;
    final error = _activationError;
    if (error == null || onChapterActivated == null) return;
    try {
      await onChapterActivated!(_state.target);
      _activationError = null;
    } catch (retryError, retryStackTrace) {
      Error.throwWithStackTrace(retryError, retryStackTrace);
    }
  }

  Future<void> saveProgress(
    NovelChapterOpenTarget target,
    ProgressPosition position,
    bool completed,
  ) async {
    if (_closed || !identical(target, _state.target)) return;
    final save = onProgress;
    if (save != null) {
      await save(target, position, completed);
    } else {
      await target.progress.save(position, completed);
    }
  }

  void beginClose() {
    if (_closing || _closed) return;
    _closing = true;
    _generation++;
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
