import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/chapter_list_patterns.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/features/remote_manga/manga_series_page.dart';

Widget _buildTestPage({
  required MangaSeriesDetails details,
  Future<void> Function(BuildContext, MangaChapter, List<MangaChapter>)?
  openChapter,
}) {
  return MaterialApp(
    theme: HikariTheme.darkTheme(),
    home: MangaSeriesPage(
      media: const Media(
        title: 'Test Manga',
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: SourceId('fake'), itemId: 'series'),
      ),
      sourceName: 'Fake Source',
      loadDetails: () async => details,
      openChapter: openChapter ?? (_, _, _) async {},
    ),
  );
}

void main() {
  final ch1 = MangaChapter(
    title: 'Chapter 1',
    source: const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'ch1'),
    chapterNumber: 1,
    canReadPages: true,
  );
  final ch2 = MangaChapter(
    title: 'Chapter 2',
    source: const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'ch2'),
    chapterNumber: 2,
    canReadPages: false,
  );
  final ch3 = MangaChapter(
    title: 'Special Episode',
    source: const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'ch3'),
    chapterNumber: 3,
    canReadPages: true,
  );

  group('MangaSeriesPage & MangaSeriesContent', () {
    testWidgets(
      'Primary "Start reading" CTA opens the first readable chapter in reading order',
      (tester) async {
        MangaChapter? opened;
        List<MangaChapter>? openedSequence;

        // In reverseReadingOrder, chapters list has [ch3, ch2, ch1].
        // Reading order is [ch1, ch2, ch3]. First readable is ch1.
        final details = MangaSeriesDetails(
          metadata: MediaMetadata(title: 'Test Manga'),
          chapterListOrder: ChapterListOrder.reverseReadingOrder,
          chapters: [ch3, ch2, ch1],
        );

        await tester.pumpWidget(
          _buildTestPage(
            details: details,
            openChapter: (_, chapter, sequence) async {
              opened = chapter;
              openedSequence = sequence;
            },
          ),
        );
        await tester.pumpAndSettle();

        expect(find.byType(PrimaryReadingCta), findsOneWidget);
        expect(find.text('Start reading'), findsOneWidget);

        await tester.tap(find.text('Start reading'));
        await tester.pumpAndSettle();

        expect(opened?.source.itemId, 'ch1');
        expect(openedSequence?.map((c) => c.source.itemId), [
          'ch1',
          'ch2',
          'ch3',
        ]);
      },
    );

    testWidgets(
      'Primary "Start reading" skips unreadable first chapter in reading order',
      (tester) async {
        MangaChapter? opened;

        // In reading order, ch2 is unreadable, so first readable is ch3.
        final details = MangaSeriesDetails(
          metadata: MediaMetadata(title: 'Test Manga'),
          chapterListOrder: ChapterListOrder.readingOrder,
          chapters: [ch2, ch3],
        );

        await tester.pumpWidget(
          _buildTestPage(
            details: details,
            openChapter: (_, chapter, _) async {
              opened = chapter;
            },
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Start reading'), findsOneWidget);
        await tester.tap(find.text('Start reading'));
        await tester.pumpAndSettle();

        expect(opened?.source.itemId, 'ch3');
      },
    );

    testWidgets('Tapping sort button reverses chapter list display', (
      tester,
    ) async {
      final details = MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Test Manga'),
        chapterListOrder: ChapterListOrder.readingOrder,
        chapters: [ch1, ch3],
      );

      await tester.pumpWidget(_buildTestPage(details: details));
      await tester.pumpAndSettle();

      expect(find.byType(ChapterControlBar), findsOneWidget);

      // Initially, ch1 is displayed before ch3.
      final ch1InitialY = tester.getTopLeft(find.text('Chapter 1')).dy;
      final ch3InitialY = tester.getTopLeft(find.text('Special Episode')).dy;
      expect(ch1InitialY, lessThan(ch3InitialY));

      // Tap sort descending
      await tester.tap(find.byTooltip('Sort descending'));
      await tester.pumpAndSettle();

      // Reversed: ch3 is now displayed before ch1.
      final ch1ReversedY = tester.getTopLeft(find.text('Chapter 1')).dy;
      final ch3ReversedY = tester.getTopLeft(find.text('Special Episode')).dy;
      expect(ch3ReversedY, lessThan(ch1ReversedY));

      // Tap sort ascending
      await tester.tap(find.byTooltip('Sort ascending'));
      await tester.pumpAndSettle();

      // Restored: ch1 is displayed before ch3 again.
      final ch1RestoredY = tester.getTopLeft(find.text('Chapter 1')).dy;
      final ch3RestoredY = tester.getTopLeft(find.text('Special Episode')).dy;
      expect(ch1RestoredY, lessThan(ch3RestoredY));
    });

    testWidgets('Filtering by query displays matching chapters only', (
      tester,
    ) async {
      final details = MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Test Manga'),
        chapterListOrder: ChapterListOrder.readingOrder,
        chapters: [ch1, ch2, ch3],
      );

      await tester.pumpWidget(_buildTestPage(details: details));
      await tester.pumpAndSettle();

      expect(find.text('3 chapters'), findsOneWidget);
      expect(find.text('Chapter 1'), findsOneWidget);
      expect(find.text('Chapter 2'), findsOneWidget);
      expect(find.text('Special Episode'), findsOneWidget);

      // Tap search icon in ChapterControlBar
      await tester.tap(find.byTooltip('Search chapters'));
      await tester.pumpAndSettle();

      // Enter query matching only Special Episode
      await tester.enterText(find.byType(TextField), 'Special');
      await tester.pumpAndSettle();

      expect(find.text('1 of 3 chapters'), findsOneWidget);
      expect(find.text('Special Episode'), findsOneWidget);
      expect(find.text('Chapter 1'), findsNothing);
      expect(find.text('Chapter 2'), findsNothing);

      // Enter query matching chapter number "1"
      await tester.enterText(find.byType(TextField), '1');
      await tester.pumpAndSettle();

      expect(find.text('1 of 3 chapters'), findsOneWidget);
      expect(find.text('Chapter 1'), findsOneWidget);
      expect(find.text('Chapter 2'), findsNothing);
      expect(find.text('Special Episode'), findsNothing);
    });

    testWidgets(
      'Filtering with zero matches displays "No chapters matching \\"...\\"" with clear button',
      (tester) async {
        final details = MangaSeriesDetails(
          metadata: MediaMetadata(title: 'Test Manga'),
          chapterListOrder: ChapterListOrder.readingOrder,
          chapters: [ch1, ch3],
        );

        await tester.pumpWidget(_buildTestPage(details: details));
        await tester.pumpAndSettle();

        await tester.tap(find.byTooltip('Search chapters'));
        await tester.pumpAndSettle();

        await tester.enterText(find.byType(TextField), 'Nonexistent');
        await tester.pumpAndSettle();

        expect(find.text('0 of 2 chapters'), findsOneWidget);
        expect(find.text('No chapters matching "Nonexistent"'), findsOneWidget);
        expect(find.text('Clear filter'), findsOneWidget);
        expect(find.text('Chapter 1'), findsNothing);

        // Tap clear filter button
        await tester.tap(find.text('Clear filter'));
        await tester.pumpAndSettle();

        // All chapters restored
        expect(find.text('2 chapters'), findsOneWidget);
        expect(find.text('Chapter 1'), findsOneWidget);
        expect(find.text('Special Episode'), findsOneWidget);
        expect(find.text('No chapters matching "Nonexistent"'), findsNothing);
      },
    );

    testWidgets(
      'Unreadable chapters remain disabled and cannot trigger openChapter',
      (tester) async {
        var opens = 0;
        final details = MangaSeriesDetails(
          metadata: MediaMetadata(title: 'Test Manga'),
          chapterListOrder: ChapterListOrder.readingOrder,
          chapters: [ch2],
        );

        await tester.pumpWidget(
          _buildTestPage(
            details: details,
            openChapter: (_, _, _) async => opens++,
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text('Chapter 2'), findsOneWidget);
        expect(find.textContaining('Not readable in Hikari'), findsOneWidget);

        // Primary reading CTA should NOT be present since no readable chapters exist
        expect(find.byType(PrimaryReadingCta), findsNothing);

        // Tapping the unreadable chapter tile does nothing
        await tester.tap(find.text('Chapter 2'));
        await tester.pumpAndSettle();

        expect(opens, 0);
      },
    );

    testWidgets(
      'Desktop width centers content within HikariBreakpoints.maxContentWidth',
      (tester) async {
        tester.view.physicalSize = const Size(1920, 1080);
        tester.view.devicePixelRatio = 1.0;
        addTearDown(() {
          tester.view.resetPhysicalSize();
          tester.view.resetDevicePixelRatio();
        });

        final details = MangaSeriesDetails(
          metadata: MediaMetadata(title: 'Test Manga'),
          chapterListOrder: ChapterListOrder.readingOrder,
          chapters: [ch1],
        );

        await tester.pumpWidget(_buildTestPage(details: details));
        await tester.pumpAndSettle();

        // Verify Center and ConstrainedBox exist with maxWidth = HikariBreakpoints.maxContentWidth
        final constrainedBoxes = tester.widgetList<ConstrainedBox>(
          find.byType(ConstrainedBox),
        );
        final contentBox = constrainedBoxes.firstWhere(
          (box) =>
              box.constraints.maxWidth == HikariBreakpoints.maxContentWidth,
        );
        expect(
          contentBox.constraints.maxWidth,
          HikariBreakpoints.maxContentWidth,
        );

        // The parent or ancestor should be a Center widget
        expect(
          find.ancestor(
            of: find.byWidget(contentBox),
            matching: find.byType(Center),
          ),
          findsOneWidget,
        );
      },
    );
  });
}
