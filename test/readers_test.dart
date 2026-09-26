import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';
import 'package:hikari/features/novel_reader/novel_reader_page.dart';
import 'package:hikari/features/player/player_page.dart';

void main() {
  const page = SourceMediaRef(sourceId: SourceId.local, itemId: 'page-1');
  final png = Uint8List.fromList(<int>[
    0x89,
    0x50,
    0x4e,
    0x47,
    0x0d,
    0x0a,
    0x1a,
    0x0a,
    0x00,
    0x00,
    0x00,
    0x0d,
    0x49,
    0x48,
    0x44,
    0x52,
    0x00,
    0x00,
    0x00,
    0x01,
    0x00,
    0x00,
    0x00,
    0x01,
    0x08,
    0x06,
    0x00,
    0x00,
    0x00,
    0x1f,
    0x15,
    0xc4,
    0x89,
    0x00,
    0x00,
    0x00,
    0x0d,
    0x49,
    0x44,
    0x41,
    0x54,
    0x78,
    0x9c,
    0x63,
    0xf8,
    0xcf,
    0xc0,
    0x00,
    0x00,
    0x03,
    0x01,
    0x01,
    0x00,
    0x18,
    0xdd,
    0x8d,
    0xb0,
    0x00,
    0x00,
    0x00,
    0x00,
    0x49,
    0x45,
    0x4e,
    0x44,
    0xae,
    0x42,
    0x60,
    0x82,
  ]);

  testWidgets('player page supplies title and playback widget', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: PlayerPage(title: 'Now playing', playback: Text('Playback')),
      ),
    );

    expect(find.text('Now playing'), findsOneWidget);
    expect(find.text('Playback'), findsOneWidget);
  });

  testWidgets('novel reader loads readable text and retries errors', (
    tester,
  ) async {
    var attempts = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: NovelReaderPage(
          title: 'Chapter one',
          loadText: () async {
            attempts++;
            if (attempts == 1) throw StateError('offline');
            return 'Readable novel text.';
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Could not load this novel.'), findsOneWidget);

    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(find.text('Readable novel text.'), findsOneWidget);
  });

  testWidgets('failed last manga page still allows previous navigation', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: MangaReaderPage(
          title: 'Pages',
          loadPages: () async => [page, page.copyWith(itemId: 'bad')],
          readPage: (ref) async {
            if (ref.itemId == 'bad') throw StateError('unreadable');
            return png;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Next page'));
    await tester.pumpAndSettle();
    expect(find.text('Could not load this page.'), findsOneWidget);
    await tester.tap(find.byTooltip('Previous page'));
    await tester.pumpAndSettle();
    expect(find.text('Page 1 of 2'), findsOneWidget);
  });

  testWidgets('manga reader loads one page and moves next and previous', (
    tester,
  ) async {
    var readIds = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        home: MangaReaderPage(
          title: 'Chapter one',
          loadPages: () async => <SourceMediaRef>[
            page,
            page.copyWith(itemId: 'page-2'),
          ],
          readPage: (ref) async {
            readIds.add(ref.itemId);
            return png;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Page 1 of 2'), findsOneWidget);
    expect(readIds, <String>['page-1']);

    await tester.tap(find.byTooltip('Next page'));
    await tester.pumpAndSettle();
    expect(find.text('Page 2 of 2'), findsOneWidget);
    expect(readIds, <String>['page-1', 'page-2']);

    await tester.tap(find.byTooltip('Previous page'));
    await tester.pumpAndSettle();
    expect(find.text('Page 1 of 2'), findsOneWidget);
  });
}

extension on SourceMediaRef {
  SourceMediaRef copyWith({String? itemId}) =>
      SourceMediaRef(sourceId: sourceId, itemId: itemId ?? this.itemId);
}
