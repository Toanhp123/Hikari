import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/home/home_page.dart';
import 'package:hikari/features/home/widgets/continue_shelf.dart';
import 'package:hikari/features/home/widgets/hero_carousel.dart';

void main() {
  testWidgets('Home prioritizes resume content before featured discovery', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1200, 1600);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

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

    expect(find.text('Continue'), findsOneWidget);
    expect(find.text('Pick up where you left off'), findsOneWidget);
    expect(find.text('Chapter 1050'), findsOneWidget);
    expect(find.text('Discover'), findsOneWidget);
    expect(find.text('Light Novels'), findsNothing);
    expect(find.text('My List'), findsNothing);

    expect(
      tester.getTopLeft(find.byType(ContinueShelf)).dy,
      lessThan(tester.getTopLeft(find.byType(HeroCarousel)).dy),
    );

    await tester.tap(find.text('Watch Now'));
    await tester.pumpAndSettle();
    expect(selectedMedia, animeMedia);

    await tester.tap(find.text('Manga'));
    await tester.pumpAndSettle();
    final posterTitles = tester
        .widgetList<MediaPoster>(find.byType(MediaPoster))
        .map((poster) => poster.title)
        .toList();
    expect(posterTitles, ['One Piece']);
  });

  testWidgets('empty Home offers real discovery and local actions', (
    tester,
  ) async {
    var searchCount = 0;
    var localCount = 0;
    var mangaCount = 0;
    var novelCount = 0;

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: HomePage(
          openMedia: (_, _) {},
          onNavigateToSearch: () => searchCount++,
          showLocalMediaEntry: true,
          onNavigateToLocal: () => localCount++,
          openRemoteManga: () => mangaCount++,
          openRemoteNovels: () => novelCount++,
        ),
      ),
    );

    expect(find.text('Start your library'), findsOneWidget);
    expect(find.text('Open Local'), findsOneWidget);
    expect(find.text('Search'), findsOneWidget);
    expect(find.text('Browse manga'), findsOneWidget);
    expect(find.text('Browse novels'), findsOneWidget);
    expect(find.text('Attack on Titan'), findsNothing);

    await tester.tap(find.text('Search'));
    await tester.tap(find.text('Open Local'));
    await tester.tap(find.text('Browse manga'));
    await tester.tap(find.text('Browse novels'));

    expect(searchCount, 1);
    expect(localCount, 1);
    expect(mangaCount, 1);
    expect(novelCount, 1);
  });

  testWidgets('Home exposes retry when Library loading fails', (tester) async {
    final repository = _FailingLibraryRepository();

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: HomePage(
          openMedia: (_, _) {},
          library: repository,
          onNavigateToSearch: () {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Could not load your Library'), findsOneWidget);
    expect(find.text('Try Again'), findsOneWidget);
    expect(repository.loadCount, 1);

    await tester.tap(find.text('Try Again'));
    await tester.pumpAndSettle();
    expect(repository.loadCount, 2);
    expect(find.text('Could not load your Library'), findsOneWidget);
  });

  testWidgets('wide Home bounds poster content instead of stretching it', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1800, 1200);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    const media = Media(
      title: 'Wide Layout',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'wide'),
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: HomePage(openMedia: (_, _) {}, trendingItems: const [media]),
      ),
    );

    final posterLeft = tester.getTopLeft(find.byType(MediaPoster)).dx;
    expect(posterLeft, greaterThanOrEqualTo(190));
  });
}

final class _FailingLibraryRepository implements LibraryRepository {
  int loadCount = 0;

  @override
  Future<List<LibraryEntry>> loadAll() async {
    loadCount++;
    throw StateError('database unavailable');
  }

  @override
  Future<bool> contains(SourceMediaRef media) async => false;

  @override
  Future<void> remove(SourceMediaRef media) async {}

  @override
  Future<void> upsert(LibraryEntry entry) async {}
}
