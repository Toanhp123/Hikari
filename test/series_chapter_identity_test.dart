import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/series_continuation.dart';

void main() {
  const series = SourceMediaRef(sourceId: SourceId('source'), itemId: 'series');
  const chapter = SourceMediaRef(
    sourceId: SourceId('source'),
    itemId: 'chapter',
  );
  test(
    'validates exact unique same-source sequence without numeric sorting',
    () {
      expect(
        () => validateSeriesChapterSequence(series, [chapter]),
        returnsNormally,
      );
      expect(
        () => validateSeriesChapterSequence(series, [chapter, chapter]),
        throwsStateError,
      );
      expect(
        () => validateSeriesChapterSequence(series, [series]),
        throwsStateError,
      );
      expect(
        () => validateSeriesChapterSequence(series, [
          const SourceMediaRef(sourceId: SourceId('other'), itemId: 'chapter'),
        ]),
        throwsStateError,
      );
      expect(
        () => validateSeriesChapterSequence(series, [
          const SourceMediaRef(sourceId: SourceId('source'), itemId: ''),
        ]),
        throwsStateError,
      );
      expect(() => validateSeriesChapterSequence(series, []), returnsNormally);
    },
  );
}
