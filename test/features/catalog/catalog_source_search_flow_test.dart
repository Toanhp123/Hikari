import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/app/app.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_detail_page.dart';
import 'package:hikari/features/source_search/source_search_page.dart';
import 'package:hikari/features/source_search/source_search_view_model.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';

void main() {
  for (final type in MediaType.values) {
    testWidgets('Home detail opens title-seeded ${type.name} source search', (
      tester,
    ) async {
      final entry = CatalogEntry(
        id: const CatalogEntryId(provider: 'test', value: '1'),
        title: 'Catalog title',
        type: type,
      );
      final database = UserDatabase(NativeDatabase.memory());
      final local = _LocalCatalog();
      final dependencies = AppDependencies.create(
        database: database,
        localMediaSource: local,
        catalogProvider: _Provider(
          onDiscover: () async => CatalogDiscovery(
            sections: {
              CatalogSection.featured: [entry],
            },
          ),
          onLoadDetails: (_) async => CatalogEntryDetails(entry: entry),
        ),
      );
      addTearDown(() async {
        await dependencies.dispose();
        await database.close();
      });

      try {
        await tester.pumpWidget(HikariApp(dependencies: dependencies));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Catalog title'));
        await tester.pumpAndSettle();

        expect(find.byType(CatalogDetailPage), findsOneWidget);
        expect(local.scans, 0);

        await tester.tap(find.text(type == MediaType.anime ? 'Watch' : 'Read'));
        await tester.pumpAndSettle();

        final page = tester.widget<SourceSearchPage>(
          find.byType(SourceSearchPage),
        );
        expect(page.initialQuery, 'Catalog title');
        expect(page.initialFilter, switch (type) {
          MediaType.anime => SourceSearchFilter.anime,
          MediaType.manga => SourceSearchFilter.manga,
          MediaType.lightNovel => SourceSearchFilter.novel,
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
      } finally {
        // Drift closes watched queries on a zero-duration timer. Unmount and
        // pump that timer before testWidgets verifies there are no timers left.
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump(Duration.zero);
      }
    });
  }
}

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
