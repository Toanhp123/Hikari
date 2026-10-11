import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/remote_manga/manga_series_view_model.dart';
import 'package:hikari/features/remote_manga/widgets/manga_series_content.dart';
import 'package:hikari/features/remote_novel/novel_series_view_model.dart';
import 'package:hikari/features/remote_novel/widgets/novel_series_content.dart';

void main() {
  for (final manga in [true, false]) {
    for (final width in [320.0, 360.0, 393.0, 1200.0]) {
      testWidgets(
        '${manga ? 'manga' : 'novel'} real 2x text at $width retains lazy header',
        (tester) async {
          tester.view.physicalSize = Size(width, 900);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          const cover = SourceMediaRef(
            sourceId: SourceId('fake'),
            itemId: 'cover',
          );
          final metadata = MediaMetadata(
            title: 'Long title ' * 12,
            cover: cover,
            authors: ['Long author ' * 8],
            publisher: 'Long publisher ' * 8,
            summary: 'Long description with readable words. ' * 8,
            rating: 9.9999,
            ratingMax: 10,
            genres: ['Long genre ' * 8],
          );
          SourceMediaRef ref(int i) =>
              SourceMediaRef(sourceId: const SourceId('fake'), itemId: '$i');
          final mangaModel = MangaSeriesViewModel(
            () async => MangaSeriesDetails(
              metadata: metadata,
              chapterListOrder: ChapterListOrder.readingOrder,
              chapters: List.generate(
                1000,
                (i) => MangaChapter(
                  title: 'Long chapter title $i ' * 5,
                  source: ref(i),
                  scanlator: 'Long scanlator ' * 6,
                ),
              ),
            ),
          );
          final novelModel = NovelSeriesViewModel(
            () async => NovelDetails(
              metadata: metadata,
              chapterListOrder: ChapterListOrder.readingOrder,
              chapters: List.generate(
                1000,
                (i) => NovelChapter(
                  title: 'Long chapter title $i ' * 5,
                  source: ref(i),
                  scanlators: ['Long scanlator ' * 6],
                ),
              ),
            ),
          );
          addTearDown(mangaModel.dispose);
          addTearDown(novelModel.dispose);
          await mangaModel.load();
          await novelModel.load();
          var artworkReads = 0;
          await tester.pumpWidget(
            MaterialApp(
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(context)
                    .copyWith(textScaler: const TextScaler.linear(2)),
                child: child!,
              ),
              home: Scaffold(
                body: ListenableBuilder(
                  listenable: manga ? mangaModel : novelModel,
                  builder: (context, _) => manga
                      ? MangaSeriesContent(
                          viewModel: mangaModel,
                          sourceName: 'Long source name ' * 4,
                          openingChapter: false,
                          onRefresh: mangaModel.load,
                          onOpenChapter: (_) {},
                          readArtwork: (_) async {
                            artworkReads++;
                            return null;
                          },
                        )
                      : NovelSeriesContent(
                          viewModel: novelModel,
                          sourceName: 'Long source name ' * 4,
                          openingChapter: false,
                          onRefresh: novelModel.load,
                          onOpenChapter: (_) {},
                          readArtwork: (_) async {
                            artworkReads++;
                            return null;
                          },
                        ),
                ),
              ),
            ),
          );
          await tester.pumpAndSettle();
          expect(
            MediaQuery.textScalerOf(tester.element(find.byType(ListView)))
                .scale(10),
            20,
          );
          expect(tester.takeException(), isNull);
          await tester.ensureVisible(find.text('Show more'));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Show more'));
          await tester.pumpAndSettle();
          final scroll = tester.state<ScrollableState>(find.byType(Scrollable));
          scroll.position.jumpTo(30000);
          await tester.pumpAndSettle();
          expect(
            tester.widgetList(find.byType(ListTile)).length,
            lessThan(100),
          );
          expect(tester.takeException(), isNull);
          scroll.position.jumpTo(0);
          await tester.pumpAndSettle();
          expect(find.text('Show less'), findsOneWidget);
          expect(artworkReads, 1);
          await tester.ensureVisible(find.byTooltip('Search chapters'));
          await tester.pumpAndSettle();
          await tester.tap(find.byTooltip('Search chapters'));
          await tester.pumpAndSettle();
          await tester.enterText(find.byType(TextField), 'no match');
          await tester.pumpAndSettle();
          expect(find.text('0 of 1000 chapters'), findsOneWidget);
          expect(find.text('No chapters matching "no match"'), findsOneWidget);
          expect(tester.takeException(), isNull);
        },
      );
    }
  }
}
