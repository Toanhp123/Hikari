import 'dart:async';

import 'package:flutter/material.dart';

import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/home/widgets/catalog_discovery_sections.dart';

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
        CatalogDiscoverySections(
          discover: DiscoverCatalog(provider),
          openDetail: (_) {},
        ),
      ),
    );
    expect(find.byType(LinearProgressIndicator), findsOneWidget);
    first.complete(
      CatalogDiscovery(
        sections: {
          CatalogSection.featured: [_anime],
        },
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Featured'), findsOneWidget);
    expect(find.text('Anime A'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(
      _app(
        CatalogDiscoverySections(
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
        CatalogDiscoverySections(
          discover: DiscoverCatalog(provider),
          openDetail: (_) {},
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
