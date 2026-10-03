import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/app/app.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/features/catalog/catalog_detail_page.dart';
import 'package:hikari/features/remote_manga/manga_series_page.dart';
import 'package:hikari/features/remote_novel/novel_series_page.dart';
import 'package:hikari/features/source_search/source_search_page.dart';
import 'package:hikari/features/source_search/source_search_view_model.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';

void main() {
  for (final partialFailure in [false, true]) {
    testWidgets(
      'filtered global search fixes scope and allowlist; partial failure $partialFailure',
      (tester) async {
        final database = UserDatabase(NativeDatabase.memory());
        final local = _LocalCatalog();
        final english = _MangaCatalogSource(empty: partialFailure);
        final french = _MangaCatalogSource(
          id: const SourceId('test:fr'),
          languageCode: 'fr',
        );
        final failed = _MangaCatalogSource(
          id: const SourceId('test:failed'),
          fail: true,
        );
        final dependencies = _dependencies(
          database: database,
          local: local,
          entry: _entry(MediaType.manga),
          additionalSources: [english, french, if (partialFailure) failed],
        );
        addTearDown(() async {
          await dependencies.dispose();
          await database.close();
        });
        try {
          await tester.pumpWidget(HikariApp(dependencies: dependencies));
          await tester.pumpAndSettle();
          await tester.tap(find.text('View details'));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Read'));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Language: All'));
          await tester.pumpAndSettle();
          await tester.tap(find.widgetWithText(MenuItemButton, 'EN'));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Search EN sources'));
          await tester.pumpAndSettle();
          final page = tester.widget<SourceSearchPage>(
            find.byType(SourceSearchPage),
          );
          expect(page.fixedMediaType, MediaType.manga);
          expect(page.catalogScopeLabel, 'Manga · EN');
          expect(page.sourceIds, {english.id, if (partialFailure) failed.id});
          expect(find.text('Searching in Manga · EN'), findsOneWidget);
          expect(find.byType(ChoiceChip), findsNothing);
          expect(local.scans, 0);
          expect(english.searches, 1);
          expect(french.searches, 0);
          if (partialFailure) {
            expect(failed.searches, 1);
            expect(find.text('No results found'), findsOneWidget);
            expect(
              find.text('Some configured sources could not be searched.'),
              findsOneWidget,
            );
            expect(
              find.text('All search sources in Manga · EN failed. Try again.'),
              findsNothing,
            );
          } else {
            expect(find.text('Manga source · EN'), findsOneWidget);
            expect(find.text('Manga source'), findsNothing);
            expect(find.text('Manga source [en]'), findsNothing);
          }
        } finally {
          await _unmount(tester);
        }
      },
    );
  }

  for (final type in [MediaType.manga, MediaType.lightNovel]) {
    testWidgets(
      '$type all-source search fixes media type and keeps local scan',
      (tester) async {
        final database = UserDatabase(NativeDatabase.memory());
        final local = _LocalCatalog();
        final dependencies = _dependencies(
          database: database,
          local: local,
          entry: _entry(type),
        );
        addTearDown(() async {
          await dependencies.dispose();
          await database.close();
        });
        try {
          await tester.pumpWidget(HikariApp(dependencies: dependencies));
          await tester.pumpAndSettle();
          await tester.tap(find.text('View details'));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Read'));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Search all sources'));
          await tester.pumpAndSettle();
          final page = tester.widget<SourceSearchPage>(
            find.byType(SourceSearchPage),
          );
          expect(page.fixedMediaType, type);
          expect(
            page.catalogScopeLabel,
            '${type == MediaType.manga ? 'Manga' : 'Novel'} · All languages',
          );
          expect(page.sourceIds, isNull);
          expect(find.byType(ChoiceChip), findsNothing);
          expect(local.scans, 1);
          expect(find.text('Catalog title ${type.name}'), findsOneWidget);
        } finally {
          await _unmount(tester);
        }
      },
    );
  }

  testWidgets('anime Watch keeps title-seeded source search', (tester) async {
    final entry = _entry(MediaType.anime);
    final database = UserDatabase(NativeDatabase.memory());
    final local = _LocalCatalog();
    final dependencies = _dependencies(
      database: database,
      local: local,
      entry: entry,
    );
    addTearDown(() async {
      await dependencies.dispose();
      await database.close();
    });

    try {
      await tester.pumpWidget(HikariApp(dependencies: dependencies));
      await tester.pumpAndSettle();
      await tester.tap(find.text('View details'));
      await tester.pumpAndSettle();

      expect(find.byType(CatalogDetailPage), findsOneWidget);
      expect(local.scans, 0);

      await tester.tap(find.text('Watch'));
      await tester.pumpAndSettle();

      final page = tester.widget<SourceSearchPage>(
        find.byType(SourceSearchPage),
      );
      expect(page.initialQuery, 'Catalog title');
      expect(page.initialFilter, SourceSearchFilter.anime);
      expect(page.fixedMediaType, MediaType.anime);
      expect(page.catalogScopeLabel, 'Anime · All languages');
      expect(find.byType(ChoiceChip), findsNothing);
      expect(page.initialSourceId, isNull);
      expect(local.scans, 1);
      expect(find.text('Catalog title anime'), findsOneWidget);
    } finally {
      await _unmount(tester);
    }
  });

  testWidgets('manga Read chooses a source then opens its series directly', (
    tester,
  ) async {
    final entry = _entry(MediaType.manga);
    final database = UserDatabase(NativeDatabase.memory());
    final local = _LocalCatalog();
    final source = _MangaCatalogSource();
    final dependencies = _dependencies(
      database: database,
      local: local,
      entry: entry,
      additionalSources: [source],
    );
    addTearDown(() async {
      await dependencies.dispose();
      await database.close();
    });

    try {
      await tester.pumpWidget(HikariApp(dependencies: dependencies));
      await tester.pumpAndSettle();
      await tester.tap(find.text('View details'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Read'));
      await tester.pumpAndSettle();

      expect(find.text('Read from'), findsOneWidget);
      expect(find.text(source.displayName), findsOneWidget);
      expect(find.byType(SourceSearchPage), findsNothing);
      expect(local.scans, 0);

      await tester.tap(find.text(source.displayName));
      await tester.pumpAndSettle();

      expect(find.byType(MangaSeriesPage), findsOneWidget);
      expect(find.byType(SourceSearchPage), findsNothing);
      expect(source.searches, 1);
      expect(local.scans, 0);
    } finally {
      await _unmount(tester);
    }
  });

  testWidgets('manual correction stays scoped to the selected manga source', (
    tester,
  ) async {
    final entry = _entry(MediaType.manga);
    final database = UserDatabase(NativeDatabase.memory());
    final local = _LocalCatalog();
    final source = _MangaCatalogSource(resultTitle: 'Catalog title side story');
    final dependencies = _dependencies(
      database: database,
      local: local,
      entry: entry,
      additionalSources: [source],
    );
    addTearDown(() async {
      await dependencies.dispose();
      await database.close();
    });

    try {
      await tester.pumpWidget(HikariApp(dependencies: dependencies));
      await tester.pumpAndSettle();
      await tester.tap(find.text('View details'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Read'));
      await tester.pumpAndSettle();
      await tester.tap(find.text(source.displayName));
      await tester.pumpAndSettle();

      expect(find.text('Is this the right title?'), findsOneWidget);
      await tester.tap(find.text('Search manually in Manga source · EN'));
      await tester.pumpAndSettle();

      final page = tester.widget<SourceSearchPage>(
        find.byType(SourceSearchPage),
      );
      expect(page.initialSourceId, source.id);
      expect(page.sourceName, source.displayName);
      expect(page.fixedMediaType, MediaType.manga);
      expect(page.catalogScopeLabel, 'Manga source · EN');
      expect(find.byType(ChoiceChip), findsNothing);
      expect(find.text(source.name), findsNothing);
      expect(page.initialFilter, SourceSearchFilter.manga);
      expect(local.scans, 0);
      expect(find.text('Searching in Manga source · EN'), findsOneWidget);
    } finally {
      await _unmount(tester);
    }
  });

  testWidgets('novel Read chooses a source then opens its series directly', (
    tester,
  ) async {
    final entry = _entry(MediaType.lightNovel);
    final database = UserDatabase(NativeDatabase.memory());
    final local = _LocalCatalog();
    final source = _NovelCatalogSource();
    final dependencies = _dependencies(
      database: database,
      local: local,
      entry: entry,
      additionalSources: [source],
    );
    addTearDown(() async {
      await dependencies.dispose();
      await database.close();
    });

    try {
      await tester.pumpWidget(HikariApp(dependencies: dependencies));
      await tester.pumpAndSettle();
      await tester.tap(find.text('View details'));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Read'));
      await tester.pumpAndSettle();

      expect(find.text('Read from'), findsOneWidget);
      expect(find.text(source.name), findsOneWidget);
      expect(find.byType(SourceSearchPage), findsNothing);
      expect(local.scans, 0);

      await tester.tap(find.text(source.name));
      await tester.pumpAndSettle();

      expect(find.byType(NovelSeriesPage), findsOneWidget);
      expect(find.byType(SourceSearchPage), findsNothing);
      expect(source.searches, 1);
      expect(local.scans, 0);
    } finally {
      await _unmount(tester);
    }
  });
}

CatalogEntry _entry(MediaType type) => CatalogEntry(
  id: const CatalogEntryId(provider: 'test', value: '1'),
  title: 'Catalog title',
  type: type,
);

AppDependencies _dependencies({
  required UserDatabase database,
  required _LocalCatalog local,
  required CatalogEntry entry,
  Iterable<MediaSource> additionalSources = const [],
}) => AppDependencies.create(
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
  additionalSources: additionalSources,
);

Future<void> _unmount(WidgetTester tester) async {
  // Drift closes watched queries on a zero-duration timer. Unmount and pump
  // that timer before testWidgets verifies there are no timers left.
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pump(Duration.zero);
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

final class _MangaCatalogSource
    implements
        MangaSearchSource,
        MangaSeriesSource,
        MangaPageSource,
        MediaSourcePresentation {
  _MangaCatalogSource({
    this.resultTitle = 'Catalog title',
    this.id = const SourceId('test:manga'),
    this.languageCode = 'en',
    this.fail = false,
    this.empty = false,
  });

  final bool fail;
  final bool empty;

  final String resultTitle;
  int searches = 0;

  @override
  final SourceId id;

  @override
  String get name => 'Manga source [en]';
  @override
  String get displayName => 'Manga source';
  @override
  final String languageCode;
  @override
  String? get presentationGroupId => 'test:manga';

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async {
    searches++;
    if (fail) throw StateError('offline');
    return MangaSearchPage(
      results: empty
          ? []
          : [
              MangaPreview(
                media: Media(
                  title: resultTitle,
                  type: MediaType.manga,
                  source: SourceMediaRef(sourceId: id, itemId: 'catalog-title'),
                ),
              ),
            ],
      hasNextPage: false,
      page: page,
    );
  }

  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef manga) async =>
      MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Catalog title'),
        chapters: const [],
      );

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

final class _NovelCatalogSource
    implements NovelSearchSource, NovelSeriesSource, NovelChapterSource {
  int searches = 0;

  @override
  SourceId get id => const SourceId('test:novel');

  @override
  String get name => 'Novel source [en]';

  @override
  Future<NovelSearchPage> search(String query, {int page = 1}) async {
    searches++;
    return NovelSearchPage(
      results: [
        NovelPreview(
          media: Media(
            title: 'Catalog title',
            type: MediaType.lightNovel,
            source: SourceMediaRef(sourceId: id, itemId: 'catalog-title'),
          ),
          metadata: MediaMetadata(title: 'Catalog title'),
        ),
      ],
      page: page,
      hasNextPage: false,
    );
  }

  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async => NovelDetails(
    metadata: MediaMetadata(title: 'Catalog title'),
    chapters: const [],
  );

  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async =>
      RichReadingContent(html: '');

  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
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
