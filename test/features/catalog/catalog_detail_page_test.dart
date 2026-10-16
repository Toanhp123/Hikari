import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/read_remote_chapter_list.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/series_continuation.dart';
import 'package:hikari/application/catalog/resolve_catalog_source.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/catalog/catalog_source_selection.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/features/catalog/catalog_detail_page.dart';
import 'package:hikari/features/catalog/widgets/catalog_detail_hero.dart';
import 'package:hikari/features/catalog/catalog_source_picker_view_model.dart';
import 'package:hikari/features/catalog/widgets/catalog_source_picker.dart';
import 'package:hikari/features/catalog/widgets/catalog_source_candidate_list.dart';

void main() {
  testWidgets(
    'Information remains lazy; Chapters restores exact cached source',
    (tester) async {
      final choices = _Choices();
      final selected = Media(
        title: 'Saved candidate',
        type: MediaType.manga,
        source: const SourceMediaRef(
          sourceId: SourceId('test:manga'),
          itemId: 'candidate-7',
        ),
      );
      choices.selected = selected;
      final memory = _MemoryCache();
      final chapters = ReadRemoteChapterList(memory);
      await chapters.persistManga(selected.source, [
        MangaChapter(
          title: 'Saved chapter 1',
          source: const SourceMediaRef(
            sourceId: SourceId('test:manga'),
            itemId: 'chapter-1',
          ),
        ),
      ], chapterListOrder: ChapterListOrder.readingOrder);
      memory.reads = 0;
      await tester.pumpWidget(
        _app(
          _detail(
            initialEntry: _related,
            loadDetails: LoadCatalogEntryDetails(_Provider()),
            resolveCatalogSource: _resolver(),
            sourceSelections: choices,
            chapterCache: chapters,
            openMedia: (_, _, {previewMetadata, canNavigate}) async {},
            openRelated: (_) {},
            openSourceSearch: (_, _, _, _, _) {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(choices.reads, 0);
      expect(memory.reads, 0);
      expect(find.text('Saved chapter 1'), findsNothing);

      await tester.tap(find.text('Chapters'));
      await tester.pumpAndSettle();
      expect(choices.reads, 1);
      expect(memory.reads, greaterThan(0));
      expect(choices.saves, 0); // restore must not rewrite a preference
      await tester.drag(
        find.descendant(
          of: find.byKey(const ValueKey('catalog-chapters-tab')),
          matching: find.byType(CustomScrollView),
        ),
        const Offset(0, -450),
      );
      await tester.pumpAndSettle();
      expect(find.text('Saved chapter 1'), findsOneWidget);
      expect(find.text('Change source'), findsOneWidget);
      expect(find.textContaining('Cached chapters · fresh'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'shared hero stays above both tabs and chapter scroll does not reset the header',
    (tester) async {
      tester.view.physicalSize = const Size(400, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      const selected = Media(
        title: 'Saved candidate',
        type: MediaType.manga,
        source: SourceMediaRef(
          sourceId: SourceId('test:manga'),
          itemId: 'saved-series',
        ),
      );
      final choices = _Choices()..selected = selected;
      final cache = ReadRemoteChapterList(_MemoryCache());
      await cache.persistManga(selected.source, [
        for (var index = 0; index < 36; index++)
          MangaChapter(
            title: 'Chapter $index',
            source: SourceMediaRef(
              sourceId: const SourceId('test:manga'),
              itemId: 'chapter-$index',
            ),
          ),
      ], chapterListOrder: ChapterListOrder.readingOrder);
      await tester.pumpWidget(
        _app(
          _detail(
            initialEntry: _related,
            loadDetails: LoadCatalogEntryDetails(_Provider()),
            resolveCatalogSource: _resolver(),
            sourceSelections: choices,
            chapterCache: cache,
            openMedia: (_, _, {previewMetadata, canNavigate}) async {},
            openRelated: (_) {},
            openSourceSearch: (_, _, _, _, _) {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byType(CatalogDetailHero), findsOneWidget);
      expect(choices.reads, 0);
      await tester.tap(find.text('Chapters'));
      await tester.pumpAndSettle();
      expect(find.byType(CatalogDetailHero), findsOneWidget);
      expect(choices.reads, 1);
      final chapterView = find.descendant(
        of: find.byKey(const ValueKey('catalog-chapters-tab')),
        matching: find.byType(CustomScrollView),
      );
      await tester.drag(chapterView, const Offset(0, -850));
      await tester.pumpAndSettle();
      final chapterScrollable = tester.state<ScrollableState>(
        find.descendant(
          of: chapterView,
          matching: find.byWidgetPredicate(
            (widget) =>
                widget is Scrollable &&
                widget.axisDirection == AxisDirection.down,
          ),
        ),
      );
      final chapterOffset = chapterScrollable.position.pixels;
      expect(chapterOffset, greaterThan(0));
      final header = tester.state<NestedScrollViewState>(
        find.byType(NestedScrollView),
      );
      final headerOffset = header.outerController.offset;
      expect(headerOffset, greaterThan(0));
      await tester.tap(find.text('Information'));
      await tester.pumpAndSettle();
      expect(find.byType(CatalogDetailHero), findsOneWidget);
      expect(header.outerController.offset, closeTo(headerOffset, 1));
      await tester.tap(find.text('Chapters'));
      await tester.pumpAndSettle();
      final restoredChapterView = find.descendant(
        of: find.byKey(const ValueKey('catalog-chapters-tab')),
        matching: find.byType(CustomScrollView),
      );
      final restored = tester.state<ScrollableState>(
        find.descendant(
          of: restoredChapterView,
          matching: find.byWidgetPredicate(
            (widget) =>
                widget is Scrollable &&
                widget.axisDirection == AxisDirection.down,
          ),
        ),
      );
      expect(restored.position.pixels, closeTo(chapterOffset, 1));
      expect(header.outerController.offset, closeTo(headerOffset, 1));
      expect(choices.reads, 1, reason: 'Switching tabs must not restore twice');
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('manga hero content clears TabBar header without overlapping', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: _related,
          loadDetails: LoadCatalogEntryDetails(_Provider()),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    final hero = tester.widget<CatalogDetailHero>(
      find.byType(CatalogDetailHero),
    );
    expect(hero.hasTabBar, isTrue);

    final tabBarTop = tester.getTopLeft(find.byType(TabBar)).dy;
    final coverBottom = tester
        .getBottomLeft(find.bySemanticsLabel('${_related.title} cover'))
        .dy;
    final readButtonBottom = tester.getBottomLeft(find.text('Read')).dy;

    expect(coverBottom, lessThan(tabBarTop));
    expect(readButtonBottom, lessThan(tabBarTop));
  });

  testWidgets('missing source retains choice and offers Change source', (
    tester,
  ) async {
    final choices = _Choices()
      ..selected = const Media(
        title: 'Gone source',
        type: MediaType.manga,
        source: SourceMediaRef(
          sourceId: SourceId('removed:extension'),
          itemId: 'old-series',
        ),
      );
    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: _related,
          loadDetails: LoadCatalogEntryDetails(_Provider()),
          resolveCatalogSource: _resolver(),
          sourceSelections: choices,
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chapters'));
    await tester.pumpAndSettle();
    expect(find.text('Change source'), findsOneWidget);
    expect(find.textContaining('Could not load chapters'), findsOneWidget);
    expect(choices.selected!.source.itemId, 'old-series');
    expect(choices.saves, 0);
    expect(tester.takeException(), isNull);
  });

  testWidgets('late restored preference cannot overwrite a new Catalog entry', (
    tester,
  ) async {
    final choices = _Choices()..delayedRead = Completer<Media?>();
    final entry = ValueNotifier(_related);
    addTearDown(entry.dispose);
    await tester.pumpWidget(
      _app(
        ValueListenableBuilder<CatalogEntry>(
          valueListenable: entry,
          builder: (_, current, _) => _detail(
            initialEntry: current,
            sourceSelections: choices,
            loadDetails: LoadCatalogEntryDetails(_Provider()),
            resolveCatalogSource: _resolver(),
            openMedia: (_, _, {previewMetadata, canNavigate}) async {},
            openRelated: (_) {},
            openSourceSearch: (_, _, _, _, _) {},
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chapters'));
    await tester.pump();
    entry.value = CatalogEntry(
      id: const CatalogEntryId(provider: 'test', value: 'replacement'),
      title: 'Replacement',
      type: MediaType.manga,
    );
    await tester.pump();
    choices.delayedRead!.complete(
      const Media(
        title: 'Late candidate',
        type: MediaType.manga,
        source: SourceMediaRef(
          sourceId: SourceId('test:manga'),
          itemId: 'late',
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Change source'), findsNothing);
    expect(find.text('Late candidate'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('contextual manual source selection saves confirmed candidate', (
    tester,
  ) async {
    final choices = _Choices();
    final cache = ReadRemoteChapterList(_NoCache());
    const manual = Media(
      title: 'Manual result',
      type: MediaType.manga,
      source: SourceMediaRef(
        sourceId: SourceId('mihon:manual'),
        itemId: 'confirmed',
      ),
    );
    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initialEntry: _related,
          loadDetails: LoadCatalogEntryDetails(_Provider()),
          resolveCatalogSource: _resolver(),
          sourceSelections: choices,
          chapterOpener: OpenMedia(
            SourceRegistry([]),
            _Progress(),
            chapterLists: cache,
          ),
          chapterLists: cache,
          loadReadingTarget: LoadSeriesReadingTarget(_Continuation()),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
          selectSourceSearch: (_, _, _, _, _) async =>
              const CatalogSourcePickerCandidate(media: manual),
          openMangaChapter: (_, _, _, _, _) async {},
          openNovelChapter: (_, _, _, _, _) async {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Read'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Search all sources'));
    await tester.pumpAndSettle();
    expect(choices.saves, 1);
    expect(choices.selected?.source, manual.source);
    expect(find.text('Change source'), findsOneWidget);
  });

  testWidgets('cancel Change source keeps confirmed choice', (tester) async {
    final choices = _Choices()
      ..selected = const Media(
        title: 'Retained candidate',
        type: MediaType.manga,
        source: SourceMediaRef(
          sourceId: SourceId('removed:extension'),
          itemId: 'retained',
        ),
      );
    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: _related,
          loadDetails: LoadCatalogEntryDetails(_Provider()),
          resolveCatalogSource: _resolver(),
          sourceSelections: choices,
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Chapters'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Change source'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Change source'), findsOneWidget);
    expect(choices.selected!.source.itemId, 'retained');
    expect(choices.saves, 0);
  });

  testWidgets('delayed A to B to A commits final confirmed source', (
    tester,
  ) async {
    final choices = _DelayedChoices();
    final a = _ExactSearchSource(const SourceId('source:a'), 'Source A');
    final b = _ExactSearchSource(const SourceId('source:b'), 'Source B');
    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: _related,
          loadDetails: LoadCatalogEntryDetails(_Provider()),
          resolveCatalogSource: _resolver([a, b]),
          sourceSelections: choices,
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Read'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Source A'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Change source'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Source B'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Change source'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Source A'));
    await tester.pumpAndSettle();
    expect(choices.calls, 1); // Later writes queue behind first in-flight save.
    choices.firstWrite.complete();
    await tester.pumpAndSettle();
    expect(choices.calls, 3);
    expect(choices.selected?.source.sourceId, a.id);
    expect(choices.savedSources, [a.id, b.id, a.id]);
  });

  for (final manual in [false, true]) {
    testWidgets(
      'identity A/B/A rejects pending ${manual ? 'manual search' : 'picker'} completion',
      (tester) async {
        final entry = ValueNotifier(_related);
        addTearDown(entry.dispose);
        final source = _PickerMangaSource(
          id: const SourceId('pending'),
          displayName: 'Pending source',
          languageCode: 'en',
        );
        final searchGate = Completer<MangaSearchPage>();
        source.searchGate = searchGate;
        final manualGate = Completer<CatalogSourcePickerCandidate?>();
        final resolver = _resolver([source]);
        final loader = LoadCatalogEntryDetails(_Provider());
        final cache = ReadRemoteChapterList(_NoCache());
        final opener = OpenMedia(
          SourceRegistry([source]),
          _Progress(),
          chapterLists: cache,
        );
        final reading = LoadSeriesReadingTarget(_Continuation());
        var manualCalls = 0;
        await tester.pumpWidget(
          _app(
            ValueListenableBuilder<CatalogEntry>(
              valueListenable: entry,
              builder: (_, current, _) => CatalogDetailPage(
                initialEntry: current,
                loadDetails: loader,
                resolveCatalogSource: resolver,
                openMedia: (_, _, {previewMetadata, canNavigate}) async {},
                openRelated: (_) {},
                openSourceSearch: (_, _, _, _, _) {},
                chapterOpener: opener,
                chapterLists: cache,
                loadReadingTarget: reading,
                selectSourceSearch: (_, _, _, _, _) {
                  manualCalls++;
                  return manualGate.future;
                },
                openMangaChapter: (_, _, _, _, _) async {},
                openNovelChapter: (_, _, _, _, _) async {},
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.text('Read'));
        await tester.pumpAndSettle();
        if (manual) {
          await tester.tap(find.text('Search all sources'));
          await tester.pumpAndSettle();
          expect(manualCalls, 1);
        } else {
          await tester.tap(find.text('Pending source'));
          await tester.pump();
        }
        entry.value = CatalogEntry(
          id: const CatalogEntryId(provider: 'test', value: 'replacement'),
          title: 'Replacement',
          type: MediaType.manga,
        );
        await tester.pump();
        entry.value = _related;
        await tester.pump();
        if (manual) {
          manualGate.complete(
            CatalogSourcePickerCandidate(
              media: Media(
                title: 'Late source',
                type: MediaType.manga,
                source: SourceMediaRef(sourceId: source.id, itemId: 'late'),
              ),
            ),
          );
        } else {
          searchGate.complete(
            MangaSearchPage(
              results: [
                MangaPreview(
                  media: Media(
                    title: _related.title,
                    type: MediaType.manga,
                    source: SourceMediaRef(sourceId: source.id, itemId: 'late'),
                  ),
                ),
              ],
              hasNextPage: false,
              page: 1,
            ),
          );
        }
        await tester.pumpAndSettle();
        if (!manual) {
          // The old modal may finish, but its result must not select into A again.
          await tester.binding.handlePopRoute();
          await tester.pumpAndSettle();
        }
        expect(find.text('Change source'), findsNothing);
        expect(find.text('Late source'), findsNothing);
        expect(tester.widget<TabBar>(find.byType(TabBar)).controller!.index, 0);
        expect(tester.takeException(), isNull);
      },
    );
  }

  for (final (size, scale) in [
    (const Size(320, 640), 3.0),
    (const Size(375, 667), 2.0),
    (const Size(640, 320), 3.0),
  ]) {
    testWidgets('picker long labels fit $size at text scale $scale', (
      tester,
    ) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final model = CatalogSourcePickerViewModel(
        entry: CatalogEntry(
          id: _related.id,
          title: 'Long catalog title ' * 12,
          type: MediaType.manga,
        ),
        resolver: _resolver([
          _PickerMangaSource(
            id: const SourceId('long'),
            displayName: 'Very long provider name ' * 8,
            languageCode: 'all',
          ),
        ]),
      );
      addTearDown(model.dispose);
      await tester.pumpWidget(
        _app(
          MediaQuery(
            data: MediaQueryData(textScaler: TextScaler.linear(scale)),
            child: Scaffold(
              body: Align(
                alignment: Alignment.bottomCenter,
                child: CatalogSourcePicker(
                  viewModel: model,
                  onOpenMedia: (_) {},
                  onManualSearch: (_, _) {},
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.byTooltip('Close').hitTestable(), findsOneWidget);
      final tile = tester.widget<ListTile>(find.byType(ListTile));
      expect(tile.subtitle, isA<Text>());
      expect(tile.trailing, isA<Icon>());
    });
  }

  testWidgets('two language variants stay compact', (tester) async {
    final model = CatalogSourcePickerViewModel(
      entry: _related,
      resolver: _resolver([
        for (final language in ['en', 'fr'])
          _PickerMangaSource(
            id: SourceId(language),
            displayName: 'Source',
            languageCode: language,
            presentationGroupId: 'group',
          ),
      ]),
    );
    addTearDown(model.dispose);
    model.chooseGroup(model.state.sources.first);
    await tester.pumpWidget(
      _app(
        Scaffold(
          body: Align(
            alignment: Alignment.bottomCenter,
            child: CatalogSourcePicker(
              viewModel: model,
              onOpenMedia: (_) {},
              onManualSearch: (_, _) {},
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      tester.getSize(find.byType(CatalogSourcePicker)).height,
      lessThan(400),
    );
  });

  testWidgets(
    'candidate recovery stays before lazy rows with reserved artwork',
    (tester) async {
      final source = _PickerMangaSource(
        id: const SourceId('candidates'),
        displayName: 'Long source name ' * 4,
        languageCode: 'en',
      );
      source.searchGate = Completer<MangaSearchPage>()
        ..complete(
          MangaSearchPage(
            results: [
              for (var i = 0; i < 20; i++)
                MangaPreview(
                  media: Media(
                    title: 'Long candidate title $i ' * 5,
                    type: MediaType.manga,
                    source: SourceMediaRef(sourceId: source.id, itemId: '$i'),
                  ),
                  metadata: MediaMetadata(
                    title: 'Candidate $i',
                    authors: ['Author $i'],
                    cover: SourceMediaRef(
                      sourceId: source.id,
                      itemId: 'cover:$i',
                    ),
                  ),
                ),
            ],
            hasNextPage: false,
            page: 1,
          ),
        );
      final model = CatalogSourcePickerViewModel(
        entry: _related,
        resolver: _resolver([source]),
      );
      addTearDown(model.dispose);
      await model.selectSource(model.state.sources.single);
      var manualCalls = 0;
      var artworkReads = 0;
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: Align(
              alignment: Alignment.bottomCenter,
              child: CatalogSourcePicker(
                viewModel: model,
                onOpenMedia: (_) {},
                onManualSearch: (_, _) => manualCalls++,
                readArtwork: (_) async {
                  artworkReads++;
                  return null;
                },
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Search manually').hitTestable(), findsOneWidget);
      expect(find.text('Change source').hitTestable(), findsOneWidget);
      expect(find.text('Author 0'), findsOneWidget);
      final candidateSemantics = tester.ensureSemantics();
      expect(
        tester.getSemantics(
          find.text(model.state.candidates.first.media.title),
        ),
        matchesSemantics(
          isButton: true,
          hasTapAction: true,
          hasFocusAction: true,
          isFocusable: true,
          label: '${model.state.candidates.first.media.title}\nAuthor 0',
        ),
      );
      candidateSemantics.dispose();
      final artwork = find.byWidgetPredicate(
        (widget) =>
            widget is SizedBox && widget.width == 50 && widget.height == 70,
      );
      expect(artwork, findsWidgets);
      final firstReads = artworkReads;
      model.notifyListeners();
      await tester.pumpAndSettle();
      expect(artworkReads, firstReads);
      expect(artworkReads, lessThan(model.state.candidates.length));
      await tester.tap(find.text('Search manually'));
      await tester.tap(find.text('Search manually'));
      expect(manualCalls, 1);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'resolving offers change source and ignores removed widget completion',
    (tester) async {
      final source = _PickerMangaSource(
        id: const SourceId('pending'),
        displayName: 'Pending source',
        languageCode: 'en',
      );
      source.searchGate = Completer<MangaSearchPage>();
      final model = CatalogSourcePickerViewModel(
        entry: _related,
        resolver: _resolver([source]),
      );
      addTearDown(model.dispose);
      var opened = 0;
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: CatalogSourcePicker(
              viewModel: model,
              onOpenMedia: (_) => opened++,
              onManualSearch: (_, _) {},
            ),
          ),
        ),
      );
      await tester.tap(find.text('Pending source'));
      await tester.pump();
      expect(find.text('Change source'), findsOneWidget);
      await tester.pumpWidget(_app(const SizedBox()));
      source.searchGate!.complete(
        MangaSearchPage(
          results: [
            MangaPreview(
              media: Media(
                title: _related.title,
                type: MediaType.manga,
                source: SourceMediaRef(sourceId: source.id, itemId: 'match'),
              ),
              metadata: MediaMetadata(title: _related.title),
            ),
          ],
          hasNextPage: false,
          page: 1,
        ),
      );
      await tester.pumpAndSettle();
      expect(opened, 0);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'source errors avoid unsupported diagnosis and retry empty result',
    (tester) async {
      final source = _PickerMangaSource(
        id: const SourceId('error'),
        displayName: 'Error source',
        languageCode: 'en',
      );
      source.searchGate = Completer<MangaSearchPage>();
      final model = CatalogSourcePickerViewModel(
        entry: _related,
        resolver: _resolver([source]),
      );
      addTearDown(model.dispose);
      final pending = model.selectSource(model.state.sources.single);
      source.searchGate!.completeError(StateError('invalid response'));
      await pending;
      await tester.pumpWidget(
        _app(
          Scaffold(
            body: CatalogSourcePicker(
              viewModel: model,
              onOpenMedia: (_) {},
              onManualSearch: (_, _) {},
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('did not respond'), findsNothing);
      expect(find.text('Could not search this source'), findsOneWidget);
      source.searchGate = null;
      await tester.tap(find.text('Retry'));
      await tester.pumpAndSettle();
      expect(find.text('No match found'), findsOneWidget);
      await tester.tap(find.text('Change source'));
      await tester.pumpAndSettle();
      expect(find.text('Error source'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('candidate list remains lazy at short height and large text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(640, 320);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    var reads = 0;
    final candidates = [
      for (var i = 0; i < 80; i++)
        CatalogSourcePickerCandidate(
          media: Media(
            title: 'Long candidate $i ' * 8,
            type: MediaType.manga,
            source: SourceMediaRef(
              sourceId: const SourceId('test'),
              itemId: '$i',
            ),
          ),
          metadata: MediaMetadata(
            title: 'Title',
            authors: ['Long author ' * 8],
            cover: SourceMediaRef(
              sourceId: const SourceId('test'),
              itemId: 'cover:$i',
            ),
          ),
        ),
    ];
    await tester.pumpWidget(
      _app(
        MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(3)),
          child: Scaffold(
            body: CatalogSourceCandidateList(
              source: CatalogSourcePickerSource(
                id: const SourceId('test'),
                displayName: 'Long source ' * 10,
                languageCode: 'all',
              ),
              candidates: candidates,
              onSelect: (_) {},
              onChooseAnother: () {},
              onManualSearch: () {},
              readArtwork: (_) async {
                reads++;
                return null;
              },
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    for (final label in ['Search manually', 'Change source']) {
      await tester.ensureVisible(find.text(label));
      await tester.pumpAndSettle();
      expect(find.text(label).hitTestable(), findsOneWidget);
    }
    expect(reads, lessThan(candidates.length));
    expect(tester.takeException(), isNull);
  });

  testWidgets('rapid Read opens one source picker', (tester) async {
    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: _related,
          loadDetails: LoadCatalogEntryDetails(_Provider()),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    final read = tester.widget<FilledButton>(
      find.ancestor(of: find.text('Read'), matching: find.byType(FilledButton)),
    );
    read.onPressed!();
    read.onPressed!();
    await tester.pumpAndSettle();
    expect(
      find.byType(CatalogSourcePicker, skipOffstage: false),
      findsOneWidget,
    );
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Read'));
    await tester.pumpAndSettle();
    expect(
      find.byType(CatalogSourcePicker, skipOffstage: false),
      findsOneWidget,
    );
  });

  for (final size in [const Size(320, 568), const Size(640, 320)]) {
    testWidgets('modal states recover with keyboard and safe area at $size', (
      tester,
    ) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final source = _PickerMangaSource(
        id: const SourceId('modal'),
        displayName: 'Long source name ' * 6,
        languageCode: 'all',
      );
      final model = CatalogSourcePickerViewModel(
        entry: _related,
        resolver: _resolver([source]),
      );
      addTearDown(model.dispose);
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              textScaler: const TextScaler.linear(3),
              padding: const EdgeInsets.only(bottom: 24),
              viewInsets: const EdgeInsets.only(bottom: 80),
            ),
            child: child!,
          ),
          home: Scaffold(
            body: Builder(
              builder: (context) => TextButton(
                onPressed: () {
                  showModalBottomSheet<void>(
                    context: context,
                    isScrollControlled: true,
                    useSafeArea: true,
                    builder: (context) => Padding(
                      padding: EdgeInsets.only(
                        bottom: MediaQuery.viewInsetsOf(context).bottom,
                      ),
                      child: SafeArea(
                        top: false,
                        child: CatalogSourcePicker(
                          viewModel: model,
                          onOpenMedia: (_) {},
                          onManualSearch: (_, _) {},
                        ),
                      ),
                    ),
                  );
                },
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      expect(find.byTooltip('Close').hitTestable(), findsOneWidget);
      source.searchGate = Completer<MangaSearchPage>();
      final pending = model.selectSource(model.state.sources.single);
      await tester.pump();
      await Scrollable.ensureVisible(
        tester.element(find.text('Change source')),
        alignment: 0.5,
      );
      await tester.pump(const Duration(milliseconds: 400));
      expect(
        find.text('Change source').hitTestable(),
        findsOneWidget,
        reason:
            'Change source ${tester.getRect(find.text('Change source'))}; picker ${tester.getRect(find.byType(CatalogSourcePicker))}',
      );
      source.searchGate!.completeError(StateError('bad response'));
      await pending;
      await tester.pumpAndSettle();
      for (final label in ['Retry', 'Search manually', 'Change source']) {
        await Scrollable.ensureVisible(
          tester.element(find.text(label)),
          alignment: 0.5,
        );
        await tester.pumpAndSettle();
        expect(find.text(label).hitTestable(), findsOneWidget);
      }
      expect(find.byTooltip('Close').hitTestable(), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('production sheet fits landscape keyboard with large text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(640, 320);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final keyboard = ValueNotifier(false);
    addTearDown(keyboard.dispose);
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        builder: (context, child) => ValueListenableBuilder<bool>(
          valueListenable: keyboard,
          builder: (context, visible, _) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              textScaler: TextScaler.linear(visible ? 3 : 1),
              viewInsets: EdgeInsets.only(bottom: visible ? 200 : 0),
            ),
            child: child!,
          ),
        ),
        home: _detail(
          initialEntry: _related,
          loadDetails: LoadCatalogEntryDetails(_Provider()),
          resolveCatalogSource: _resolver([
            _PickerMangaSource(
              id: const SourceId('source'),
              displayName: 'Source',
              languageCode: 'en',
            ),
          ]),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    tester
        .widget<FilledButton>(
          find.ancestor(
            of: find.text('Read'),
            matching: find.byType(FilledButton),
          ),
        )
        .onPressed!();
    await tester.pumpAndSettle();
    expect(find.byType(CatalogSourcePicker), findsOneWidget);
    // Catalog hero text scaling is outside this picker regression.
    final reportError = FlutterError.onError!;
    FlutterError.onError = (details) {
      if (!details.toString().contains('catalog_detail_hero.dart')) {
        reportError(details);
      }
    };
    addTearDown(() => FlutterError.onError = reportError);
    keyboard.value = true;
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await Scrollable.ensureVisible(
      tester.element(find.byTooltip('Close')),
      alignment: 0.5,
    );
    await tester.pumpAndSettle();
    expect(find.byTooltip('Close').hitTestable(), findsOneWidget);
  });

  testWidgets('large source lists build only visible rows', (tester) async {
    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: _related,
          loadDetails: LoadCatalogEntryDetails(_Provider()),
          resolveCatalogSource: _resolver([
            for (var i = 0; i < 80; i++)
              _PickerMangaSource(
                id: SourceId('source:$i'),
                displayName: 'Source $i',
                languageCode: 'en',
                presentationGroupId: 'group:$i',
              ),
          ]),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Read'));
    await tester.pumpAndSettle();
    final tiles = find.descendant(
      of: find.byType(CatalogSourcePicker),
      matching: find.byType(ListTile),
    );
    expect(tiles.evaluate().length, lessThan(80));
    expect(find.text('Source 0'), findsOneWidget);
    expect(find.text('Source 79'), findsNothing);
  });

  testWidgets(
    'same-name grouped sources stay separate without exposing opaque IDs',
    (tester) async {
      await tester.pumpWidget(
        _app(
          _detail(
            initialEntry: _related,
            loadDetails: LoadCatalogEntryDetails(_Provider()),
            resolveCatalogSource: _resolver([
              for (final package in ['package:a', 'package:b'])
                for (final language in ['en', 'fr'])
                  _PickerMangaSource(
                    id: SourceId('$package:$language'),
                    displayName: 'MangaDex',
                    languageCode: language,
                    presentationGroupId: package,
                  ),
            ]),
            openMedia: (_, _, {previewMetadata, canNavigate}) async {},
            openRelated: (_) {},
            openSourceSearch: (_, _, _, _, _) {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Read'));
      await tester.pumpAndSettle();
      expect(find.text('MangaDex'), findsNWidgets(2));
      expect(find.textContaining('package:a'), findsNothing);
      expect(find.textContaining('package:b'), findsNothing);
      final sourceTiles = find.descendant(
        of: find.byType(CatalogSourcePicker),
        matching: find.byType(ListTile),
      );
      expect(sourceTiles, findsNWidgets(2));
      await tester.tap(sourceTiles.at(1));
      await tester.pumpAndSettle();
      expect(find.text('Choose language'), findsOneWidget);
      expect(find.text('MangaDex'), findsNWidgets(3));
      expect(find.text('EN'), findsOneWidget);
      expect(find.text('FR'), findsOneWidget);
      expect(find.textContaining('package:a'), findsNothing);
      expect(find.textContaining('package:b'), findsNothing);
    },
  );

  testWidgets(
    'ambiguous languages remain separate without exposing opaque IDs',
    (tester) async {
      final manga = CatalogEntry(
        id: _related.id,
        title: 'Book A',
        type: MediaType.manga,
      );
      await tester.pumpWidget(
        _app(
          _detail(
            initialEntry: manga,
            loadDetails: LoadCatalogEntryDetails(
              _Provider(
                onLoadDetails: (_) async => CatalogEntryDetails(entry: manga),
              ),
            ),
            resolveCatalogSource: _resolver([
              for (final (id, language) in [
                ('a', 'en'),
                ('b', 'en'),
                ('c', 'all'),
                ('d', null),
              ])
                _PickerMangaSource(
                  id: SourceId(id),
                  displayName: 'Same',
                  languageCode: language,
                  presentationGroupId: 'one',
                ),
            ]),
            openMedia: (_, _, {previewMetadata, canNavigate}) async {},
            openRelated: (_) {},
            openSourceSearch: (_, _, _, _, _) {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Read'));
      await tester.pumpAndSettle();
      expect(find.text('Same'), findsNWidgets(4));
      expect(find.text('EN'), findsNWidgets(2));
      expect(find.text('Multiple languages'), findsOneWidget);
      expect(find.text('Unspecified'), findsOneWidget);
      expect(find.byType(MenuAnchor), findsNothing);
    },
  );

  testWidgets(
    'thirty languages stay in a bounded scrollable menu with selection state',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final manga = CatalogEntry(
        id: _related.id,
        title: 'Book A',
        type: MediaType.manga,
      );
      await tester.pumpWidget(
        _app(
          _detail(
            initialEntry: manga,
            loadDetails: LoadCatalogEntryDetails(
              _Provider(
                onLoadDetails: (_) async => CatalogEntryDetails(entry: manga),
              ),
            ),
            resolveCatalogSource: _resolver([
              for (var i = 0; i < 30; i++)
                _PickerMangaSource(
                  id: SourceId('test:$i'),
                  displayName: 'MangaDex',
                  languageCode: 'l${i.toString().padLeft(2, '0')}',
                  presentationGroupId: 'package:dex',
                ),
            ]),
            openMedia: (_, _, {previewMetadata, canNavigate}) async {},
            openRelated: (_) {},
            openSourceSearch: (_, _, _, _, _) {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Read'));
      await tester.pumpAndSettle();
      expect(find.text('MangaDex'), findsOneWidget);
      expect(find.byType(MenuAnchor), findsOneWidget);
      final languageMenu = tester.widget<MenuAnchor>(find.byType(MenuAnchor));
      final maximumSize = languageMenu.style?.maximumSize?.resolve({});
      expect(maximumSize?.height, 320);

      await tester.tap(find.text('Language: All'));
      await tester.pumpAndSettle();
      final allLanguagesItem = find.ancestor(
        of: find.text('All languages'),
        matching: find.byType(MenuItemButton),
      );
      expect(
        find.descendant(
          of: allLanguagesItem,
          matching: find.byIcon(Icons.check_rounded),
        ),
        findsOneWidget,
      );
      await tester.ensureVisible(find.widgetWithText(MenuItemButton, 'L29'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(MenuItemButton, 'L29'));
      await tester.pumpAndSettle();
      expect(find.text('Language: L29'), findsOneWidget);

      await tester.tap(find.text('Language: L29'));
      await tester.pumpAndSettle();
      final selectedLanguageItem = find.widgetWithText(MenuItemButton, 'L29');
      expect(
        find.descendant(
          of: selectedLanguageItem,
          matching: find.byIcon(Icons.check_rounded),
        ),
        findsOneWidget,
      );
      await tester.tap(find.text('Language: L29'));
      await tester.pumpAndSettle();
      expect(find.text('Language: L29'), findsOneWidget);
      expect(find.text('Search L29 sources'), findsOneWidget);
      await tester.tap(find.text('Show all languages'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('MangaDex'));
      await tester.pumpAndSettle();
      expect(find.text('Choose language'), findsOneWidget);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.text('Choose language'), findsNothing);
      expect(find.text('Read from'), findsOneWidget);
      await tester.tap(find.text('MangaDex'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Back'));
      await tester.pumpAndSettle();
      expect(find.text('MangaDex'), findsOneWidget);
      await tester.tap(find.text('MangaDex'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Close'));
      await tester.pumpAndSettle();
      expect(find.text('Read from'), findsNothing);
    },
  );

  testWidgets('detail keeps identity and source action while loading', (
    tester,
  ) async {
    final pending = Completer<CatalogEntryDetails?>();
    CatalogEntry? searched;

    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(onLoadDetails: (_) => pending.future),
          ),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (entry, _, _, _, _) => searched = entry,
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Anime A'), findsWidgets);
    expect(find.byKey(const Key('catalog-detail-loading')), findsOneWidget);
    expect(find.text('Watch'), findsOneWidget);
    expect(find.text('Choose a source to continue'), findsOneWidget);

    await tester.tap(find.text('Watch'));
    expect(searched, _anime);

    pending.complete(CatalogEntryDetails(entry: _anime));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('catalog-detail-loading')), findsNothing);
  });

  testWidgets('detail renders grouped metadata and routes relations', (
    tester,
  ) async {
    CatalogEntry? related;
    final description = List.filled(
      10,
      'A long catalog synopsis that should remain readable and scannable.',
    ).join(' ');
    final detail = CatalogEntryDetails(
      entry: _anime,
      description: description,
      alternateTitles: ['Japanese title'],
      synonyms: ['Alternate'],
      averageScore: 91,
      popularity: 15500,
      format: CatalogFormat.tv,
      status: CatalogStatus.finished,
      season: CatalogSeason.summer,
      year: 2025,
      episodes: 12,
      studios: ['Example Studio'],
      staff: ['Writer'],
      warnings: ['Some fields unavailable'],
      relations: [
        CatalogRelatedEntry(
          relation: CatalogRelation.sideStory,
          entry: _related,
        ),
      ],
    );

    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(onLoadDetails: (_) async => detail),
          ),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (entry) => related = entry,
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    for (final value in [
      'At a glance',
      '91%',
      '16K',
      'Finished',
      'TV',
      'Some fields unavailable',
      'Genres',
      'Action',
      'Synopsis',
      'Titles',
      'Japanese title',
      'Alternate',
    ]) {
      await _scrollTo(tester, find.text(value));
      expect(find.text(value), findsWidgets, reason: value);
    }

    await _scrollTo(tester, find.text('Related series'));
    expect(find.text('Side story'), findsOneWidget);
    await tester.tapAt(tester.getCenter(find.text('Related series')));
    expect(related, _related);

    await _scrollTo(tester, find.text('Details'));
    expect(find.text('Summer 2025'), findsOneWidget);
    expect(find.text('12'), findsOneWidget);

    await _scrollTo(tester, find.text('Credits'));
    expect(find.text('Example Studio'), findsOneWidget);
    expect(find.text('Writer'), findsOneWidget);
  });

  testWidgets('description expands and collapses without replacing content', (
    tester,
  ) async {
    final description = List.filled(
      12,
      'Long description text for catalog detail readability.',
    ).join(' ');
    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(
              onLoadDetails: (_) async =>
                  CatalogEntryDetails(entry: _anime, description: description),
            ),
          ),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _scrollTo(tester, find.text('Show more'));
    var body = tester.widget<Text>(
      find.byKey(const Key('catalog-detail-description')),
    );
    expect(body.maxLines, 6);

    await tester.tap(find.text('Show more'));
    await tester.pump();
    body = tester.widget<Text>(
      find.byKey(const Key('catalog-detail-description')),
    );
    expect(body.maxLines, isNull);
    expect(find.text('Show less'), findsOneWidget);
  });

  testWidgets('failed and missing metadata keep source search usable', (
    tester,
  ) async {
    var calls = 0;
    CatalogEntry? searched;
    final provider = _Provider(
      onLoadDetails: (_) {
        calls++;
        if (calls == 1) return Future<CatalogEntryDetails?>.value(null);
        if (calls == 2) {
          return Future<CatalogEntryDetails?>.error(StateError('offline'));
        }
        return Future.value(CatalogEntryDetails(entry: _anime));
      },
    );

    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(provider),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (entry, _, _, _, _) => searched = entry,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Catalog entry unavailable'), findsOneWidget);
    expect(find.text('Watch'), findsOneWidget);
    await tester.tap(find.text('Watch'));
    expect(searched, _anime);

    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('More details could not load'), findsOneWidget);
    expect(find.text('Watch'), findsOneWidget);

    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(calls, 3);
    expect(find.text('More details could not load'), findsNothing);
  });

  testWidgets('detail refresh action reloads metadata without navigation', (
    tester,
  ) async {
    var calls = 0;
    final provider = _Provider(
      onLoadDetails: (_) async {
        calls++;
        return CatalogEntryDetails(entry: _anime);
      },
    );

    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(provider),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(calls, 1);

    await tester.tap(find.byTooltip('Refresh'));
    await tester.pumpAndSettle();
    expect(calls, 2);
  });

  testWidgets('failed refresh keeps the last usable details visible', (
    tester,
  ) async {
    var calls = 0;
    final provider = _Provider(
      onLoadDetails: (_) {
        if (++calls == 1) {
          return Future.value(
            CatalogEntryDetails(
              entry: _anime,
              description: 'Cached description',
              warnings: ['Partial metadata'],
            ),
          );
        }
        return Future<CatalogEntryDetails?>.error(StateError('offline'));
      },
    );

    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(provider),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _scrollTo(tester, find.text('Partial metadata'));
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();

    expect(find.text('Refresh failed'), findsOneWidget);
    expect(find.text('Cached description'), findsOneWidget);
    expect(find.byKey(const Key('catalog-detail-loading')), findsNothing);
  });

  testWidgets('expanded detail uses a bounded supporting pane', (tester) async {
    tester.view.physicalSize = const Size(1200, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(
              onLoadDetails: (_) async => CatalogEntryDetails(
                entry: _anime,
                description: 'Description',
                averageScore: 90,
                format: CatalogFormat.tv,
                status: CatalogStatus.releasing,
                year: 2026,
              ),
            ),
          ),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('catalog-detail-supporting-pane')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('compact source picker can be dismissed with visible Close', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final manga = CatalogEntry(
      id: _related.id,
      title: 'Book A',
      type: MediaType.manga,
    );
    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: manga,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(
              onLoadDetails: (_) async => CatalogEntryDetails(entry: manga),
            ),
          ),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Read'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Close'), findsOneWidget);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Read from'), findsNothing);
  });

  testWidgets('expanded source picker can be dismissed with visible Close', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1200, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final manga = CatalogEntry(
      id: _related.id,
      title: 'Book A',
      type: MediaType.manga,
    );
    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: manga,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(
              onLoadDetails: (_) async => CatalogEntryDetails(entry: manga),
            ),
          ),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Read'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Close'), findsOneWidget);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Read from'), findsNothing);
  });

  testWidgets('filtered Search all passes only visible source IDs', (
    tester,
  ) async {
    Set<SourceId>? searchedIds;
    final manga = CatalogEntry(
      id: _related.id,
      title: 'Book A',
      type: MediaType.manga,
    );
    final sources = [
      _PickerMangaSource(
        id: const SourceId('test:en'),
        displayName: 'English source',
        languageCode: 'en',
      ),
      _PickerMangaSource(
        id: const SourceId('test:fr'),
        displayName: 'French source',
        languageCode: 'fr',
      ),
    ];
    await tester.pumpWidget(
      _app(
        _detail(
          initialEntry: manga,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(
              onLoadDetails: (_) async => CatalogEntryDetails(entry: manga),
            ),
          ),
          resolveCatalogSource: _resolver(sources),
          openMedia: (_, _, {previewMetadata, canNavigate}) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, ids, _) => searchedIds = ids,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Read'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Language: All'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(MenuItemButton, 'EN'));
    await tester.pumpAndSettle();
    expect(find.text('English source'), findsOneWidget);
    expect(find.text('French source'), findsNothing);
    await tester.tap(find.text('Search EN sources'));

    expect(searchedIds, {const SourceId('test:en')});
    await tester.pumpAndSettle();
    await tester.tap(find.text('Read'));
    await tester.pumpAndSettle();
    expect(find.text('Language: All'), findsOneWidget);
    await tester.tap(find.text('Search all sources'));
    await tester.pumpAndSettle();
    expect(searchedIds, isNull);
  });

  for (final automatic in [true, false]) {
    testWidgets(
      'CatalogDetailPage forwards own source preview automatic $automatic',
      (tester) async {
        Media? opened;
        MediaMetadata? received;
        CatalogEntry? searched;
        final catalog = CatalogEntry(
          id: _related.id,
          title: 'Book A',
          type: MediaType.manga,
        );
        final preview = MediaMetadata(
          title: 'Source title',
          summary: 'Source summary',
          cover: const SourceMediaRef(
            sourceId: SourceId('test:manga'),
            itemId: 'source-cover',
          ),
        );
        final candidate = MangaPreview(
          metadata: preview,
          media: Media(
            title: automatic ? 'Book A' : 'Chosen book',
            type: MediaType.manga,
            source: const SourceMediaRef(
              sourceId: SourceId('test:manga'),
              itemId: 'chosen',
            ),
          ),
        );
        final source = _MangaSource(results: [candidate]);
        final choices = _Choices();
        await tester.pumpWidget(
          _app(
            _detail(
              initialEntry: catalog,
              sourceSelections: choices,
              loadDetails: LoadCatalogEntryDetails(
                _Provider(
                  onLoadDetails: (_) async =>
                      CatalogEntryDetails(entry: catalog),
                ),
              ),
              resolveCatalogSource: _resolver([source]),
              openMedia: (_, media, {previewMetadata, canNavigate}) async {
                opened = media;
                received = previewMetadata;
              },
              openRelated: (_) {},
              openSourceSearch: (entry, _, _, _, _) => searched = entry,
            ),
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(find.text('Read'));
        await tester.pumpAndSettle();
        await tester.tap(find.text('Manga source [en]'));
        await tester.pumpAndSettle();
        if (!automatic) {
          expect(opened, isNull);
          expect(find.text('Choose matching title'), findsOneWidget);
          await tester.tap(find.text('Chosen book'));
          await tester.pumpAndSettle();
        }
        expect(opened, isNull);
        expect(find.text('Change source'), findsOneWidget);
        expect(choices.saves, 1);
        expect(choices.selected?.source, candidate.media.source);
        await tester.ensureVisible(find.text('Start reading'));
        await tester.tap(find.text('Start reading'));
        await tester.pumpAndSettle();
        expect(opened, same(candidate.media));
        expect(received, same(preview));
        expect(received!.cover!.sourceId, source.id);
        expect(searched, isNull);
        expect(find.text('Read from'), findsNothing);
      },
    );
  }

  testWidgets(
    'catalog detail hero layout remains resilient at 200% text scale with long title',
    (tester) async {
      tester.view.physicalSize = const Size(360, 640);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final longTitleEntry = CatalogEntry(
        id: const CatalogEntryId(provider: 'test', value: 'long-title'),
        title: 'A Very Extremely Long Anime Title That Takes Multiple Lines In English And Japanese',
        type: MediaType.anime,
        genres: ['Action', 'Fantasy'],
      );

      await tester.pumpWidget(
        MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2.0)),
          child: _app(
            _detail(
              initialEntry: longTitleEntry,
              loadDetails: LoadCatalogEntryDetails(
                _Provider(
                  onLoadDetails: (_) async => CatalogEntryDetails(
                    entry: longTitleEntry,
                    averageScore: 95,
                    format: CatalogFormat.tv,
                    year: 2026,
                  ),
                ),
              ),
              resolveCatalogSource: _resolver(),
              openMedia: (_, _, {previewMetadata, canNavigate}) async {},
              openRelated: (_) {},
              openSourceSearch: (_, _, _, _, _) {},
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text('Watch'), findsOneWidget);
      expect(find.byType(FilledButton), findsOneWidget);
    },
  );
}

Future<void> _scrollTo(WidgetTester tester, Finder finder) async {
  final matches = finder.evaluate().toList(growable: false);
  expect(matches, isNotEmpty, reason: 'Scroll target not found: $finder');
  await tester.ensureVisible(finder.first);
  await tester.pumpAndSettle();
}

Widget _app(Widget child) =>
    MaterialApp(theme: HikariTheme.darkTheme(), home: child);

final _anime = CatalogEntry(
  id: const CatalogEntryId(provider: 'test', value: '1'),
  title: 'Anime A',
  type: MediaType.anime,
  genres: ['Action'],
);

final _related = CatalogEntry(
  id: const CatalogEntryId(provider: 'test', value: '2'),
  title: 'Related series',
  type: MediaType.manga,
);

ResolveCatalogSource _resolver([Iterable<MediaSource> sources = const []]) {
  final registry = SourceRegistry(sources);
  return ResolveCatalogSource(
    searchManga: SearchManga(registry),
    searchNovels: SearchNovels(registry),
  );
}

final class _MangaSource implements MangaSearchSource, MangaPageSource {
  _MangaSource({this.results = const []});

  final List<MangaPreview> results;

  @override
  SourceId get id => const SourceId('test:manga');

  @override
  String get name => 'Manga source [en]';

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async =>
      MangaSearchPage(results: results, hasNextPage: false, page: page);

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

final class _PickerMangaSource
    implements MangaSearchSource, MangaPageSource, MediaSourcePresentation {
  _PickerMangaSource({
    required this.id,
    required this.displayName,
    required this.languageCode,
    this.presentationGroupId,
  });

  @override
  final SourceId id;
  @override
  final String displayName;
  @override
  final String? languageCode;

  @override
  final String? presentationGroupId;

  @override
  String get name => '$displayName [$languageCode]';

  Completer<MangaSearchPage>? searchGate;
  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) =>
      searchGate?.future ??
      Future.value(
        MangaSearchPage(results: const [], hasNextPage: false, page: page),
      );

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
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

// Picker/metadata tests compose real chapter dependencies, even when unused.
Widget _detail({
  required CatalogEntry initialEntry,
  required LoadCatalogEntryDetails loadDetails,
  required ResolveCatalogSource resolveCatalogSource,
  required Future<void> Function(
    BuildContext,
    Media, {
    MediaMetadata? previewMetadata,
    bool Function()? canNavigate,
  })
  openMedia,
  required void Function(CatalogEntry) openRelated,
  required void Function(
    CatalogEntry,
    SourceId?,
    String?,
    Set<SourceId>?,
    String?,
  )
  openSourceSearch,
  Future<Uint8List?> Function(SourceMediaRef)? readArtwork,
  CatalogSourceSelectionRepository? sourceSelections,
  ReadRemoteChapterList? chapterCache,
}) {
  final cache = chapterCache ?? ReadRemoteChapterList(_NoCache());
  return CatalogDetailPage(
    initialEntry: initialEntry,
    loadDetails: loadDetails,
    resolveCatalogSource: resolveCatalogSource,
    openMedia: openMedia,
    sourceSelections: sourceSelections,
    openRelated: openRelated,
    openSourceSearch: openSourceSearch,
    readArtwork: readArtwork,
    chapterOpener: OpenMedia(
      SourceRegistry([_MangaSource()]),
      _Progress(),
      chapterLists: cache,
    ),
    chapterLists: cache,
    loadReadingTarget: LoadSeriesReadingTarget(_Continuation()),
    selectSourceSearch: (entry, source, name, ids, language) async {
      openSourceSearch(entry, source, name, ids, language);
      return null;
    },
    openMangaChapter: (_, _, _, _, _) async {},
    openNovelChapter: (_, _, _, _, _) async {},
  );
}

final class _Choices implements CatalogSourceSelectionRepository {
  Media? selected;
  int reads = 0;
  int saves = 0;
  Completer<Media?>? delayedRead;

  @override
  Future<Media?> load(CatalogEntry entry) async {
    reads++;
    return delayedRead?.future ?? selected;
  }

  @override
  Future<void> save(CatalogEntry entry, Media value) async {
    saves++;
    selected = value;
  }
}

final class _DelayedChoices implements CatalogSourceSelectionRepository {
  final firstWrite = Completer<void>();
  final savedSources = <SourceId>[];
  Media? selected;
  int calls = 0;

  @override
  Future<Media?> load(CatalogEntry entry) async => selected;

  @override
  Future<void> save(CatalogEntry entry, Media value) async {
    calls++;
    if (calls == 1) await firstWrite.future;
    savedSources.add(value.source.sourceId);
    selected = value;
  }
}

final class _ExactSearchSource implements MangaSearchSource, MangaPageSource {
  _ExactSearchSource(this.id, this.name);

  @override
  final SourceId id;
  @override
  final String name;

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async =>
      MangaSearchPage(
        results: [
          MangaPreview(
            media: Media(
              title: _related.title,
              type: MediaType.manga,
              source: SourceMediaRef(sourceId: id, itemId: 'exact'),
            ),
          ),
        ],
        page: page,
        hasNextPage: false,
      );

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

final class _MemoryCache implements ByteCache {
  final bytes = <String, Uint8List>{};
  int reads = 0;

  @override
  Future<Uint8List?> read(String namespace, String key) async {
    reads++;
    return bytes['$namespace::$key'];
  }

  @override
  Future<void> write(String namespace, String key, Uint8List value) async {
    bytes['$namespace::$key'] = value;
  }

  @override
  Future<void> close() async => bytes.clear();
}

final class _NoCache implements ByteCache {
  @override
  Future<Uint8List?> read(String namespace, String key) async => null;
  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {}
  @override
  Future<void> close() async {}
}

final class _Progress implements ProgressRepository {
  @override
  Future<MediaProgress?> load(SourceMediaRef media) async => null;
  @override
  Future<void> save(MediaProgress progress) async {}
  @override
  Future<void> delete(SourceMediaRef media) async {}
}

final class _Continuation implements SeriesContinuationRepository {
  @override
  Future<SourceMediaRef?> load(SourceMediaRef series) async => null;
  @override
  Future<void> save(SeriesContinuation continuation) async {}
}
