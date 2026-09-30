import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';
import 'package:hikari/features/novel_reader/novel_reader_page.dart';

void main() {
  const ref = SourceMediaRef(sourceId: SourceId.local, itemId: 'book');
  final png = base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg==',
  );
  MediaProgress saved(ProgressPosition position, {bool completed = false}) =>
      MediaProgress(
        media: ref,
        position: position,
        completed: completed,
        updatedAt: DateTime.utc(2026),
      );
  testWidgets(
    'manga clamps stale resume and saves only displayed frames; reread clears completion',
    (tester) async {
      final writes = <(ProgressPosition, bool)>[];
      await tester.pumpWidget(
        MaterialApp(
          home: MangaReaderPage(
            title: 'Pages',
            initialProgress: saved(PagePosition(pageIndex: 8, pageCount: 10)),
            saveProgress: (position, completed) async =>
                writes.add((position, completed)),
            loadPages: () async => [ref, ref],
            readPage: (_) async => png,
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.runAsync(
        () async => Future<void>.delayed(const Duration(milliseconds: 100)),
      );
      await tester.pumpAndSettle();
      expect(find.text('Page 2 of 2'), findsOneWidget);
      expect(writes, isNotEmpty);
      expect(writes.last.$2, isTrue);
      await tester.tap(find.byTooltip('Previous page'));
      await tester.pumpAndSettle();
      await tester.runAsync(
        () async => Future<void>.delayed(const Duration(milliseconds: 100)),
      );
      await tester.pumpAndSettle();
      expect((writes.last.$1 as PagePosition).pageIndex, 0);
      expect(writes.last.$2, isFalse);
    },
  );
  testWidgets('completed manga untouched reopen preserves completion', (
    tester,
  ) async {
    final writes = <(ProgressPosition, bool)>[];
    await tester.pumpWidget(
      MaterialApp(
        home: MangaReaderPage(
          title: 'Completed',
          initialProgress: saved(
            PagePosition(pageIndex: 2, pageCount: 3),
            completed: true,
          ),
          saveProgress: (position, completed) async =>
              writes.add((position, completed)),
          loadPages: () async => [ref, ref, ref],
          readPage: (_) async => png,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.runAsync(() async {
      await precacheImage(
        tester.widget<Image>(find.byType(Image)).image,
        tester.element(find.byType(MangaReaderPage)),
      );
    });
    await tester.pumpAndSettle();
    expect(find.text('Page 1 of 3'), findsOneWidget);
    expect(find.text('Could not decode this page.'), findsNothing);
    expect(writes, isEmpty);
    await tester.pumpWidget(const SizedBox());
    expect(writes, isEmpty);
  });
  testWidgets(
    'completed manga meaningful reread becomes active then complete',
    (tester) async {
      final writes = <(ProgressPosition, bool)>[];
      await tester.pumpWidget(
        MaterialApp(
          home: MangaReaderPage(
            title: 'Completed',
            initialProgress: saved(
              PagePosition(pageIndex: 2, pageCount: 3),
              completed: true,
            ),
            saveProgress: (position, completed) async =>
                writes.add((position, completed)),
            loadPages: () async => [ref, ref, ref],
            readPage: (_) async => png,
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.runAsync(() async {
        await precacheImage(
          tester.widget<Image>(find.byType(Image)).image,
          tester.element(find.byType(MangaReaderPage)),
        );
      });
      await tester.pumpAndSettle();
      expect(writes, isEmpty);
      await tester.tap(find.byTooltip('Next page'));
      await tester.pumpAndSettle();
      await tester.runAsync(() async {
        await precacheImage(
          tester.widget<Image>(find.byType(Image)).image,
          tester.element(find.byType(MangaReaderPage)),
        );
      });
      await tester.pumpAndSettle();
      expect((writes.last.$1 as PagePosition).pageIndex, 1);
      expect(writes.last.$2, isFalse);
      await tester.tap(find.byTooltip('Next page'));
      await tester.pumpAndSettle();
      await tester.runAsync(() async {
        await precacheImage(
          tester.widget<Image>(find.byType(Image)).image,
          tester.element(find.byType(MangaReaderPage)),
        );
      });
      await tester.pumpAndSettle();
      expect((writes.last.$1 as PagePosition).pageIndex, 2);
      expect(writes.last.$2, isTrue);
    },
  );
  testWidgets(
    'manga decode failure never writes progress; completed starts first',
    (tester) async {
      var writes = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: MangaReaderPage(
            title: 'Pages',
            initialProgress: saved(
              PagePosition(pageIndex: 1, pageCount: 2),
              completed: true,
            ),
            saveProgress: (_, _) async {
              writes++;
            },
            loadPages: () async => [ref, ref],
            readPage: (_) async => Uint8List.fromList([1, 2]),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.runAsync(
        () async => Future<void>.delayed(const Duration(milliseconds: 100)),
      );
      await tester.pumpAndSettle();
      expect(find.text('Page 1 of 2'), findsOneWidget);
      expect(find.text('Could not decode this page.'), findsOneWidget);
      expect(writes, 0);
    },
  );
  testWidgets(
    'novel restores after layout, debounces and flushes final position',
    (tester) async {
      final writes = <(ProgressPosition, bool)>[];
      await tester.pumpWidget(
        MaterialApp(
          home: NovelReaderPage(
            title: 'Text',
            initialProgress: saved(TextPosition(progression: 0.5)),
            saveProgress: (position, completed) async =>
                writes.add((position, completed)),
            loadText: () async =>
                List.filled(200, 'Readable words on a long line.').join('\n'),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final scroll = tester
          .widget<SingleChildScrollView>(find.byType(SingleChildScrollView))
          .controller!;
      expect(
        scroll.offset / scroll.position.maxScrollExtent,
        closeTo(0.5, 0.001),
      );
      expect(writes, isEmpty);
      scroll.jumpTo(scroll.position.maxScrollExtent * 0.75);
      await tester.pump(const Duration(milliseconds: 100));
      expect(writes, isEmpty);
      await tester.pump(const Duration(milliseconds: 500));
      expect(
        (writes.last.$1 as TextPosition).progression,
        closeTo(0.75, 0.001),
      );
      expect(writes.last.$2, isFalse);
      scroll.jumpTo(scroll.position.maxScrollExtent);
      await tester.pumpWidget(const SizedBox());
      expect(writes.last.$2, isTrue);
      expect((writes.last.$1 as TextPosition).progression, 1);
    },
  );
  testWidgets(
    'early lifecycle and dispose cannot overwrite delayed novel restore',
    (tester) async {
      final content = Completer<String>();
      var writes = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: NovelReaderPage(
            title: 'Delayed',
            initialProgress: saved(TextPosition(progression: 0.8)),
            saveProgress: (_, _) async {
              writes++;
            },
            loadText: () => content.future,
          ),
        ),
      );
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      await tester.pumpWidget(const SizedBox());
      content.complete('Late content');
      await tester.pump();
      expect(writes, 0);
      expect(tester.takeException(), isNull);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    },
  );
  testWidgets('background flush saves latest scroll before debounce', (
    tester,
  ) async {
    final writes = <double>[];
    await tester.pumpWidget(
      MaterialApp(
        home: NovelReaderPage(
          title: 'Background',
          saveProgress: (position, _) async =>
              writes.add((position as TextPosition).progression),
          loadText: () async => List.filled(100, 'Long text').join('\n'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final scroll = tester
        .widget<SingleChildScrollView>(find.byType(SingleChildScrollView))
        .controller!;
    scroll.jumpTo(scroll.position.maxScrollExtent * 0.4);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    await tester.pump();
    expect(writes.single, closeTo(0.4, 0.001));
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpWidget(const SizedBox());
    expect(writes, hasLength(1));
  });
  testWidgets('completed novel starts at top; failed content does not save', (
    tester,
  ) async {
    final writes = <bool>[];
    await tester.pumpWidget(
      MaterialApp(
        home: NovelReaderPage(
          title: 'Text',
          initialProgress: saved(TextPosition(progression: 1), completed: true),
          saveProgress: (_, completed) async => writes.add(completed),
          loadText: () async => List.filled(100, 'Text').join('\n'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final scroll = tester
        .widget<SingleChildScrollView>(find.byType(SingleChildScrollView))
        .controller!;
    expect(scroll.offset, 0);
    await tester.pumpWidget(const SizedBox());
    expect(writes, isEmpty);
    writes.clear();
    await tester.pumpWidget(
      MaterialApp(
        home: NovelReaderPage(
          title: 'Missing',
          saveProgress: (_, completed) async => writes.add(completed),
          loadText: () async => throw StateError('missing'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox());
    expect(writes, isEmpty);
  });
}
