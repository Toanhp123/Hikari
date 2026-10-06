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

abstract interface class SeriesContinuationRepository {
  Future<SourceMediaRef?> load(SourceMediaRef series);
  Future<void> save(SeriesContinuation continuation);
}
