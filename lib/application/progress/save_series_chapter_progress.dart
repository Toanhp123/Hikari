import 'package:hikari/application/progress/progress_session.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/series_continuation.dart';

final class SaveSeriesChapterProgress {
  SaveSeriesChapterProgress(this._continuations, this.series);

  final SeriesContinuationRepository _continuations;
  final SourceMediaRef series;
  final Map<SourceMediaRef, Future<void>> _pendingActivations = {};
  Future<void> _tail = Future<void>.value();
  SourceMediaRef? _lastActivatedChapter;
  SourceMediaRef? _lastChapter;

  Future<void> activate(
    ProgressSession progress,
    SourceMediaRef chapter,
    ProgressPosition initialPosition,
  ) {
    final continuation = SeriesContinuation(series: series, chapter: chapter);
    final pending = _pendingActivations[chapter];
    if (pending != null) return pending;

    late final Future<void> activation;
    activation = _enqueue(() async {
      try {
        if (_lastActivatedChapter != chapter) {
          final initial = progress.initialProgress;
          await progress.save(
            initial?.position ?? initialPosition,
            initial?.completed ?? false,
          );
          _lastActivatedChapter = chapter;
        }
        if (_lastChapter != chapter) {
          await _continuations.save(continuation);
          _lastChapter = chapter;
        }
      } finally {
        if (identical(_pendingActivations[chapter], activation)) {
          _pendingActivations.remove(chapter);
        }
      }
    });
    _pendingActivations[chapter] = activation;
    return activation;
  }

  Future<void> execute(
    ProgressSession progress,
    SourceMediaRef chapter,
    ProgressPosition position,
    bool completed,
  ) {
    final continuation = SeriesContinuation(series: series, chapter: chapter);
    return _enqueue(() async {
      await progress.save(position, completed);
      if (_lastChapter != chapter) {
        await _continuations.save(continuation);
        _lastChapter = chapter;
      }
    });
  }

  Future<void> _enqueue(Future<void> Function() operation) {
    final previous = _tail;
    final next = () async {
      try {
        await previous;
      } catch (_) {
        // A failed older write must not poison a later retry.
      }
      await operation();
    }();
    _tail = next;
    return next;
  }
}
