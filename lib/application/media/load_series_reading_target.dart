import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/series_continuation.dart';

/// Resolves the detail-page CTA against the current readable chapter snapshot.
final class SeriesReadingTarget {
  const SeriesReadingTarget(this.chapter, {this.isContinuation = false});
  final SourceMediaRef? chapter;
  final bool isContinuation;
}

final class LoadSeriesReadingTarget {
  const LoadSeriesReadingTarget(this._continuations);
  final SeriesContinuationRepository _continuations;

  Future<SeriesReadingTarget> execute(
    SourceMediaRef series,
    List<SourceMediaRef> readableChapters,
  ) async {
    validateSeriesChapterSequence(series, readableChapters);
    SourceMediaRef? saved;
    try {
      saved = await _continuations.load(series);
    } catch (_) {
      // An unavailable or malformed continuation must not block a new read.
    }
    if (saved != null && readableChapters.contains(saved)) {
      return SeriesReadingTarget(saved, isContinuation: true);
    }
    return SeriesReadingTarget(readableChapters.firstOrNull);
  }
}
