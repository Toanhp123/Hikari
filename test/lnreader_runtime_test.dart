import 'dart:convert';

import 'package:flutter/services.dart';

import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/extensions/lnreader/lnreader_novel_source.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('test/lnreader');
  test(
    'normalizes paginated metadata and rejects duplicate chapter paths',
    () async {
      var duplicate = false;
      var images = false;
      var rowCountSingle = false;
      final chapterCalls = <List<Object?>>[];
      final pageCalls = <List<Object?>>[];
      String? artworkUrl;
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'readResource') {
              artworkUrl = (call.arguments as Map)['url'] as String;
              return Uint8List.fromList([7, 8, 9]);
            }
            final args = call.arguments as Map;
            if (args['method'] == 'parseNovel') {
              chapterCalls.add(
                jsonDecode(args['args'] as String) as List<Object?>,
              );
            }
            if (args['method'] == 'parsePage') {
              pageCalls.add(
                jsonDecode(args['args'] as String) as List<Object?>,
              );
            }
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
                'totalPages': rowCountSingle ? 1 : 2,
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
      final search = await source.search('query');
      expect(search.hasNextPage, isNull);
      expect(search.results.single.metadata.cover, isNotNull);
      expect(source, isA<ArtworkSource>());
      expect(await source.readArtwork(search.results.single.metadata.cover!), [
        7,
        8,
        9,
      ]);
      expect(artworkUrl, 'https://example.org/cover');
      final details = await source.loadDetails(
        search.results.single.media.source,
      );
      expect(details.chapters.length, 2);
      expect(details.chapterListOrder, ChapterListOrder.readingOrder);
      expect(details.chaptersInReadingOrder, details.chapters);
      expect(details.chapters.map((chapter) => chapter.title), [
        'First',
        'Second',
      ]);
      expect(chapterCalls, [
        ['/novel'],
      ]);
      expect(pageCalls, [
        ['/novel', '2'],
      ]);
      duplicate = false;
      chapterCalls.clear();
      pageCalls.clear();
      rowCountSingle = true;
      final singlePage = await source.loadDetails(
        search.results.single.media.source,
      );
      expect(singlePage.chapters.map((chapter) => chapter.title), ['First']);
      expect(chapterCalls, [
        ['/novel'],
      ]);
      expect(pageCalls, isEmpty);
      rowCountSingle = false;
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
        source.loadDetails(search.results.single.media.source),
        throwsFormatException,
      );
    },
  );
}
