import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/home/home_page.dart';
import 'package:hikari/features/home/widgets/continue_shelf.dart';
import 'package:hikari/features/home/widgets/hero_carousel.dart';

void main() {
  testWidgets('HomePage renders HeroCarousel, ContinueShelf, and filters', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1200, 1600);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    const animeMedia = Media(
      title: 'Chainsaw Man',
      type: MediaType.anime,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'csm'),
    );
    const mangaMedia = Media(
      title: 'One Piece',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'op'),
    );

    Media? selectedMedia;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: HomePage(
          openMedia: (_, item) => selectedMedia = item,
          featuredItems: const [
            FeaturedHeroItem(
              media: animeMedia,
              tagline: 'Action, Supernatural',
              genres: ['Action', 'Demons'],
            ),
          ],
          continueItems: const [
            ContinueReadingItem(
              media: mangaMedia,
              progress: 0.5,
              progressLabel: 'Chapter 1050',
            ),
          ],
          trendingItems: const [animeMedia, mangaMedia],
        ),
      ),
    );

    expect(find.text('Chainsaw Man'), findsWidgets);
    expect(find.text('Continue Watching & Reading'), findsOneWidget);
    expect(find.text('Chapter 1050'), findsOneWidget);
    expect(find.text('Discover'), findsOneWidget);

    // Tap Watch Now in Hero
    await tester.tap(find.text('Watch Now'));
    await tester.pumpAndSettle();
    expect(selectedMedia, animeMedia);

    // Filter by Manga
    await tester.tap(find.text('Manga'));
    await tester.pumpAndSettle();
    expect(find.text('One Piece'), findsWidgets);
  });

  testWidgets('HomePage never invents showcase media for empty inputs', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: HomePage(openMedia: (_, _) {}),
      ),
    );

    expect(find.text('Attack on Titan'), findsNothing);
    expect(find.text('Demon Slayer'), findsNothing);
    expect(find.textContaining('Add media to your Library'), findsOneWidget);
  });
}
