import 'dart:async';

import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:hikari/app/app.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/features/search/unified_search_page.dart';
import 'package:hikari/features/search/unified_search_view_model.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/catalog_workflows.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_pages.dart';

void main() {
  for (final type in MediaType.values) {
    testWidgets('Home detail opens title-seeded ${type.name} source search', (
      tester,
    ) async {
      final media = CatalogMedia(
        id: const CatalogMediaId(provider: 'test', value: '1'),
        title: 'Catalog title',
        type: type,
      );
      final db = UserDatabase(NativeDatabase.memory());
      final local = _LocalCatalog();
      final dependencies = AppDependencies.create(
        database: db,
        localMediaSource: local,
        catalogProvider: _Provider(
          onDiscover: () async => CatalogDiscovery(
            sections: {
              CatalogSection.featured: [media],
            },
          ),
          onDetails: (_) async => CatalogDetails(media: media),
        ),
      );
      addTearDown(() async {
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump(Duration.zero);
        await dependencies.dispose();
        await db.close();
      });
      await tester.pumpWidget(HikariApp(dependencies: dependencies));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Catalog title'));
      await tester.pumpAndSettle();
      expect(find.byType(CatalogDetailPage), findsOneWidget);
      expect(local.scans, 0);
      await tester.tap(find.text(type == MediaType.anime ? 'Watch' : 'Read'));
      await tester.pumpAndSettle();
      final page = tester.widget<UnifiedSearchPage>(
        find.byType(UnifiedSearchPage),
      );
      expect(page.initialQuery, 'Catalog title');
      expect(page.initialFilter, switch (type) {
        MediaType.anime => SearchMediaTypeFilter.anime,
        MediaType.manga => SearchMediaTypeFilter.manga,
        MediaType.lightNovel => SearchMediaTypeFilter.novel,
      });
      expect(
        tester.widget<TextField>(find.byType(TextField)).controller!.text,
        'Catalog title',
      );
      expect(local.scans, 1);
      expect(find.text('Catalog title ${type.name}'), findsOneWidget);
      for (final other in MediaType.values.where((value) => value != type)) {
        expect(find.text('Catalog title ${other.name}'), findsNothing);
      }
      expect(await dependencies.libraryRepository.loadAll(), isEmpty);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(Duration.zero);
    });
  }

  testWidgets('Home renders all six catalog sections', (tester) async {
    await tester.pumpWidget(
      _app(
        SingleChildScrollView(
          child: CatalogHomeSections(
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
            openDetails: (_) {},
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    for (final title in [
      'Featured this season',
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
        CatalogHomeSections(
          discover: DiscoverCatalog(provider),
          openDetails: (_) {},
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
    expect(find.text('Featured this season'), findsOneWidget);
    expect(find.text('Anime A'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(
      _app(
        CatalogHomeSections(
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
          openDetails: (_) {},
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
        CatalogHomeSections(
          discover: DiscoverCatalog(provider),
          openDetails: (_) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Catalog unavailable'), findsOneWidget);
    await tester.tap(find.text('Try Again'));
    await tester.pumpAndSettle();
    expect(find.text('No catalog results available.'), findsOneWidget);
  });

  testWidgets(
    'catalog detail renders normalized fields and routes relations/search',
    (tester) async {
      CatalogMedia? related;
      String? query;
      MediaType? filter;
      final detail = CatalogDetails(
        media: _anime,
        description: 'Description',
        warnings: ['Some fields unavailable'],
        relations: [
          CatalogRelationMedia(
            relation: CatalogRelation.sequel,
            media: _related,
          ),
        ],
      );
      await tester.pumpWidget(
        _app(
          CatalogDetailPage(
            initial: _anime,
            loadDetails: LoadCatalogDetails(
              _Provider(onDetails: (_) async => detail),
            ),
            openRelated: (media) => related = media,
            searchTitle: (title, type) {
              query = title;
              filter = type;
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Anime A'), findsWidgets);
      expect(find.text('Japanese title'), findsOneWidget);
      expect(find.text('Alternate'), findsOneWidget);
      expect(find.text('Action'), findsOneWidget);
      for (final value in [
        'Score 91',
        'Popularity 55',
        'Format: tv',
        'Status: finished',
        'summer 2025',
        '12 episodes',
        'Description',
        'Some fields unavailable',
        'Example Studio',
        'Writer',
      ]) {
        await tester.scrollUntilVisible(
          find.text(value),
          250,
          scrollable: find.byType(Scrollable).first,
        );
        expect(find.text(value), findsOneWidget, reason: value);
      }
      await tester.tap(find.text('Related series'));
      expect(related, _related);
      await tester.tap(find.text('Watch'));
      expect(query, 'Anime A');
      expect(filter, MediaType.anime);
    },
  );

  testWidgets('catalog detail not-found and failed states can retry', (
    tester,
  ) async {
    var calls = 0;
    final failedAttempt = Completer<CatalogDetails?>();
    final provider = _Provider(
      onDetails: (_) {
        if (++calls == 1) return Future.value(null);
        if (calls == 2) return failedAttempt.future;
        return Future.value(CatalogDetails(media: _anime));
      },
    );
    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initial: _anime,
          loadDetails: LoadCatalogDetails(provider),
          openRelated: (_) {},
          searchTitle: (_, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Catalog entry not found'), findsOneWidget);
    await tester.tap(find.text('Try Again'));
    await tester.pump(const Duration(milliseconds: 1));
    expect(calls, 2);
    failedAttempt.completeError(StateError('offline'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 1));
    await tester.pumpAndSettle();
    expect(find.text('Details unavailable'), findsOneWidget);
    await tester.tap(find.text('Try Again'));
    await tester.pumpAndSettle();
    expect(calls, 3);
    await tester.scrollUntilVisible(
      find.text('Watch'),
      250,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('Watch'), findsOneWidget);
  });

  testWidgets('manga detail exposes Read callback', (tester) async {
    String? title;
    MediaType? type;
    final manga = CatalogMedia(
      id: _related.id,
      title: 'Book A',
      type: MediaType.manga,
      alternateTitles: ['Translated title'],
    );
    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initial: manga,
          loadDetails: LoadCatalogDetails(
            _Provider(onDetails: (_) async => CatalogDetails(media: manga)),
          ),
          openRelated: (_) {},
          searchTitle: (value, mediaType) {
            title = value;
            type = mediaType;
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Read'));
    expect(title, 'Book A');
    expect(type, MediaType.manga);
  });
}

Widget _app(Widget child) =>
    MaterialApp(theme: HikariTheme.darkTheme(), home: child);

final _anime = CatalogMedia(
  id: const CatalogMediaId(provider: 'test', value: '1'),
  title: 'Anime A',
  type: MediaType.anime,
  coverUrl: 'https://example/cover',
  alternateTitles: ['Japanese title'],
  synonyms: ['Alternate'],
  genres: ['Action'],
  averageScore: 91,
  popularity: 55,
  format: CatalogFormat.tv,
  status: CatalogStatus.finished,
  season: CatalogSeason.summer,
  year: 2025,
  episodes: 12,
  studios: ['Example Studio'],
  staff: ['Writer'],
);
final _related = CatalogMedia(
  id: const CatalogMediaId(provider: 'test', value: '2'),
  title: 'Related series',
  type: MediaType.manga,
);

final class _LocalCatalog extends LocalMediaSource {
  int scans = 0;
  @override
  Future<List<Media>> scanSelectedRoot() async {
    scans++;
    return [
      for (final type in MediaType.values)
        Media(
          title: 'Catalog title ${type.name}',
          type: type,
          source: SourceMediaRef(sourceId: SourceId.local, itemId: type.name),
        ),
    ];
  }
}

final class _Provider implements CatalogProvider {
  _Provider({
    Future<CatalogDiscovery> Function()? onDiscover,
    Future<CatalogDetails?> Function(CatalogMediaId)? onDetails,
  }) : _onDiscover =
           onDiscover ?? (() async => CatalogDiscovery(sections: const {})),
       _onDetails = onDetails ?? ((_) async => null);
  final Future<CatalogDiscovery> Function() _onDiscover;
  final Future<CatalogDetails?> Function(CatalogMediaId) _onDetails;
  @override
  String get id => 'test';
  @override
  Set<CatalogCapability> get capabilities => const {
    CatalogCapability.discovery,
    CatalogCapability.details,
  };
  @override
  Future<CatalogDiscovery> discover() => _onDiscover();
  @override
  Future<CatalogDetails?> details(CatalogMediaId id) => _onDetails(id);
  @override
  Future<void> close() async {}
}
