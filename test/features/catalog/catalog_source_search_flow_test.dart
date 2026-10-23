import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/app/app.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/novel_reader/novel_reader_page.dart';
import 'package:hikari/features/novel_reader/widgets/novel_content_view.dart';
import 'package:hikari/features/home/home_page.dart';
import 'package:hikari/features/home/widgets/continue_shelf.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/features/catalog/catalog_detail_page.dart';
import 'package:hikari/features/catalog/widgets/catalog_source_picker.dart';
import 'package:hikari/features/remote_manga/manga_series_page.dart';
import 'package:hikari/features/remote_manga/manga_chapter_reader_page.dart';
import 'package:hikari/features/remote_novel/novel_chapter_reader_page.dart';
import 'package:hikari/features/remote_novel/novel_series_page.dart';
import 'package:hikari/features/source_search/source_search_page.dart';
import 'package:hikari/features/source_search/source_search_view_model.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';

void main() {
  testWidgets(
    'Change source supersedes a confirmed source with pending chapters',
    (tester) async {
      final database = UserDatabase(NativeDatabase.memory());
      final pending = Completer<MangaSeriesDetails>();
      final first = _MangaCatalogSource(
        id: const SourceId('test:first'),
        languageCode: 'en',
      )..detailsGate = pending;
      final second = _MangaCatalogSource(
        id: const SourceId('test:second'),
        languageCode: 'fr',
      );
      final dependencies = _dependencies(
        database: database,
        local: _LocalCatalog(),
        entry: _entry(MediaType.manga),
        additionalSources: [first, second],
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
        await tester.tap(find.text(first.displayName).first);
        await tester.pumpAndSettle();
        await tester.tap(find.text('EN'));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 500));
        expect(first.detailLoads, 1);
        // Presenter resolves after route teardown; chapter spinner stays active.
        for (
          var frame = 0;
          frame < 20 && find.text('Change source').evaluate().isEmpty;
          frame++
        ) {
          await tester.pump(const Duration(milliseconds: 50));
        }
        await tester.tap(find.text('Change source'));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 500));
        expect(find.byType(CatalogSourcePicker), findsOneWidget);
        await tester.tap(find.text(second.displayName).first);
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 500));
        await tester.tap(find.text('FR'));
        await tester.pumpAndSettle();
        expect(second.detailLoads, 1);
        await tester.drag(find.byType(NestedScrollView), const Offset(0, -500));
        await tester.pumpAndSettle();
        expect(find.text('Chapter 30'), findsOneWidget);
        pending.complete(
          MangaSeriesDetails(
            metadata: MediaMetadata(title: 'Late source A'),
            chapterListOrder: ChapterListOrder.readingOrder,
            chapters: const [],
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('Late source A'), findsNothing);
        expect(find.text('Chapter 30'), findsOneWidget);
        expect(tester.takeException(), isNull);
      } finally {
        if (!pending.isCompleted) {
          pending.complete(
            MangaSeriesDetails(
              metadata: MediaMetadata(title: 'A'),
              chapterListOrder: ChapterListOrder.readingOrder,
              chapters: const [],
            ),
          );
        }
        await _unmount(tester);
      }
    },
  );

  testWidgets(
    'mounted closing novel rejects late reload and unseen illustration',
    (tester) async {
      final closing = ValueNotifier(false);
      addTearDown(closing.dispose);
      final reload = Completer<RichReadingContent>();
      var reads = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: ValueListenableBuilder<bool>(
            valueListenable: closing,
            builder: (_, value, _) => NovelReaderPage(
              title: 'Closing chapter',
              closing: value,
              loadContent: () async =>
                  RichReadingContent(html: '<p>Original chapter</p>'),
              reloadContent: () => reload.future,
              readResource: (_) async {
                reads++;
                return Uint8List(0);
              },
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Reload chapter'));
      await tester.pump();
      closing.value = true;
      await tester.pump();
      reload.complete(
        RichReadingContent(
          html: '<p>Late chapter</p><img src="unseen">',
          resources: const {
            'unseen': SourceMediaRef(
              sourceId: SourceId('test:novel'),
              itemId: 'unseen',
            ),
          },
        ),
      );
      // Closing route intentionally retains its reload spinner until disposal.
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 100));
      expect(find.byType(NovelReaderPage), findsOneWidget);
      expect(
        tester.widget<NovelReaderPage>(find.byType(NovelReaderPage)).closing,
        isTrue,
      );
      expect(
        tester
            .widget<NovelContentView>(find.byType(NovelContentView))
            .content
            .html,
        contains('Original chapter'),
      );
      expect(reads, 0);
      expect(tester.takeException(), isNull);
      await _unmount(tester);
    },
  );

  testWidgets('Information and Chapters refresh have independent counters', (
    tester,
  ) async {
    final database = UserDatabase(NativeDatabase.memory());
    final source = _NovelCatalogSource();
    var metadataLoads = 0;
    final dependencies = _dependencies(
      database: database,
      local: _LocalCatalog(),
      entry: _entry(MediaType.lightNovel),
      additionalSources: [source],
      onMetadataLoad: () => metadataLoads++,
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
      expect(metadataLoads, 1);
      expect(source.detailLoads, 0);
      await tester.tap(find.byTooltip('Refresh'));
      await tester.pumpAndSettle();
      expect(metadataLoads, 2);
      expect(source.detailLoads, 0);
      await tester.tap(find.text('Read'));
      await tester.pumpAndSettle();
      await tester.tap(find.text(source.name));
      await tester.pumpAndSettle();
      expect(source.detailLoads, 1);
      await tester.tap(find.byTooltip('Refresh'));
      await tester.pumpAndSettle();
      expect(source.detailLoads, 2);
      expect(metadataLoads, 2);
      await tester.tap(find.text('Information'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Refresh'));
      await tester.pumpAndSettle();
      expect(metadataLoads, 3);
      expect(source.detailLoads, 2);
    } finally {
      await _unmount(tester);
    }
  });

  testWidgets(
    'same-title source switching isolates Library and progress identity',
    (tester) async {
      final database = UserDatabase(NativeDatabase.memory());
      final a = _NovelCatalogSource();
      final b = _NovelCatalogSource(
        id: const SourceId('test:other-novel'),
        name: 'Other novel source [en]',
      );
      final dependencies = _dependencies(
        database: database,
        local: _LocalCatalog(),
        entry: _entry(MediaType.lightNovel),
        additionalSources: [a, b],
      );
      addTearDown(() async {
        await dependencies.dispose();
        await database.close();
      });
      SourceMediaRef series(_NovelCatalogSource source) =>
          SourceMediaRef(sourceId: source.id, itemId: 'catalog-title');
      try {
        await dependencies.libraryRepository.upsert(
          LibraryEntry(
            media: Media(
              title: 'Catalog title',
              type: MediaType.lightNovel,
              source: series(a),
            ),
            addedAt: DateTime.utc(2026),
          ),
        );
        await tester.pumpWidget(HikariApp(dependencies: dependencies));
        await tester.pumpAndSettle();
        await tester.tap(find.text('View details'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Read'));
        await tester.pumpAndSettle();
        await tester.tap(find.text(a.name));
        await tester.pumpAndSettle();
        expect(find.byTooltip('Remove from library'), findsOneWidget);
        await tester.ensureVisible(find.text('Start reading'));
        await tester.tap(find.text('Start reading'));
        await tester.pumpAndSettle();
        final reader = tester
            .widget<NovelChapterReaderPage>(find.byType(NovelChapterReaderPage))
            .viewModel;
        await reader.openNext();
        await tester.pumpAndSettle();
        await reader.saveProgress(
          reader.state.target,
          TextPosition(progression: 0.4),
          false,
        );
        await tester.pageBack();
        await tester.pumpAndSettle();
        expect(
          await dependencies.seriesContinuationRepository.load(series(a)),
          SourceMediaRef(sourceId: a.id, itemId: 'chapter-2'),
        );
        await tester.drag(find.byType(CustomScrollView), const Offset(0, 1800));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Change source'));
        await tester.pumpAndSettle();
        await tester.tap(find.text(b.name));
        await tester.pumpAndSettle();
        expect(find.byTooltip('Add to library'), findsOneWidget);
        expect(find.text('Start reading'), findsOneWidget);
        expect(find.text('Continue reading'), findsNothing);
        expect(
          await dependencies.seriesContinuationRepository.load(series(b)),
          isNull,
        );
        expect(
          await dependencies.libraryRepository.contains(series(b)),
          isFalse,
        );
        final aChapter = SourceMediaRef(sourceId: a.id, itemId: 'chapter-2');
        final aProgress = await dependencies.progressRepository.load(aChapter);
        expect(aProgress, isNotNull);
        expect(
          await dependencies.progressRepository.load(
            SourceMediaRef(sourceId: b.id, itemId: 'chapter-2'),
          ),
          isNull,
        );
        await tester.tap(find.byTooltip('Add to library'));
        await tester.pumpAndSettle();
        expect(
          (await dependencies.libraryRepository.loadAll())
              .map((entry) => entry.media.source)
              .toSet(),
          {series(a), series(b)},
        );
        await tester.tap(find.text('Start reading'));
        await tester.pumpAndSettle();
        expect(
          tester
              .widget<NovelChapterReaderPage>(
                find.byType(NovelChapterReaderPage),
              )
              .viewModel
              .currentChapter
              .source,
          SourceMediaRef(sourceId: b.id, itemId: 'chapter-1'),
        );
        await tester.pageBack();
        await tester.pumpAndSettle();
        expect(
          await dependencies.seriesContinuationRepository.load(series(a)),
          SourceMediaRef(sourceId: a.id, itemId: 'chapter-2'),
        );
        expect(
          await dependencies.seriesContinuationRepository.load(series(b)),
          SourceMediaRef(sourceId: b.id, itemId: 'chapter-1'),
        );
        expect(
          ((await dependencies.progressRepository.load(aChapter))!.position
                  as TextPosition)
              .progression,
          (aProgress!.position as TextPosition).progression,
        );
        expect(
          await dependencies.progressRepository.load(
            SourceMediaRef(sourceId: b.id, itemId: 'chapter-1'),
          ),
          isNotNull,
        );
      } finally {
        await _unmount(tester);
      }
    },
  );

  testWidgets(
    'disposed Catalog rejects delayed initial novel reader and releases once',
    (tester) async {
      final database = UserDatabase(NativeDatabase.memory());
      final source = _NovelCatalogSource();
      final dependencies = _dependencies(
        database: database,
        local: _LocalCatalog(),
        entry: _entry(MediaType.lightNovel),
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
        await tester.tap(find.text(source.name));
        await tester.pumpAndSettle();
        final gate = Completer<RichReadingContent>();
        source.contentGate = gate;
        await tester.ensureVisible(find.text('Start reading'));
        await tester.tap(find.text('Start reading'));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 50));
        final readerLease = source.leases.last;
        expect(readerLease.releases, 0);
        await tester.pageBack();
        await tester.pumpAndSettle();
        expect(find.byType(CatalogDetailPage), findsNothing);
        expect(readerLease.releases, 0);
        gate.complete(RichReadingContent(html: '<p>Late chapter</p>'));
        await tester.pumpAndSettle();
        expect(find.byType(NovelChapterReaderPage), findsNothing);
        expect(readerLease.releases, 1);
        expect(tester.takeException(), isNull);
      } finally {
        await _unmount(tester);
      }
    },
  );

  testWidgets('standalone Source Search opens native Detail then Reader', (
    tester,
  ) async {
    final database = UserDatabase(NativeDatabase.memory());
    final source = _NovelCatalogSource();
    final dependencies = _dependencies(
      database: database,
      local: _LocalCatalog(),
      entry: _entry(MediaType.lightNovel),
      additionalSources: [source],
    );
    addTearDown(() async {
      await dependencies.dispose();
      await database.close();
    });
    try {
      await tester.pumpWidget(HikariApp(dependencies: dependencies));
      await tester.pumpAndSettle();
      // Navigate through the actual Catalog Search action, not a test-only route.
      await tester.tap(find.byTooltip('Search').first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Search sources'));
      await tester.pumpAndSettle();
      final searchPage = tester.widget<SourceSearchPage>(
        find.byType(SourceSearchPage),
      );
      expect(searchPage.catalogScopeLabel, isNull);
      expect(searchPage.fixedMediaType, isNull);
      expect(searchPage.initialSourceId, isNull);
      expect(
        source.searches,
        0,
      ); // Standalone entry does not fetch until submit.
      await tester.enterText(find.byType(SearchBar), 'Catalog title');
      await tester.testTextInput.receiveAction(TextInputAction.search);
      await tester.pumpAndSettle();
      expect(source.searches, 1);
      await tester.tap(find.widgetWithText(MediaPoster, 'Catalog title'));
      await tester.pumpAndSettle();
      expect(find.byType(NovelSeriesPage), findsOneWidget);
      expect(find.byType(CatalogDetailPage), findsNothing);
      expect(source.detailLoads, 1);
      await tester.ensureVisible(find.text('Start reading'));
      await tester.tap(find.text('Start reading'));
      await tester.pumpAndSettle();
      expect(find.byType(NovelChapterReaderPage), findsOneWidget);
      expect(
        tester
            .widget<NovelChapterReaderPage>(find.byType(NovelChapterReaderPage))
            .viewModel
            .currentChapter
            .source,
        SourceMediaRef(sourceId: source.id, itemId: 'chapter-1'),
      );
    } finally {
      await _unmount(tester);
    }
  });

  for (final type in [MediaType.manga, MediaType.lightNovel]) {
    testWidgets(
      '$type Reader back retains Catalog source search order and scroll',
      (tester) async {
        final database = UserDatabase(NativeDatabase.memory());
        final manga = _MangaCatalogSource();
        final novel = _NovelCatalogSource();
        final dependencies = _dependencies(
          database: database,
          local: _LocalCatalog(),
          entry: _entry(type),
          additionalSources: [manga, novel],
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
          expect(manga.detailLoads + novel.detailLoads, 0);
          expect(manga.searches + novel.searches, 0);
          await tester.tap(find.text('Read'));
          await tester.pumpAndSettle();
          await tester.tap(
            find.text(type == MediaType.manga ? manga.displayName : novel.name),
          );
          await tester.pumpAndSettle();
          final scroll = find.descendant(
            of: find.byKey(const ValueKey('catalog-chapters-tab')),
            matching: find.byType(CustomScrollView),
          );
          await tester.drag(scroll, const Offset(0, -450));
          await tester.pumpAndSettle();
          await tester.tap(find.byTooltip('Reverse chapter order'));
          await tester.pumpAndSettle();
          await tester.tap(find.byTooltip('Search chapters'));
          await tester.pumpAndSettle();
          await tester.enterText(find.byType(TextField), '2');
          await tester.testTextInput.receiveAction(TextInputAction.done);
          await tester.pumpAndSettle();
          await Scrollable.ensureVisible(
            tester.element(find.text('Chapter 2').first),
            alignment: 0.5,
          );
          await tester.pumpAndSettle();
          double chaptersOffset() => tester
              .state<ScrollableState>(
                find.descendant(
                  of: scroll,
                  matching: find.byWidgetPredicate(
                    (widget) =>
                        widget is Scrollable &&
                        widget.axisDirection == AxisDirection.down,
                  ),
                ),
              )
              .position
              .pixels;
          double currentOffset() {
            final header = tester.state<NestedScrollViewState>(
              find.byType(NestedScrollView),
            );
            return header.outerController.offset + chaptersOffset();
          }

          final offset = currentOffset();
          await tester.tap(find.text('Chapter 2').first);
          await tester.pumpAndSettle();
          expect(find.byType(MangaSeriesPage), findsNothing);
          expect(find.byType(NovelSeriesPage), findsNothing);
          expect(
            find.byType(
              type == MediaType.manga
                  ? MangaChapterReaderPage
                  : NovelChapterReaderPage,
            ),
            findsOneWidget,
          );
          if (type == MediaType.manga) {
            final vm = tester
                .widget<MangaChapterReaderPage>(
                  find.byType(MangaChapterReaderPage),
                )
                .viewModel;
            expect(
              vm.state.target.chapter.source,
              SourceMediaRef(sourceId: manga.id, itemId: 'chapter-2'),
            );
            await vm.openNext();
            expect(vm.state.target.chapter.source.itemId, 'chapter-3');
            await vm.openPrevious();
            expect(vm.state.target.chapter.source.itemId, 'chapter-2');
          } else {
            final vm = tester
                .widget<NovelChapterReaderPage>(
                  find.byType(NovelChapterReaderPage),
                )
                .viewModel;
            expect(
              vm.state.target.chapter.source,
              SourceMediaRef(sourceId: novel.id, itemId: 'chapter-2'),
            );
            await vm.openNext();
            expect(vm.state.target.chapter.source.itemId, 'chapter-3');
            await vm.openPrevious();
            expect(vm.state.target.chapter.source.itemId, 'chapter-2');
          }
          await tester.pumpAndSettle();
          await tester.pageBack();
          await tester.pumpAndSettle();
          expect(find.byType(CatalogDetailPage), findsOneWidget);
          expect(find.byTooltip('Restore source order'), findsOneWidget);
          expect(
            tester.widget<TextField>(find.byType(TextField)).controller!.text,
            '2',
          );
          expect(currentOffset(), closeTo(offset, 1));
          expect(manga.detailLoads + novel.detailLoads, 1);
          expect(manga.searches + novel.searches, 1);
          // Detached tab positions restore without source requests.
          final tabOffset = currentOffset();
          expect(tabOffset, greaterThan(0));
          await tester.tap(find.text('Information'));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Chapters'));
          await tester.pumpAndSettle();
          expect(currentOffset(), closeTo(tabOffset, 1));
          expect(manga.detailLoads + novel.detailLoads, 1);
          // Cancel source replacement must retain current list/filter/order.
          await tester.drag(scroll, const Offset(0, 1800));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Change source'));
          await tester.pumpAndSettle();
          await tester.binding.handlePopRoute();
          await tester.pumpAndSettle();
          expect(find.byTooltip('Restore source order'), findsOneWidget);
          expect(
            tester.widget<TextField>(find.byType(TextField)).controller!.text,
            '2',
          );
        } finally {
          await _unmount(tester);
        }
      },
    );
  }
  testWidgets('narrow large text Catalog chapters remain usable and lazy', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final database = UserDatabase(NativeDatabase.memory());
    final source = _MangaCatalogSource();
    final dependencies = _dependencies(
      database: database,
      local: _LocalCatalog(),
      entry: _entry(MediaType.manga),
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
      tester.platformDispatcher.textScaleFactorTestValue = 2;
      addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Read'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text(source.displayName));
      await tester.pumpAndSettle();
      await tester.tap(find.text(source.displayName));
      await tester.pumpAndSettle();
      await tester.drag(find.byType(CustomScrollView), const Offset(0, -650));
      await tester.pumpAndSettle();
      expect(find.text('Change source'), findsOneWidget);
      expect(find.byType(ListTile).evaluate().length, lessThan(30));
      expect(tester.takeException(), isNull);
    } finally {
      await _unmount(tester);
    }
  });

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

      expect(source.searches, 0);
      expect(source.detailLoads, 0);
      expect(find.text('Information'), findsOneWidget);
      await tester.tap(find.text('Read'));
      await tester.pumpAndSettle();

      expect(find.text('Read from'), findsOneWidget);
      expect(find.text(source.displayName), findsOneWidget);
      expect(find.byType(SourceSearchPage), findsNothing);
      expect(local.scans, 0);

      await tester.tap(find.text(source.displayName));
      await tester.pumpAndSettle();

      expect(find.byType(MangaSeriesPage), findsNothing);
      expect(find.byType(CatalogDetailPage), findsOneWidget);
      expect(find.text('Chapters'), findsOneWidget);
      expect(find.text('Change source'), findsOneWidget);
      expect(find.byType(SourceSearchPage), findsNothing);
      expect(source.searches, 1);
      expect(local.scans, 0);
    } finally {
      await _unmount(tester);
    }
  });

  testWidgets(
    'ambiguous source candidates render source-owned cover metadata',
    (tester) async {
      final entry = _entry(MediaType.manga);
      final database = UserDatabase(NativeDatabase.memory());
      final local = _LocalCatalog();
      final source = _MangaCatalogSource(
        resultTitle: 'Catalog title side story',
        withCover: true,
      );
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

        expect(find.text('Choose matching title'), findsOneWidget);
        expect(find.text('Source Author'), findsOneWidget);
        expect(source.artworkReads, 1);
        expect(
          find.byWidgetPredicate(
            (widget) =>
                widget is Image &&
                widget.image is ResizeImage &&
                (widget.image as ResizeImage).imageProvider is MemoryImage,
          ),
          findsOneWidget,
        );
      } finally {
        await _unmount(tester);
      }
    },
  );

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

      expect(find.text('Choose matching title'), findsOneWidget);
      await tester.tap(find.text('Search manually'));
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
      await tester.tap(find.byType(MediaPoster).first);
      await tester.pumpAndSettle();
      expect(find.byType(SourceSearchPage), findsNothing);
      expect(find.byType(MangaSeriesPage), findsNothing);
      expect(find.byType(CatalogDetailPage), findsOneWidget);
      expect(find.text('Change source'), findsOneWidget);
      expect(source.detailLoads, 1);
    } finally {
      await _unmount(tester);
    }
  });

  testWidgets('novel manual selection returns to Catalog and opens Reader', (
    tester,
  ) async {
    final database = UserDatabase(NativeDatabase.memory());
    final source = _NovelCatalogSource(resultTitle: 'Catalog title side story');
    final dependencies = _dependencies(
      database: database,
      local: _LocalCatalog(),
      entry: _entry(MediaType.lightNovel),
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
      await tester.tap(find.text(source.name));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Search manually'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<SourceSearchPage>(find.byType(SourceSearchPage))
            .fixedMediaType,
        MediaType.lightNovel,
      );
      await tester.tap(find.byType(MediaPoster).first);
      await tester.pumpAndSettle();
      expect(find.byType(NovelSeriesPage), findsNothing);
      expect(find.byType(CatalogDetailPage), findsOneWidget);
      await tester.ensureVisible(find.text('Start reading'));
      await tester.tap(find.text('Start reading'));
      await tester.pumpAndSettle();
      expect(find.byType(NovelChapterReaderPage), findsOneWidget);
      expect(
        tester
            .widget<NovelChapterReaderPage>(find.byType(NovelChapterReaderPage))
            .viewModel
            .currentChapter
            .source
            .itemId,
        'chapter-1',
      );
    } finally {
      await _unmount(tester);
    }
  });

  testWidgets(
    'saved Catalog reader refreshes Home Continue after both Back routes',
    (tester) async {
      final database = UserDatabase(NativeDatabase.memory());
      final source = _NovelCatalogSource();
      final dependencies = _dependencies(
        database: database,
        local: _LocalCatalog(),
        entry: _entry(MediaType.lightNovel),
        additionalSources: [source],
      );
      addTearDown(() async {
        await dependencies.dispose();
        await database.close();
      });
      try {
        final series = SourceMediaRef(
          sourceId: source.id,
          itemId: 'catalog-title',
        );
        await dependencies.libraryRepository.upsert(
          LibraryEntry(
            media: Media(
              title: 'Saved source novel',
              type: MediaType.lightNovel,
              source: series,
            ),
            addedAt: DateTime.utc(2026),
          ),
        );
        await tester.pumpWidget(HikariApp(dependencies: dependencies));
        await tester.pumpAndSettle();
        final revision = tester
            .widget<HomePage>(find.byType(HomePage))
            .refreshRevision;
        await tester.tap(find.text('View details'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Read'));
        await tester.pumpAndSettle();
        await tester.tap(find.text(source.name));
        await tester.pumpAndSettle();
        await tester.ensureVisible(find.text('Start reading'));
        await tester.tap(find.text('Start reading'));
        await tester.pumpAndSettle();
        final model = tester
            .widget<NovelChapterReaderPage>(find.byType(NovelChapterReaderPage))
            .viewModel;
        await model.openNext();
        await tester.pumpAndSettle();
        await tester.pageBack();
        await tester.pumpAndSettle();
        await tester.pageBack();
        await tester.pumpAndSettle();
        expect(
          tester.widget<HomePage>(find.byType(HomePage)).refreshRevision,
          greaterThan(revision),
        );
        final items = tester
            .widget<ContinueShelf>(find.byType(ContinueShelf))
            .items;
        expect(items.single.media.source, series);
        expect(items.single.chapter?.itemId, 'chapter-2');
        expect(
          await dependencies.seriesContinuationRepository.load(series),
          SourceMediaRef(sourceId: source.id, itemId: 'chapter-2'),
        );
      } finally {
        await _unmount(tester);
      }
    },
  );

  for (final native in [false, true]) {
    testWidgets(
      'novel delayed reload Back retains independent lease; native $native',
      (tester) async {
        final database = UserDatabase(NativeDatabase.memory());
        final source = _NovelCatalogSource();
        final dependencies = _dependencies(
          database: database,
          local: _LocalCatalog(),
          entry: _entry(MediaType.lightNovel),
          additionalSources: [source],
        );
        addTearDown(() async {
          await dependencies.dispose();
          await database.close();
        });
        try {
          if (native) {
            await dependencies.libraryRepository.upsert(
              LibraryEntry(
                media: Media(
                  title: 'Saved source novel',
                  type: MediaType.lightNovel,
                  source: SourceMediaRef(
                    sourceId: source.id,
                    itemId: 'catalog-title',
                  ),
                ),
                addedAt: DateTime.utc(2026),
              ),
            );
          }
          await tester.pumpWidget(HikariApp(dependencies: dependencies));
          await tester.pumpAndSettle();
          if (native) {
            await tester.tap(find.text('Library').last);
            await tester.pumpAndSettle();
            await tester.tap(find.byType(MediaPoster).first);
            await tester.pumpAndSettle();
            expect(find.byType(NovelSeriesPage), findsOneWidget);
            expect(find.byType(CatalogDetailPage), findsNothing);
            expect(source.searches, 0);
          } else {
            await tester.tap(find.text('View details'));
            await tester.pumpAndSettle();
            await tester.tap(find.text('Read'));
            await tester.pumpAndSettle();
            await tester.tap(find.text(source.name));
            await tester.pumpAndSettle();
          }
          await tester.ensureVisible(find.text('Start reading'));
          await tester.tap(find.text('Start reading'));
          await tester.pumpAndSettle();
          final readerLease = source.leases.last;
          final gate = Completer<RichReadingContent>();
          source.contentGate = gate;
          readerLease.throwOnRelease = true;
          await tester.tap(find.byTooltip('Reload chapter'));
          await tester.pump();
          expect(readerLease.releases, 0);
          await tester.pageBack();
          await tester.pumpAndSettle();
          expect(find.byType(NovelChapterReaderPage), findsNothing);
          expect(readerLease.releases, 0);
          if (native) {
            await tester.pageBack();
            await tester.pumpAndSettle();
            expect(find.byType(NovelSeriesPage), findsNothing);
            expect(source.leases.first.releases, 1);
            expect(readerLease.releases, 0);
          }
          gate.complete(
            RichReadingContent(
              html: '<p>Late content</p><img src="unseen.png">',
            ),
          );
          await tester.pumpAndSettle();
          expect(readerLease.releases, 1);
          expect(source.resourceReads, 0);
          expect(
            tester.takeException().toString(),
            contains('lease cleanup failed'),
          );
          expect(tester.takeException(), isNull);
        } finally {
          await _unmount(tester);
        }
      },
    );
  }

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

      expect(source.searches, 0);
      expect(source.detailLoads, 0);
      expect(find.text('Information'), findsOneWidget);
      await tester.tap(find.text('Read'));
      await tester.pumpAndSettle();

      expect(find.text('Read from'), findsOneWidget);
      expect(find.text(source.name), findsOneWidget);
      expect(find.byType(SourceSearchPage), findsNothing);
      expect(local.scans, 0);

      await tester.tap(find.text(source.name));
      await tester.pumpAndSettle();

      expect(find.byType(NovelSeriesPage), findsNothing);
      expect(find.byType(CatalogDetailPage), findsOneWidget);
      expect(find.text('Chapters'), findsOneWidget);
      expect(find.text('Change source'), findsOneWidget);
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
  void Function()? onMetadataLoad,
}) => AppDependencies.create(
  database: database,
  cache: _NoCache(),
  localMediaSource: local,
  catalogProvider: _Provider(
    onDiscover: () async => CatalogDiscovery(
      sections: {
        CatalogSection.featured: [entry],
      },
    ),
    onLoadDetails: (_) async {
      onMetadataLoad?.call();
      return CatalogEntryDetails(entry: entry);
    },
  ),
  additionalSources: additionalSources,
);

