import 'dart:async';

import 'package:flutter/material.dart';

import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/home/widgets/catalog_discovery_sections.dart';
import 'package:hikari/features/home/widgets/hero_carousel.dart';

void main() {
  testWidgets('Home renders all six catalog sections', (tester) async {
    await tester.pumpWidget(
      _app(
        SingleChildScrollView(
          child: CatalogDiscoverySections(
            discover: DiscoverCatalog(
              _Provider(
                onDiscover: () async => CatalogDiscovery(
                  sections: {
                    for (final section in CatalogSection.values)
                      section: [_anime],
                  },
                ),
              ),
            ),
            openDetail: (_) {},
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    for (final title in [
      'Featured',
      'Trending',
      'Popular Anime',
      'Popular Manga',
      'Popular Light Novels',
      'Seasonal Anime',
    ]) {
      expect(find.text(title), findsOneWidget);
    }
  });

  testWidgets('Featured uses the catalog hero and opens catalog detail', (
    tester,
  ) async {
    final featured = CatalogEntry(
      id: const CatalogEntryId(provider: 'test', value: 'featured'),
      title: 'Featured story',
      type: MediaType.lightNovel,
      bannerUrl: 'https://example/banner',
      genres: const ['Fantasy', 'Adventure'],
    );
    final trending = CatalogEntry(
      id: const CatalogEntryId(provider: 'test', value: 'trending'),
      title: 'Trending story',
      type: MediaType.manga,
    );
    CatalogEntry? opened;

    await tester.pumpWidget(
      _app(
        SingleChildScrollView(
          child: CatalogDiscoverySections(
            discover: DiscoverCatalog(
              _Provider(
                onDiscover: () async => CatalogDiscovery(
                  sections: {
                    CatalogSection.featured: [featured],
                    CatalogSection.trending: [trending],
                  },
                ),
              ),
            ),
            openDetail: (entry) => opened = entry,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(HeroCarousel), findsOneWidget);
    expect(find.text('Featured'), findsOneWidget);
    expect(find.text('Featured story'), findsOneWidget);
    expect(find.text('View details'), findsOneWidget);
    expect(find.text('Watch Now'), findsNothing);
    expect(find.text('Read Now'), findsNothing);

    final posterTitles = tester
        .widgetList<MediaPoster>(find.byType(MediaPoster))
        .map((poster) => poster.title);
    expect(posterTitles, contains('Trending story'));
    expect(posterTitles, isNot(contains('Featured story')));

    await tester.tap(find.text('View details'));
    expect(opened, same(featured));
  });

  testWidgets('Catalog shelves use compact media labels and poster sizing', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final novel = CatalogEntry(
      id: const CatalogEntryId(provider: 'test', value: 'novel'),
      title: 'Novel story',
      type: MediaType.lightNovel,
    );

    await tester.pumpWidget(
      _app(
        CatalogDiscoverySections(
          discover: DiscoverCatalog(
            _Provider(
              onDiscover: () async => CatalogDiscovery(
                sections: {
                  CatalogSection.popularLightNovels: [novel],
                },
              ),
            ),
          ),
          openDetail: (_) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('NOVEL'), findsOneWidget);
    expect(find.text('LIGHTNOVEL'), findsNothing);
    expect(tester.getSize(find.byType(MediaPoster)).width, 132);
  });

  testWidgets('Home discovery loads, shows partial warning and retries', (
    tester,
  ) async {
    final first = Completer<CatalogDiscovery>();
    var calls = 0;
    final provider = _Provider(
      onDiscover: () => ++calls == 1
          ? first.future
          : Future.value(
              CatalogDiscovery(
                sections: {
                  CatalogSection.featured: [_anime],
                },
                warnings: ['Partial result'],
              ),
            ),
    );
    await tester.pumpWidget(
      _app(
        SingleChildScrollView(
          child: CatalogDiscoverySections(
            discover: DiscoverCatalog(provider),
            openDetail: (_) {},
          ),
        ),
      ),
    );
    expect(
      find.byKey(const ValueKey('catalog-loading-skeleton')),
      findsOneWidget,
    );
    expect(find.byType(MediaPoster), findsNWidgets(8));
    first.complete(
      CatalogDiscovery(
        sections: {
          CatalogSection.featured: [_anime],
        },
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('catalog-loading-skeleton')),
      findsNothing,
    );
    expect(find.text('Featured'), findsOneWidget);
    expect(find.text('Anime A'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(
      _app(
        SingleChildScrollView(
          child: CatalogDiscoverySections(
            discover: DiscoverCatalog(
              _Provider(
                onDiscover: () async => CatalogDiscovery(
                  sections: {
                    CatalogSection.trending: [_anime],
                  },
                  warnings: ['One section failed'],
                ),
              ),
            ),
            openDetail: (_) {},
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('One section failed'), findsOneWidget);
    expect(find.text('Anime A'), findsOneWidget);
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('Anime A'), findsOneWidget);
  });

  testWidgets('Home discovery shows empty and retries request errors', (
    tester,
  ) async {
    var calls = 0;
    final provider = _Provider(
      onDiscover: () async {
        if (++calls == 1) throw StateError('offline');
        return CatalogDiscovery(
          sections: {
            for (final section in CatalogSection.values) section: const [],
          },
        );
      },
    );
    await tester.pumpWidget(
      _app(
        SingleChildScrollView(
          child: CatalogDiscoverySections(
            discover: DiscoverCatalog(provider),
            openDetail: (_) {},
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Catalog unavailable'), findsOneWidget);
    await tester.tap(find.text('Try Again'));
    await tester.pumpAndSettle();
    expect(find.text('No catalog results available.'), findsOneWidget);
  });
}

Widget _app(Widget child) =>
    MaterialApp(theme: HikariTheme.darkTheme(), home: child);

final _anime = CatalogEntry(
  id: const CatalogEntryId(provider: 'test', value: '1'),
  title: 'Anime A',
  type: MediaType.anime,
  coverUrl: 'https://example/cover',
);

final class _Provider implements CatalogProvider {
  _Provider({
    Future<CatalogDiscovery> Function()? onDiscover,
    Future<CatalogEntryDetails?> Function(CatalogEntryId)? onLoadDetails,
  }) : _onDiscover =
           onDiscover ?? (() async => CatalogDiscovery(sections: const {})),
       _onLoadDetails = onLoadDetails ?? ((_) async => null);

  final Future<CatalogDiscovery> Function() _onDiscover;
  final Future<CatalogEntryDetails?> Function(CatalogEntryId) _onLoadDetails;

  @override
  String get id => 'test';

  @override
  Future<CatalogDiscovery> discover() => _onDiscover();

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async =>
      const [];

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) =>
      _onLoadDetails(id);

  @override
  Future<void> close() async {}
}
