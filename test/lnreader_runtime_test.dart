import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/infrastructure/extensions/lnreader/lnreader_source_loader.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('test/lnreader');
  test(
    'normalizes paginated metadata and rejects duplicate chapter paths',
    () async {
      var duplicate = false;
      var images = false;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            final args = call.arguments as Map;
            return jsonEncode(switch (args['method']) {
              'searchNovels' => [
                {
                  'name': 'Test',
                  'path': '/novel',
                  'cover': 'https://example.org/cover',
                },
              ],
              'parseNovel' => {
                'name': 'Test',
                'summary': 'Summary',
                'author': 'A, B',
                'genres': 'Fantasy',
                'rating': 4.5,
                'status': 'Ongoing',
                'totalPages': 2,
                'chapters': [
                  {
                    'name': 'First',
                    'path': '/chapter1',
                    'chapterNumber': 1.5,
                    'releaseTime': '2025-01-01',
                    'scanlator': ['A', 'B'],
                  },
                ],
              },
              'parsePage' => {
                'chapters': [
                  {
                    'name': 'Second',
                    'path': duplicate ? '/chapter1' : '/chapter2',
                  },
                ],
              },
              'parseChapter' =>
                images
                    ? '<img src="image.png"><img src="javascript:bad()">'
                    : '<script>bad()</script><p>Safe</p>',
              _ => throw StateError('unexpected'),
            });
          });
      final source = LnReaderNovelSource(
        'test',
        'Test',
        channel,
        site: 'https://example.org/',
      );
      final search = await source.searchNovels('query');
      expect(search.hasNextPage, isNull);
      final details = await source.novelDetails(
        search.results.single.media.source,
      );
      expect(details.chapters.length, 2);
      expect(details.metadata.authors, ['A', 'B']);
      expect(details.metadata.rating, 4.5);
      expect(details.metadata.ratingMax, 5);
      expect(search.results.single.metadata.ratingMax, isNull);
      expect(details.chapters.first.chapterNumber, 1.5);
      expect(details.chapters.first.scanlators, ['A', 'B']);
      expect(
        (await source.chapterContent(details.chapters.first.source)).html,
        '<p>Safe</p>',
      );
      images = true;
      final content = await source.chapterContent(
        details.chapters.first.source,
      );
      expect(content.resources.length, 1);
      expect(content.html, contains('hikari-image-0'));
      expect(content.html, isNot(contains('javascript:')));
      duplicate = true;
      await expectLater(
        source.novelDetails(search.results.single.media.source),
        throwsFormatException,
      );
    },
  );
}
