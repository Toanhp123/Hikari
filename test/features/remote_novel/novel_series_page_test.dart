import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/core/ui/patterns/chapter_control_bar.dart';
import 'package:hikari/core/ui/patterns/primary_reading_cta.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/remote_novel/novel_series_page.dart';

class _FakeNovelSeriesSource implements NovelSeriesSource {
  _FakeNovelSeriesSource(this.details);
  final NovelDetails details;

  @override
  final SourceId id = const SourceId('fake');

  @override
  String get name => 'Fake Source';

  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async => details;
}

Widget _buildTestPage({
  required NovelDetails details,
  Future<void> Function(
    BuildContext,
    NovelChapter,
    List<NovelChapter>,
    bool Function(),
  )?
  openChapter,
}) {
  final source = _FakeNovelSeriesSource(details);
  final target = NovelSeriesOpenTarget(
    const Media(
      title: 'Test Novel',
      type: MediaType.lightNovel,
      source: SourceMediaRef(sourceId: SourceId('fake'), itemId: 'series'),
    ),
    source: source,
  );
  return MaterialApp(
    theme: HikariTheme.darkTheme(),
    home: NovelSeriesPage(
      target: target,
      openChapter: openChapter ?? (_, _, _, _) async {},
    ),
  );
}

void main() {
  final ch1 = NovelChapter(
    title: 'Chapter 1',
    source: const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'ch1'),
    chapterNumber: 1,
  );
  final ch2 = NovelChapter(
    title: 'Chapter 2',
    source: const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'ch2'),
    chapterNumber: 2,
  );
  final ch3 = NovelChapter(
    title: 'Special Episode',
    source: const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'ch3'),
    chapterNumber: 3,
  );

  group('NovelSeriesPage & NovelSeriesContent', () {
    testWidgets(
      'Primary "Start reading" CTA opens first chapter in reading order',
      (tester) async {
        NovelChapter? opened;
        List<NovelChapter>? openedSequence;

        // In reverseReadingOrder, chapters list has [ch3, ch2, ch1].
        // Reading order is [ch1, ch2, ch3]. First readable is ch1.
        final details = NovelDetails(
          metadata: MediaMetadata(title: 'Test Novel'),
          chapterListOrder: ChapterListOrder.reverseReadingOrder,
          chapters: [ch3, ch2, ch1],
        );

        await tester.pumpWidget(
          _buildTestPage(
            details: details,
            openChapter: (_, chapter, sequence, _) async {
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

    testWidgets('Tapping sort button reverses chapter list display', (
      tester,
    ) async {
      final details = NovelDetails(
        metadata: MediaMetadata(title: 'Test Novel'),
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
      await tester.tap(find.byTooltip('Reverse chapter order'));
      await tester.pumpAndSettle();

      // Reversed: ch3 is now displayed before ch1.
      final ch1ReversedY = tester.getTopLeft(find.text('Chapter 1')).dy;
      final ch3ReversedY = tester.getTopLeft(find.text('Special Episode')).dy;
      expect(ch3ReversedY, lessThan(ch1ReversedY));

      // Tap sort ascending
      await tester.tap(find.byTooltip('Restore source order'));
      await tester.pumpAndSettle();

      // Restored: ch1 is displayed before ch3 again.
      final ch1RestoredY = tester.getTopLeft(find.text('Chapter 1')).dy;
      final ch3RestoredY = tester.getTopLeft(find.text('Special Episode')).dy;
      expect(ch1RestoredY, lessThan(ch3RestoredY));
    });

    testWidgets('Filtering by query displays matching chapters only', (
      tester,
    ) async {
      final details = NovelDetails(
        metadata: MediaMetadata(title: 'Test Novel'),
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
        final details = NovelDetails(
          metadata: MediaMetadata(title: 'Test Novel'),
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
      'Empty chapter list displays "No readable chapters found." and disabled CTA',
      (tester) async {
        final details = NovelDetails(
          metadata: MediaMetadata(title: 'Test Novel'),
          chapterListOrder: ChapterListOrder.readingOrder,
          chapters: [],
        );

        await tester.pumpWidget(_buildTestPage(details: details));
        await tester.pumpAndSettle();

        expect(find.text('No readable chapters found.'), findsOneWidget);
        expect(find.byType(PrimaryReadingCta), findsOneWidget);
        expect(
          tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
          isNull,
        );
        expect(find.byType(ChapterControlBar), findsNothing);
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

        final details = NovelDetails(
          metadata: MediaMetadata(title: 'Test Novel'),
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
