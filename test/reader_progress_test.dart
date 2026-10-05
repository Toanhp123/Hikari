import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';
import 'package:hikari/features/novel_reader/novel_reader_page.dart';
import 'package:hikari/features/novel_reader/widgets/novel_content_view.dart';

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
    final displayed = <int>[];
    await tester.pumpWidget(
      MaterialApp(
        home: MangaReaderPage(
          title: 'Completed',
          initialProgress: saved(
            PagePosition(pageIndex: 2, pageCount: 3),
            completed: true,
          ),
          onPageDisplayed: displayed.add,
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
    expect(displayed, [0]);
    expect(writes, isEmpty);
    await tester.pumpWidget(const SizedBox());
    expect(writes, isEmpty);
  });
  testWidgets(
    'completed manga meaningful reread becomes active then complete',
    (tester) async {
      final writes = <(ProgressPosition, bool)>[];
      final displayed = <int>[];
      await tester.pumpWidget(
        MaterialApp(
          home: MangaReaderPage(
            title: 'Completed',
            initialProgress: saved(
              PagePosition(pageIndex: 2, pageCount: 3),
              completed: true,
            ),
            onPageDisplayed: displayed.add,
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
      expect(displayed, [0, 1]);
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
      expect(displayed, [0, 1, 2]);
      expect((writes.last.$1 as PagePosition).pageIndex, 2);
      expect(writes.last.$2, isTrue);
    },
  );
  testWidgets('manga display callback fires once only after image decode', (
    tester,
  ) async {
    final displayed = <int>[];
    await tester.pumpWidget(
      MaterialApp(
        home: MangaReaderPage(
          title: 'Pages',
          loadPages: () async => [ref],
          readPage: (_) async => Uint8List.fromList([1, 2]),
          reloadPage: (_) async => png,
          onPageDisplayed: displayed.add,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(displayed, isEmpty);
    expect(find.text('Could not decode this page.'), findsOneWidget);
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    await tester.runAsync(
      () async => Future<void>.delayed(const Duration(milliseconds: 100)),
    );
    await tester.pumpAndSettle();
    expect(displayed, [0]);
    await tester.pump();
    expect(displayed, [0]);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('bytes alone do not notify before a decoded frame is painted', (
    tester,
  ) async {
    final displayed = <int>[];
    final bytes = Completer<Uint8List>();
    await tester.pumpWidget(
      MaterialApp(
        home: MangaReaderPage(
          title: 'Frame',
          loadPages: () async => [ref],
          readPage: (_) => bytes.future,
          onPageDisplayed: displayed.add,
        ),
      ),
    );
    await tester.pump();
    bytes.complete(png);
    expect(displayed, isEmpty);
    await tester.pump();
    final image = tester.widget<Image>(find.byType(Image));
    await tester.runAsync(() async {
      final decoded = Completer<void>();
      final stream = image.image.resolve(ImageConfiguration.empty);
      final listener = ImageStreamListener((_, _) => decoded.complete());
      stream.addListener(listener);
      await decoded.future;
      stream.removeListener(listener);
    });
    await tester.pump();
    expect(displayed, [0]);
    await tester.tap(find.byType(InteractiveViewer));
    await tester.pumpAndSettle();
    expect(displayed, [0]);
  });

  testWidgets('save failure does not repeat display notification', (
    tester,
  ) async {
    final displayed = <int>[];
    await tester.pumpWidget(
      MaterialApp(
        home: MangaReaderPage(
          title: 'Pages',
          loadPages: () async => [ref],
          readPage: (_) async => png,
          onPageDisplayed: displayed.add,
          saveProgress: (_, _) async => throw StateError('offline'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(displayed, [0]);
    await tester.pump(const Duration(milliseconds: 100));
    expect(displayed, [0]);
    await tester.pumpWidget(const SizedBox());
  });

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
    'manga decode retry reloads fresh bytes and saves only decoded frame',
    (tester) async {
      var reads = 0;
      var reloads = 0;
      var writes = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: MangaReaderPage(
            title: 'Retry',
            loadPages: () async => [ref],
            readPage: (_) async {
              reads++;
              return Uint8List.fromList([1, 2]);
            },
            reloadPage: (_) async {
              reloads++;
              return png;
            },
            saveProgress: (_, _) async {
              writes++;
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Could not decode this page.'), findsOneWidget);
      expect(writes, 0);
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      await tester.runAsync(
        () async => Future<void>.delayed(const Duration(milliseconds: 100)),
      );
      await tester.pumpAndSettle();
      expect(reads, 1);
      expect(reloads, 1);
      expect(find.text('Could not decode this page.'), findsNothing);
      expect(writes, 1);
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
  testWidgets(
    'novel reload preserves live progression and does not save progress',
    (tester) async {
      final writes = <(ProgressPosition, bool)>[];
      var loads = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: NovelReaderPage(
            title: 'Reload',
            initialProgress: saved(TextPosition(progression: 0.2)),
            saveProgress: (position, completed) async =>
                writes.add((position, completed)),
            loadContent: () async => RichReadingContent(
              html:
                  '<p>${List.filled(300, 'First chapter body.').join(' ')}</p>',
            ),
            readResource: (_) async => Uint8List(0),
            reloadContent: () async {
              loads++;
              return RichReadingContent(
                html:
                    '<p>${List.filled(80, 'Short chapter body.').join(' ')}</p>',
              );
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      final scroll = tester
          .widget<SingleChildScrollView>(find.byType(SingleChildScrollView))
          .controller!;
      scroll.jumpTo(scroll.position.maxScrollExtent * 0.65);
      await tester.pump(const Duration(milliseconds: 600));
      writes.clear();
      final before = scroll.offset / scroll.position.maxScrollExtent;
      await tester.tap(find.byTooltip('Reload chapter'));
      await tester.pump();
      await tester.pumpAndSettle();
      final after = scroll.offset / scroll.position.maxScrollExtent;
      expect(loads, 1);
      expect(after, closeTo(before, 0.02));
      expect(find.text('${(before * 100).toInt()}%'), findsOneWidget);
      expect(writes, isEmpty);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      await tester.pump();
      expect(writes, isEmpty);
    },
  );
  testWidgets(
    'illustration retry reloads bytes and changed ref reads new resource',
    (tester) async {
      const first = SourceMediaRef(sourceId: SourceId('source'), itemId: 'one');
      const second = SourceMediaRef(
        sourceId: SourceId('source'),
        itemId: 'two',
      );
      final decodeErrorBytes = Uint8List.fromList([0, 0, 0, 0, 0, 0, 0, 0]);
      var retries = 0;
      final reads = <String>[];
      RichReadingContent content(SourceMediaRef resource) => RichReadingContent(
        html: '<img src="image">',
        resources: {'image': resource},
      );
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: NovelContentView(
              content: content(first),
              readResource: (resource) async {
                reads.add(resource.itemId);
                return decodeErrorBytes;
              },
              reloadResource: (resource) async {
                retries++;
                return png;
              },
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Could not load illustration.'), findsOneWidget);
      await tester.tap(find.text('Retry illustration'));
      await tester.pumpAndSettle();
      await tester.runAsync(
        () async => Future<void>.delayed(const Duration(milliseconds: 100)),
      );
      await tester.pumpAndSettle();
      expect(retries, 1);
      expect(find.text('Could not load illustration.'), findsNothing);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: NovelContentView(
              content: content(second),
              readResource: (resource) async {
                reads.add(resource.itemId);
                return png;
              },
              reloadResource: (_) async => png,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(reads, ['one', 'two']);
    },
  );

  testWidgets(
    'repeated non-scrollable reload preserves logical progression without saves',
    (tester) async {
      final writes = <(ProgressPosition, bool)>[];
      var reloads = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: NovelReaderPage(
            title: 'Short reload',
            initialProgress: saved(TextPosition(progression: 0.65)),
            saveProgress: (position, completed) async =>
                writes.add((position, completed)),
            loadContent: () async => RichReadingContent(
              html: '<p>Long body ${List.filled(250, 'word').join(' ')}</p>',
            ),
            readResource: (_) async => Uint8List(0),
            reloadContent: () async {
              reloads++;
              return RichReadingContent(html: '<p>short</p>');
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      final scroll = tester
          .widget<SingleChildScrollView>(find.byType(SingleChildScrollView))
          .controller!;
      scroll.jumpTo(scroll.position.maxScrollExtent * 0.65);
      await tester.pump(const Duration(milliseconds: 600));
      writes.clear();
      for (var i = 0; i < 2; i++) {
        await tester.tap(find.byTooltip('Reload chapter'));
        await tester.pumpAndSettle();
        expect(scroll.position.maxScrollExtent, 0);
        expect(find.text('65%'), findsOneWidget);
        expect(writes, isEmpty);
      }
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      await tester.pump();
      expect(reloads, 2);
      expect(writes, isEmpty);
      await tester.pumpWidget(const SizedBox());
      expect(writes, isEmpty);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    },
  );

  testWidgets('pending reload restores progression after concurrent scroll', (
    tester,
  ) async {
    final pendingReload = Completer<RichReadingContent>();
    final writes = <(ProgressPosition, bool)>[];
    await tester.pumpWidget(
      MaterialApp(
        home: NovelReaderPage(
          title: 'Pending reload',
          saveProgress: (position, completed) async =>
              writes.add((position, completed)),
          loadContent: () async => RichReadingContent(
            html: '<p>${List.filled(250, 'starting body').join(' ')}</p>',
          ),
          readResource: (_) async => Uint8List(0),
          reloadContent: () => pendingReload.future,
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Reload chapter'));
    await tester.pump();
    final scroll = tester
        .widget<SingleChildScrollView>(find.byType(SingleChildScrollView))
        .controller!;
    scroll.jumpTo(scroll.position.maxScrollExtent * 0.4);
    writes.clear();
    pendingReload.complete(
      RichReadingContent(
        html: '<p>${List.filled(250, 'fresh body').join(' ')}</p>',
      ),
    );
    await tester.pumpAndSettle();
    expect(scroll.offset / scroll.position.maxScrollExtent, closeTo(0.4, 0.02));
    expect(writes, isEmpty);
    await tester.pumpWidget(const SizedBox());
    expect(writes, hasLength(1));
    expect((writes.single.$1 as TextPosition).progression, closeTo(0.4, 0.02));
  });

  testWidgets('novel failed reload retains content and progress state', (
    tester,
  ) async {
    final writes = <(ProgressPosition, bool)>[];
    var failures = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: NovelReaderPage(
          title: 'Reload failure',
          saveProgress: (position, completed) async =>
              writes.add((position, completed)),
          loadContent: () async =>
              RichReadingContent(html: '<p>Visible chapter</p>'),
          readResource: (_) async => Uint8List(0),
          reloadContent: () async {
            failures++;
            throw StateError('offline');
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Reload chapter'));
    await tester.pumpAndSettle();
    expect(failures, 1);
    expect(find.byType(NovelContentView), findsOneWidget);
    expect(find.text('Could not reload this chapter.'), findsOneWidget);
    expect(writes, isEmpty);
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
