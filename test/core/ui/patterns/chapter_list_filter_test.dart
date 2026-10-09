import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/patterns/chapter_list_filter.dart';

void main() {
  test(
    'matches normalized titles and fractional or integer chapter numbers',
    () {
      expect(matchesChapterQuery('special', title: 'SPECIAL Episode'), isTrue);
      expect(
        matchesChapterQuery('12', title: 'Extra', chapterNumber: 12),
        isTrue,
      );
      expect(
        matchesChapterQuery('12.5', title: 'Extra', chapterNumber: 12.5),
        isTrue,
      );
      expect(matchesChapterQuery('missing', title: 'Extra'), isFalse);
      expect(matchesChapterQuery('', title: 'Extra'), isTrue);
    },
  );
}
