import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/home/home_page.dart';
import 'package:hikari/features/home/widgets/continue_shelf.dart';

void main() {
  testWidgets('Continue shelf precedes catalog discovery', (tester) async {
    tester.view.physicalSize = const Size(900, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    const media = Media(
      title: 'Resume first',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'resume'),
    );
    final catalogItem = CatalogEntry(
      id: const CatalogEntryId(provider: 'test', value: '1'),
      title: 'Catalog later',
      type: MediaType.anime,
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: HomePage(
          openMedia: (_, _) {},
          continueItems: const [
            ContinueReadingItem(
              media: media,
              progress: .5,
              progressLabel: '50% read',
            ),
          ],
          discoverCatalog: DiscoverCatalog(_CatalogProvider(catalogItem)),
          openCatalogDetail: (_, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      tester.getTopLeft(find.byType(ContinueShelf)).dy,
      lessThan(tester.getTopLeft(find.text('Featured')).dy),
    );
  });

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
    final catalogItem = CatalogEntry(
      id: const CatalogEntryId(provider: 'test', value: '1'),
      title: 'Catalog show',
      type: MediaType.anime,
    );

    Media? selectedMedia;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: HomePage(
          openMedia: (_, item) => selectedMedia = item,
          library: _FakeLibraryRepository([animeMedia, mangaMedia]),
          continueItems: const [
            ContinueReadingItem(
              media: mangaMedia,
              progress: 0.5,
              progressLabel: 'Chapter 1050',
            ),
          ],
          discoverCatalog: DiscoverCatalog(_CatalogProvider(catalogItem)),
          openCatalogDetail: (_, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Continue'), findsOneWidget);
    expect(find.text('Pick up where you left off'), findsOneWidget);
    expect(find.text('Chapter 1050'), findsOneWidget);
    expect(find.text('Featured'), findsOneWidget);
    expect(find.text('Catalog show'), findsOneWidget);
    expect(find.text('Recently added'), findsOneWidget);

    expect(
      tester.getTopLeft(find.byType(ContinueShelf)).dy,
      lessThan(tester.getTopLeft(find.text('Featured')).dy),
    );

    await tester.tap(find.text('Manga'));
    await tester.pumpAndSettle();
    final posterTitles = tester
        .widgetList<MediaPoster>(find.byType(MediaPoster))
        .map((poster) => poster.title)
        .toList();
    expect(posterTitles, contains('One Piece'));
    expect(posterTitles, isNot(contains('Chainsaw Man')));

    await tester.tap(
      find.descendant(
        of: find.byType(MediaPoster),
        matching: find.text('One Piece'),
      ),
    );
    await tester.pumpAndSettle();
    expect(selectedMedia, mangaMedia);
  });

  testWidgets('empty Home offers real discovery actions', (tester) async {
    var searchCount = 0;
    var mangaCount = 0;
    var novelCount = 0;

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: HomePage(
          openMedia: (_, _) {},
          onNavigateToSearch: () => searchCount++,
          openRemoteManga: () => mangaCount++,
          openRemoteNovels: () => novelCount++,
        ),
      ),
    );

    expect(find.text('Start your library'), findsOneWidget);
    expect(find.text('Open Local'), findsNothing);
    expect(find.text('Local media'), findsNothing);
    expect(find.text('Search'), findsOneWidget);
    expect(find.text('Browse manga'), findsOneWidget);
    expect(find.text('Browse novels'), findsOneWidget);
    expect(find.text('Attack on Titan'), findsNothing);

    await tester.tap(find.text('Search'));
    await tester.tap(find.text('Browse manga'));
    await tester.tap(find.text('Browse novels'));

    expect(searchCount, 1);
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
        home: HomePage(
          openMedia: (_, _) {},
          library: _FakeLibraryRepository([media]),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final posterLeft = tester.getTopLeft(find.byType(MediaPoster)).dx;
    expect(posterLeft, greaterThanOrEqualTo(190));
  });
}

final class _FakeLibraryRepository implements LibraryRepository {
  _FakeLibraryRepository(this.media);
  final List<Media> media;

  @override
  Future<List<LibraryEntry>> loadAll() async => [
    for (final item in media)
      LibraryEntry(media: item, addedAt: DateTime.utc(2026)),
  ];

  @override
  Future<bool> contains(SourceMediaRef media) async => false;

  @override
  Future<void> remove(SourceMediaRef media) async {}

  @override
  Future<void> upsert(LibraryEntry entry) async {}
}

final class _CatalogProvider implements CatalogProvider {
  _CatalogProvider(this.item);
  final CatalogEntry item;
  @override
  String get id => 'test';
  @override
  Future<CatalogDiscovery> discover() async => CatalogDiscovery(
    sections: {
      CatalogSection.featured: [item],
    },
  );
  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async =>
      const [];

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) async => null;
  @override
  Future<void> close() async {}
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
