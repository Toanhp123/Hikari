import 'dart:async';

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

  List<NovelChapter> _chapters;
  final OpenNovelChapter _openChapter;
  final Future<void> Function(NovelChapterOpenTarget target)?
  onChapterActivated;
  final Future<void> Function(NovelChapterOpenTarget, ProgressPosition, bool)?
  onProgress;
  late NovelChapterReaderUiState _state;
  NovelChapterReaderUiState get state => _state;
  NovelChapter get currentChapter => _chapters[_state.chapterIndex];
  bool get canOpenPrevious => _state.chapterIndex > 0;
  bool get canOpenNext => _state.chapterIndex < _chapters.length - 1;
  NovelChapter? get nextChapter =>
      canOpenNext ? _chapters[_state.chapterIndex + 1] : null;
  Future<void> _activationTail = Future<void>.value();
  Object? _activationError;
  int _generation = 0;
  bool _closed = false;
  bool _closing = false;
  bool get isClosing => _closing || _closed;

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

  /// Updates adjacent navigation without replacing the active reader target.
  /// A delayed response cannot reset the current chapter or reading position.
  bool reconcileSequence(List<NovelChapter> chapters) {
    if (_closed || _closing || _state.openingAdjacent) return false;
    final normalized = List<NovelChapter>.unmodifiable(chapters);
    _validate(normalized.map((item) => item.source));
    if (normalized.any(
      (item) => item.source.sourceId != _state.target.chapter.source.sourceId,
    )) {
      throw ArgumentError('Chapter sequence has foreign source references.');
    }
    final index = normalized.indexWhere(
      (item) => item.source == _state.target.chapter.source,
    );
    if (index < 0) return false;
    _chapters = normalized;
    _publish(
      NovelChapterReaderUiState(target: _state.target, chapterIndex: index),
    );
    return true;
  }

  final _closedSignal = Completer<void>();
  final _pendingSourceWork = <Future<void>>{};

  /// Retains this reader's source ownership until dismissed work settles.
  Future<T> sourceWork<T>(Future<T> Function() operation) {
    if (_closing || _closed) {
      return Future<T>.error(StateError('Reader is closing.'));
    }
    final result = Future<T>.sync(operation);
    late final Future<void> pending;
    pending = result
        .then<void>((_) {}, onError: (Object _, StackTrace _) {})
        .whenComplete(() => _pendingSourceWork.remove(pending));
    _pendingSourceWork.add(pending);
    return result;
  }

  Future<void> get settled async {
    await _closedSignal.future;
    while (_pendingSourceWork.isNotEmpty) {
      await Future.wait(_pendingSourceWork.toList());
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
      final target = await sourceWork(
        () => _openChapter.execute(_chapters[index]),
      );
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
    _closedSignal.complete();
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
