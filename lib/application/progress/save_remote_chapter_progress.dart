import 'package:hikari/application/progress/progress_session.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/series_continuation.dart';

final class SaveRemoteChapterProgress {
  SaveRemoteChapterProgress(this._continuations, this.series);

  final SeriesContinuationRepository _continuations;
  final SourceMediaRef series;
  Object? _lastTarget;

  Future<void> call(
    Object targetIdentity,
    ProgressSession progress,
    SourceMediaRef chapter,
    ProgressPosition position,
    bool completed,
  ) async {
    final continuation = SeriesContinuation(series: series, chapter: chapter);
    await progress.save(position, completed);
    if (!identical(targetIdentity, _lastTarget)) {
      await _continuations.save(continuation);
      _lastTarget = targetIdentity;
    }
  }
}