// UI routing tests avoid real filesystem work in the fake-async zone.
final class _NoCache implements ByteCache {
  @override
  Future<Uint8List?> read(String namespace, String key) async => null;
  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {}
  @override
  Future<void> close() async {}
}

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
        MediaSourcePresentation,
        ArtworkSource {
  _MangaCatalogSource({
    this.resultTitle = 'Catalog title',
    this.id = const SourceId('test:manga'),
    this.languageCode = 'en',
    this.fail = false,
    this.empty = false,
    this.withCover = false,
  });

  final bool fail;
  final bool empty;
  final bool withCover;

  final String resultTitle;
  int searches = 0;
  int detailLoads = 0;
  int artworkReads = 0;
  Completer<MangaSeriesDetails>? detailsGate;

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
                metadata: withCover
                    ? MediaMetadata(
                        title: resultTitle,
                        cover: SourceMediaRef(sourceId: id, itemId: 'cover'),
                        authors: const ['Source Author'],
                      )
                    : null,
              ),
            ],
      hasNextPage: false,
      page: page,
    );
  }

  @override
  Future<Uint8List> readArtwork(SourceMediaRef artwork) async {
    artworkReads++;
    return Uint8List.fromList(
      base64Decode(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
      ),
    );
  }

  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef manga) async {
    detailLoads++;
    if (detailsGate case final gate?) return gate.future;
    return MangaSeriesDetails(
      metadata: MediaMetadata(title: 'Catalog title'),
      chapterListOrder: ChapterListOrder.reverseReadingOrder,
      chapters: [
        for (var i = 30; i >= 1; i--)
          MangaChapter(
            title: 'Chapter $i',
            chapterNumber: i.toDouble(),
            source: SourceMediaRef(sourceId: id, itemId: 'chapter-$i'),
          ),
      ],
    );
  }

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => [
    SourceMediaRef(sourceId: id, itemId: '${readable.itemId}-page'),
  ];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) => readArtwork(page);
}

