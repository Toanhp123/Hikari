import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/home/home_page.dart';
import 'package:hikari/features/home/widgets/continue_shelf.dart';

void main() {
  testWidgets('Home bottom clearance follows inherited obstruction', (
    tester,
  ) async {
    for (final bottomPadding in [0.0, 120.0]) {
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: MediaQuery(
            data: MediaQueryData(
              padding: EdgeInsets.only(bottom: bottomPadding),
            ),
            child: HomePage(openMedia: (_, _) {}),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        tester
            .getSize(find.byKey(const ValueKey('home-bottom-clearance')))
            .height,
        bottomPadding + HikariSpacing.xl,
      );
    }
  });

  testWidgets('Catalog featured hero precedes resume shelf', (tester) async {
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
          library: _FakeLibraryRepository(const [media]),
          progressRepository: _ProgressRepository({
            media.source: MediaProgress(
              media: media.source,
              position: TextPosition(progression: .5),
              completed: false,
              updatedAt: DateTime.utc(2026),
            ),
          }),
          discoverCatalog: DiscoverCatalog(_CatalogProvider(catalogItem)),
          openCatalogDetail: (_, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      tester.getTopLeft(find.text('Featured')).dy,
      lessThan(tester.getTopLeft(find.byType(ContinueShelf)).dy),
    );
  });

  testWidgets('Home features spotlight hero before resume content', (
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
          progressRepository: _ProgressRepository({
            mangaMedia.source: MediaProgress(
              media: mangaMedia.source,
              position: PagePosition(pageIndex: 1049, pageCount: 1100),
              completed: false,
              updatedAt: DateTime.utc(2026),
            ),
          }),
          discoverCatalog: DiscoverCatalog(_CatalogProvider(catalogItem)),
          openCatalogDetail: (_, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Continue'), findsOneWidget);
    expect(find.text('Pick up where you left off'), findsOneWidget);
    expect(find.text('Page 1050 of 1100'), findsOneWidget);
    expect(find.text('Featured'), findsOneWidget);
    expect(find.text('Catalog show'), findsOneWidget);
    expect(find.text('Recently added'), findsOneWidget);

    expect(
      tester.getTopLeft(find.text('Featured')).dy,
      lessThan(tester.getTopLeft(find.byType(ContinueShelf)).dy),
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
      find.ancestor(
        of: find.text('One Piece'),
        matching: find.byType(MediaPoster),
      ),
    );
    await tester.pumpAndSettle();
    expect(selectedMedia, mangaMedia);
  });

  testWidgets('empty Library never renders the removed start-library state', (
    tester,
  ) async {
    final catalog = Completer<CatalogDiscovery>();
    final catalogItem = CatalogEntry(
      id: const CatalogEntryId(provider: 'test', value: 'loaded'),
      title: 'Loaded discovery',
      type: MediaType.anime,
    );

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: HomePage(
          openMedia: (_, _) {},
          library: _FakeLibraryRepository(const []),
          discoverCatalog: DiscoverCatalog(
            _DeferredCatalogProvider(catalog.future),
          ),
          openCatalogDetail: (_, _) {},
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Start your library'), findsNothing);
    expect(
      find.byKey(const ValueKey('catalog-loading-skeleton')),
      findsOneWidget,
    );

    catalog.complete(
      CatalogDiscovery(
        sections: {
          CatalogSection.featured: [catalogItem],
        },
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('catalog-loading-skeleton')),
      findsNothing,
    );
    expect(find.text('Loaded discovery'), findsOneWidget);
    expect(find.text('Start your library'), findsNothing);
  });

  testWidgets('Home hides refresh when no refreshable data source exists', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: HomePage(openMedia: (_, _) {}, onNavigateToSearch: () {}),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byTooltip('Refresh'), findsNothing);
    expect(find.byType(RefreshIndicator), findsNothing);
  });

  testWidgets('Home keeps Library visible when resume progress fails', (
    tester,
  ) async {
    const media = Media(
      title: 'Saved manga',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'saved'),
    );
    final progress = _FailingProgressRepository();

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: HomePage(
          openMedia: (_, _) {},
          library: _FakeLibraryRepository([media]),
          progressRepository: progress,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Some resume progress could not load. Your Library is still available.',
      ),
      findsOneWidget,
    );
    expect(find.text('Recently added'), findsOneWidget);
    expect(find.text('Saved manga'), findsOneWidget);

    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(progress.loadCount, 2);
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

  testWidgets('failed refresh preserves a known-empty Library snapshot', (
    tester,
  ) async {
    final library = _FailAfterFirstEmptyLibraryRepository();

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: HomePage(openMedia: (_, _) {}, library: library),
      ),
    );
    await tester.pumpAndSettle();

    final refresh = tester
        .state<RefreshIndicatorState>(find.byType(RefreshIndicator))
        .show();
    await tester.pump();
    await tester.pumpAndSettle();
    await refresh;

    expect(
      find.text('Library refresh failed. Showing the last available items.'),
      findsOneWidget,
    );
    expect(find.text('Could not load your Library'), findsNothing);
    expect(library.loadCount, 2);
  });

  testWidgets('Home pull refresh reloads all Home data', (tester) async {
    final library = _CountingLibraryRepository();
    final item = CatalogEntry(
      id: const CatalogEntryId(provider: 'test', value: 'refresh'),
      title: 'Refresh item',
      type: MediaType.anime,
    );
    final catalog = _CountingCatalogProvider(item);

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: HomePage(
          openMedia: (_, _) {},
          library: library,
          discoverCatalog: DiscoverCatalog(catalog),
          openCatalogDetail: (_, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(library.loadCount, 1);
    expect(catalog.discoverCount, 1);
    expect(find.byType(RefreshIndicator), findsOneWidget);

    expect(find.byTooltip('Refresh'), findsNothing);

    final pullRefresh = tester
        .state<RefreshIndicatorState>(find.byType(RefreshIndicator))
        .show();
    await tester.pump();
    await tester.pumpAndSettle();
    await pullRefresh;
    expect(library.loadCount, 2);
    expect(catalog.discoverCount, 2);
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

final class _FailAfterFirstEmptyLibraryRepository
    extends _FakeLibraryRepository {
  _FailAfterFirstEmptyLibraryRepository() : super(const []);

  int loadCount = 0;

  @override
  Future<List<LibraryEntry>> loadAll() async {
    if (++loadCount > 1) throw StateError('offline');
    return super.loadAll();
  }
}

final class _CountingLibraryRepository extends _FakeLibraryRepository {
  _CountingLibraryRepository() : super(const []);

  int loadCount = 0;

  @override
  Future<List<LibraryEntry>> loadAll() async {
    loadCount++;
    return super.loadAll();
  }
}

final class _CountingCatalogProvider extends _CatalogProvider {
  _CountingCatalogProvider(super.item);

  int discoverCount = 0;

  @override
  Future<CatalogDiscovery> discover() async {
    discoverCount++;
    return super.discover();
  }
}

final class _DeferredCatalogProvider implements CatalogProvider {
  _DeferredCatalogProvider(this.discovery);

  final Future<CatalogDiscovery> discovery;

  @override
  String get id => 'deferred-test';

  @override
  Future<CatalogDiscovery> discover() => discovery;

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async =>
      const [];

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) async => null;

  @override
  Future<void> close() async {}
}

final class _ProgressRepository implements ProgressRepository {
  _ProgressRepository(this.progressByMedia);

  final Map<SourceMediaRef, MediaProgress> progressByMedia;

  @override
  Future<MediaProgress?> load(SourceMediaRef media) async =>
      progressByMedia[media];

  @override
  Future<void> save(MediaProgress progress) async {}

  @override
  Future<void> delete(SourceMediaRef media) async {}
}

final class _FailingProgressRepository implements ProgressRepository {
  int loadCount = 0;

  @override
  Future<MediaProgress?> load(SourceMediaRef media) async {
    loadCount++;
    throw StateError('progress unavailable');
  }

  @override
  Future<void> save(MediaProgress progress) async {}

  @override
  Future<void> delete(SourceMediaRef media) async {}
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
