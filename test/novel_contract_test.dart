import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';

void main() {
  const ref = SourceMediaRef(
    sourceId: SourceId('novel:test'),
    itemId: 'opaque',
  );
  test(
    'chapter retains optional product metadata without inventing values',
    () {
      final chapter = NovelChapter(
        title: 'Chapter',
        source: ref,
        chapterNumber: 2.5,
        releaseDate: DateTime.utc(2026, 9, 28),
        releaseLabel: 'Yesterday',
        scanlators: ['Team A', 'Team B'],
      );
      expect(chapter.source, ref);
      expect(chapter.chapterNumber, 2.5);
      expect(chapter.scanlators, ['Team A', 'Team B']);
      expect(chapter.releaseLabel, 'Yesterday');
      final absent = NovelChapter(title: 'Unknown', source: ref);
      expect(absent.chapterNumber, isNull);
      expect(absent.releaseDate, isNull);
      expect(() => chapter.scanlators.add('Mutation'), throwsUnsupportedError);
    },
  );
  test('rejects non-finite chapter number', () {
    expect(
      () => NovelChapter(title: 'Bad', source: ref, chapterNumber: double.nan),
      throwsArgumentError,
    );
  });
  test(
    'rich content retains source-owned resource refs separately from HTML',
    () {
      final content = NovelChapterContent(
        html: '<p>Text</p>',
        resources: {'image': ref},
      );
      expect(content.resources['image'], ref);
      expect(() => content.resources.clear(), throwsUnsupportedError);
    },
  );
}
