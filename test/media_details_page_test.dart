import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/media_details/media_details_page.dart';

void main() {
  testWidgets('MediaDetailsPage renders metadata, synopsis, and chapters', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1200, 1600);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    const media = Media(
      title: 'Steins;Gate',
      type: MediaType.anime,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'sg'),
    );

    Media? openedMedia;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: MediaDetailsPage(
          media: media,
          openMedia: (_, item) => openedMedia = item,
          author: 'White Fox / 5pb.',
          year: '2011',
          rating: 4.9,
          synopsis: 'A self-proclaimed mad scientist accidentally invents time travel through a microwave.',
          chapters: const [
            MediaChapterItem(
              id: 'ep-1',
              title: 'Episode 1: Turning Point',
              duration: '24m',
            ),
          ],
        ),
      ),
    );

    expect(find.text('Steins;Gate'), findsWidgets);
    expect(find.text('White Fox / 5pb.'), findsOneWidget);
    expect(find.text('4.9'), findsOneWidget);
    expect(find.text('Episode 1: Turning Point'), findsOneWidget);

    // Tap Start Watching
    await tester.tap(find.text('Start Watching'));
    await tester.pumpAndSettle();
    expect(openedMedia, media);
  });
}