final class _NovelCatalogSource
    implements
        NovelSearchSource,
        NovelSeriesSource,
        NovelChapterSource,
        MediaOpenLeaseSource {
  _NovelCatalogSource({
    this.resultTitle = 'Catalog title',
    this.id = const SourceId('test:novel'),
    this.name = 'Novel source [en]',
  });
  final String resultTitle;
  int searches = 0;
  int detailLoads = 0;
  int resourceReads = 0;
  Completer<RichReadingContent>? contentGate;
  final leases = <_Lease>[];
  bool throwOnRelease = false;

  @override
  MediaOpenLease acquireOpenLease(SourceMediaRef media) {
    final lease = _Lease(throwOnRelease);
    leases.add(lease);
    return lease;
  }

  @override
  final SourceId id;

  @override
  final String name;

  @override
  Future<NovelSearchPage> search(String query, {int page = 1}) async {
    searches++;
    return NovelSearchPage(
      results: [
        NovelPreview(
          media: Media(
            title: resultTitle,
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
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async {
    detailLoads++;
    return NovelDetails(
      metadata: MediaMetadata(title: 'Catalog title'),
      chapterListOrder: ChapterListOrder.reverseReadingOrder,
      chapters: [
        for (var i = 30; i >= 1; i--)
          NovelChapter(
            title: 'Chapter $i',
            chapterNumber: i.toDouble(),
            source: SourceMediaRef(sourceId: id, itemId: 'chapter-$i'),
          ),
      ],
    );
  }

  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) =>
      contentGate?.future ??
      Future.value(RichReadingContent(html: '<p>Chapter text</p>'));

  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async {
    resourceReads++;
    return Uint8List(0);
  }
}

final class _Lease implements MediaOpenLease {
  _Lease(this.throwOnRelease);
  bool throwOnRelease;
  int releases = 0;
  @override
  Future<void> release() async {
    releases++;
    if (throwOnRelease) throw StateError('lease cleanup failed');
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
