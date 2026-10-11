import 'package:hikari/domain/media/media.dart';

final class SeriesContinuation {
  SeriesContinuation({required this.series, required this.chapter}) {
    if (series.sourceId.value.isEmpty ||
        series.itemId.isEmpty ||
        chapter.sourceId.value.isEmpty ||
        chapter.itemId.isEmpty ||
        series.sourceId != chapter.sourceId) {
      throw ArgumentError('Continuation requires nonempty same-source refs.');
    }
  }

  final SourceMediaRef series;
  final SourceMediaRef chapter;
}

/// Validates the complete source-owned sequence without changing its order.
/// Parent membership comes from the series source response: chapter refs do not
/// carry a parent ID, so it cannot be inferred from opaque item identifiers.
void validateSeriesChapterSequence(
  SourceMediaRef series,
  Iterable<SourceMediaRef> chapters,
) {
  if (series.sourceId.value.isEmpty || series.itemId.isEmpty) {
    throw StateError('Series reference must be nonempty.');
  }
  final seen = <SourceMediaRef>{};
  for (final chapter in chapters) {
    if (chapter.sourceId != series.sourceId ||
        chapter.itemId.isEmpty ||
        chapter == series ||
        !seen.add(chapter)) {
      throw StateError(
        'Chapter sequence contains invalid or duplicate references.',
      );
    }
  }
}

abstract interface class SeriesContinuationRepository {
  Future<SourceMediaRef?> load(SourceMediaRef series);
  Future<void> save(SeriesContinuation continuation);
}
