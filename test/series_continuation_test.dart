import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/series_continuation.dart';

void main() {
  const parent = SourceMediaRef(sourceId: SourceId('remote'), itemId: 'series');
  const chapter = SourceMediaRef(
    sourceId: SourceId('remote'),
    itemId: 'chapter',
  );

  test('series continuation requires nonempty same-source references', () {
    expect(
      SeriesContinuation(series: parent, chapter: chapter).chapter,
      chapter,
    );
    expect(
      () => SeriesContinuation(
        series: const SourceMediaRef(sourceId: SourceId(''), itemId: 'series'),
        chapter: chapter,
      ),
      throwsArgumentError,
    );
    expect(
      () => SeriesContinuation(
        series: parent,
        chapter: const SourceMediaRef(sourceId: SourceId('remote'), itemId: ''),
      ),
      throwsArgumentError,
    );
    expect(
      () => SeriesContinuation(
        series: parent,
        chapter: const SourceMediaRef(
          sourceId: SourceId('other'),
          itemId: 'chapter',
        ),
      ),
      throwsArgumentError,
    );
  });
}
