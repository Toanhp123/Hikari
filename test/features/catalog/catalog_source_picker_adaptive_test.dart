import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/application/catalog/resolve_catalog_source.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/media/read_remote_chapter_list.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/catalog/catalog_source_selection.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/series_continuation.dart';
import 'package:hikari/features/catalog/catalog_detail_page.dart';
import 'package:hikari/features/catalog/catalog_source_picker_view_model.dart';
import 'package:hikari/features/catalog/presentation/catalog_source_selection.dart';
import 'package:hikari/features/catalog/widgets/catalog_source_picker.dart';

void main() {
  testWidgets('resolver replacement rejects pending picker identity', (
    tester,
  ) async {
    final fixture = _Fixture();
    await fixture.mount(tester, 375);
    await fixture.open(tester);
    final picker = tester.widget<CatalogSourcePicker>(
      find.byType(CatalogSourcePicker),
    );
    fixture.resolver = fixture.makeResolver();
    fixture.revision.value++;
    await tester.pump();
    picker.controller.complete(CatalogCandidateSelection(fixture.candidate));
    await tester.pumpAndSettle();
    expect(fixture.choices.saves, 0);
    expect(fixture.readerCalls, 0);
  });

  for (final width in <double>[375, 1440]) {
    testWidgets(
      'production picker $width preserves language and route on resize',
      (tester) async {
        final fixture = _Fixture();
        await fixture.mount(tester, width);
        await fixture.open(tester);
        final picker = tester.widget<CatalogSourcePicker>(
          find.byType(CatalogSourcePicker),
        );
        final route = ModalRoute.of(
          tester.element(find.byType(CatalogSourcePicker)),
        );
        expect(route!.navigator, fixture.root.currentState);
        expect(find.byType(width < 840 ? BottomSheet : Dialog), findsOneWidget);
        await tester.tap(find.text('Language: All'));
        await tester.pumpAndSettle();
        await tester.tap(find.widgetWithText(MenuItemButton, 'EN'));
        await tester.pumpAndSettle();
        for (final next in <double>[
          599,
          600,
          601,
          839,
          840,
          841,
          1200,
          1440,
          375,
        ]) {
          tester.view.physicalSize = Size(next, 800);
          await tester.pumpAndSettle();
          final current = tester.widget<CatalogSourcePicker>(
            find.byType(CatalogSourcePicker),
          );
          expect(current.viewModel, same(picker.viewModel));
          expect(current.controller, same(picker.controller));
          expect(current.viewModel.state.selectedLanguage, 'en');
          expect(
            ModalRoute.of(tester.element(find.byType(CatalogSourcePicker))),
            same(route),
          );
        }
        await tester.tap(find.text('Search EN sources'));
        await tester.pumpAndSettle();
        expect(fixture.manualIds, {const SourceId('fixture:en')});
        expect(fixture.manualLanguage, 'en');
        expect(fixture.choices.saves, 0);
        expect(fixture.readerCalls, 0);
        expect(fixture.nested.currentState!.canPop(), isFalse);
        expect(tester.takeException(), isNull);
      },
    );

    for (final scale in <double>[1, 2, 3]) {
      testWidgets(
        'production picker $width at ${scale}x handles short IME RTL with unscaled caller',
        (tester) async {
          final fixture = _Fixture();
          await fixture.mount(tester, width, scale: scale, stress: true);
          await fixture.open(tester);
          tester.view.physicalSize = Size(width, 440);
          tester.view.viewInsets = const FakeViewPadding(bottom: 120);
          addTearDown(tester.view.resetViewInsets);
          await tester.pumpAndSettle();
          final pickerContext = tester.element(
            find.byType(CatalogSourcePicker),
          );
          expect(MediaQuery.textScalerOf(pickerContext).scale(10), scale * 10);
          expect(MediaQuery.disableAnimationsOf(pickerContext), isTrue);
          expect(Directionality.of(pickerContext), TextDirection.rtl);
          expect(tester.takeException(), isNull);
          await tester.ensureVisible(find.byTooltip('Close'));
          await tester.tap(find.byTooltip('Close'));
          await tester.pumpAndSettle();
          expect(find.byType(CatalogSourcePicker), findsNothing);
          expect(fixture.choices.saves, 0);
          expect(tester.takeException(), isNull);
        },
      );
    }

    testWidgets(
      'production picker $width consumes language Back before dismissal',
      (tester) async {
        final fixture = _Fixture();
        await fixture.mount(tester, width);
        await fixture.open(tester);
        await tester.tap(find.text('Provider'));
        await tester.pumpAndSettle();
        expect(find.text('Choose language'), findsOneWidget);
        final picker = tester.widget<CatalogSourcePicker>(
          find.byType(CatalogSourcePicker),
        );
        expect(picker.controller.canDismiss, isFalse);
        await fixture.root.currentState!.maybePop();
        await tester.pumpAndSettle();
        expect(find.byType(CatalogSourcePicker), findsOneWidget);
        expect(find.text('Choose language'), findsNothing);
        expect(picker.controller.canDismiss, isTrue);
        await fixture.root.currentState!.maybePop();
        await tester.pumpAndSettle();
        expect(find.byType(CatalogSourcePicker), findsNothing);
        expect(fixture.readerCalls, 0);
        expect(fixture.choices.saves, 0);
      },
    );

    testWidgets(
      'production picker $width exact candidate commits once without reader autoplay',
      (tester) async {
        final fixture = _Fixture();
        await fixture.mount(tester, width);
        await fixture.open(tester);
        final picker = tester.widget<CatalogSourcePicker>(
          find.byType(CatalogSourcePicker),
        );
        await picker.viewModel.selectSource(
          picker.viewModel.state.sources.first,
        );
        // Direct VM resolution returns the automatic candidate; UI callback is
        // exercised separately by source-tile integration regressions.
        await tester.pump();
        final selection = CatalogCandidateSelection(fixture.candidate);
        expect(picker.controller.complete(selection), isTrue);
        expect(picker.controller.complete(selection), isFalse);
        await tester.pumpAndSettle();
        expect(
          fixture.choices.selected!.source,
          fixture.candidate.media.source,
        );
        expect(fixture.choices.saves, 1);
        expect(fixture.readerCalls, 0);
        expect(find.byType(CatalogSourcePicker), findsNothing);
        expect(fixture.nested.currentState!.canPop(), isFalse);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'production picker $width Escape closes open language menu before modal',
      (tester) async {
        final fixture = _Fixture();
        await fixture.mount(tester, width);
        await fixture.open(tester);
        final picker = tester.widget<CatalogSourcePicker>(
          find.byType(CatalogSourcePicker),
        );
        await tester.tap(find.text('Language: All'));
        await tester.pumpAndSettle();
        expect(find.widgetWithText(MenuItemButton, 'EN'), findsOneWidget);

        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pumpAndSettle();
        expect(find.widgetWithText(MenuItemButton, 'EN'), findsNothing);
        expect(find.byType(CatalogSourcePicker), findsOneWidget);
        expect(picker.controller.isSettled, isFalse);
        expect(fixture.choices.saves, 0);

        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pumpAndSettle();
        expect(find.byType(CatalogSourcePicker), findsNothing);
        expect(fixture.choices.saves, 0);
        expect(fixture.readerCalls, 0);
        expect(fixture.nested.currentState!.canPop(), isFalse);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'production picker $width Escape leaves grouped language step first',
      (tester) async {
        final fixture = _Fixture();
        await fixture.mount(tester, width);
        await fixture.open(tester);
        await tester.tap(find.text('Provider'));
        await tester.pumpAndSettle();
        expect(find.text('Choose language'), findsOneWidget);
        final picker = tester.widget<CatalogSourcePicker>(
          find.byType(CatalogSourcePicker),
        );
        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pumpAndSettle();
        expect(find.byType(CatalogSourcePicker), findsOneWidget);
        expect(find.text('Choose language'), findsNothing);
        expect(picker.controller.isSettled, isFalse);

        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pumpAndSettle();
        expect(find.byType(CatalogSourcePicker), findsNothing);
        expect(fixture.choices.saves, 0);
        expect(fixture.readerCalls, 0);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      'production picker $width keyboard Escape leaves nested page intact',
      (tester) async {
        final fixture = _Fixture();
        await fixture.mount(tester, width);
        Focus.of(tester.element(find.text('Read'))).requestFocus();
        await tester.pump();
        final callerFocus = FocusManager.instance.primaryFocus;
        await fixture.open(tester);
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
        final modalFocus = FocusManager.instance.primaryFocus;
        expect(modalFocus, isNotNull);
        expect(modalFocus, isNot(same(callerFocus)));
        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
        await tester.pumpAndSettle();
        expect(find.byType(CatalogSourcePicker), findsNothing);
        expect(find.byType(CatalogDetailPage), findsOneWidget);
        expect(FocusManager.instance.primaryFocus, same(callerFocus));
        expect(fixture.root.currentState!.canPop(), isFalse);
        expect(fixture.choices.saves, 0);
      },
    );

    testWidgets('production picker $width source tile keeps exact metadata', (
      tester,
    ) async {
      final fixture = _Fixture();
      await fixture.mount(tester, width);
      await fixture.open(tester);
      await tester.tap(find.text('Provider'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('EN'));
      await tester.pumpAndSettle();
      expect(fixture.choices.selected!.source, fixture.candidate.media.source);
      expect(fixture.choices.saves, 1);
      expect(fixture.readerCalls, 0);
      expect(find.byType(CatalogSourcePicker), findsNothing);
      await tester.ensureVisible(find.text('Start reading'));
      await tester.tap(find.text('Start reading'));
      await tester.pumpAndSettle();
      expect(fixture.readerCalls, 1);
      expect(fixture.readerMedia!.source, fixture.candidate.media.source);
      expect(fixture.readerMetadata!.cover, fixture.candidate.metadata!.cover);
      expect(tester.takeException(), isNull);
    });

    testWidgets('production picker $width cancellation keeps saved choice', (
      tester,
    ) async {
      final fixture = _Fixture();
      fixture.choices.selected = fixture.candidate.media;
      await fixture.mount(tester, width);
      await tester.tap(find.text('Chapters'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Change source'));
      await tester.tap(find.text('Change source'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Close'));
      await tester.pumpAndSettle();
      expect(fixture.choices.selected!.source, fixture.candidate.media.source);
      expect(fixture.choices.saves, 0);
      expect(fixture.readerCalls, 0);
      expect(find.byType(CatalogSourcePicker), findsNothing);
    });

    testWidgets(
      'production picker $width keyboard inset keeps close reachable',
      (tester) async {
        final fixture = _Fixture();
        await fixture.mount(tester, width);
        await fixture.open(tester);
        tester.view.viewInsets = const FakeViewPadding(bottom: 260);
        addTearDown(tester.view.resetViewInsets);
        await tester.pumpAndSettle();
        expect(find.byTooltip('Close').hitTestable(), findsOneWidget);
        await tester.tap(find.byTooltip('Close'));
        await tester.pumpAndSettle();
        expect(fixture.choices.saves, 0);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets('production picker $width rejects mismatched cover source', (
      tester,
    ) async {
      final fixture = _Fixture();
      await fixture.mount(tester, width);
      await fixture.open(tester);
      final picker = tester.widget<CatalogSourcePicker>(
        find.byType(CatalogSourcePicker),
      );
      picker.controller.complete(
        CatalogCandidateSelection(
          CatalogSourcePickerCandidate(
            media: fixture.candidate.media,
            metadata: MediaMetadata(
              title: _entry.title,
              cover: const SourceMediaRef(
                sourceId: SourceId('other'),
                itemId: 'cover',
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(fixture.choices.saves, 0);
      expect(fixture.readerCalls, 0);
      expect(find.text('Invalid source selection.'), findsOneWidget);
    });

    testWidgets('production picker $width disposal ignores pending resolver', (
      tester,
    ) async {
      final fixture = _Fixture();
      fixture.sources.first.gate = Completer<MangaSearchPage>();
      await fixture.mount(tester, width);
      await fixture.open(tester);
      final picker = tester.widget<CatalogSourcePicker>(
        find.byType(CatalogSourcePicker),
      );
      final pending = picker.viewModel.selectSource(
        picker.viewModel.state.sources.first,
      );
      await tester.pump();
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
      fixture.sources.first.gate!.complete(fixture.sources.first.results());
      await pending;
      await tester.pumpAndSettle();
      expect(picker.controller.isSettled, isTrue);
      expect(fixture.choices.saves, 0);
      expect(fixture.readerCalls, 0);
      expect(tester.takeException(), isNull);
    });
  }
}

class _Fixture {
  final root = GlobalKey<NavigatorState>();
  final nested = GlobalKey<NavigatorState>();
  final revision = ValueNotifier(0);
  final choices = _Choices();
  final sources = [
    for (final language in ['en', 'fr']) _Source(language),
  ];
  late ResolveCatalogSource resolver = makeResolver();
  final details = LoadCatalogEntryDetails(_Provider());
  final chapters = ReadRemoteChapterList(_Cache());
  late final opener = OpenMedia(
    SourceRegistry(sources),
    _Progress(),
    chapterLists: chapters,
  );
  final target = LoadSeriesReadingTarget(_Continuation());
  int readerCalls = 0;
  Media? readerMedia;
  MediaMetadata? readerMetadata;
  Set<SourceId>? manualIds;
  String? manualLanguage;
  CatalogSourcePickerCandidate get candidate => CatalogSourcePickerCandidate(
    media: sources.first.results().results.first.media,
    metadata: sources.first.results().results.first.metadata,
  );

  ResolveCatalogSource makeResolver() {
    final registry = SourceRegistry(sources);
    return ResolveCatalogSource(
      searchManga: SearchManga(registry),
      searchNovels: SearchNovels(registry),
    );
  }

  Future<void> mount(
    WidgetTester tester,
    double width, {
    double scale = 1,
    bool stress = false,
  }) async {
    tester.view.physicalSize = Size(width, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(revision.dispose);
    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: root,
        theme: HikariTheme.darkTheme(),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(
            textScaler: TextScaler.linear(scale),
            disableAnimations: stress,
            padding: stress
                ? const EdgeInsets.fromLTRB(24, 24, 24, 24)
                : EdgeInsets.zero,
          ),
          child: Directionality(
            textDirection: stress ? TextDirection.rtl : TextDirection.ltr,
            child: child!,
          ),
        ),
        home: Navigator(
          key: nested,
          onGenerateRoute: (_) => MaterialPageRoute<void>(
            builder: (context) => MediaQuery(
              // Stress modal route independently: legacy detail chrome has its
              // own 3x overflow debt, outside the Phase 02 picker migration.
              data: MediaQuery.of(context)
                  .copyWith(textScaler: TextScaler.noScaling),
              child: ValueListenableBuilder<int>(
                valueListenable: revision,
                builder: (_, _, _) => CatalogDetailPage(
                  initialEntry: _entry,
                  loadDetails: details,
                  resolveCatalogSource: resolver,
                  chapterOpener: opener,
                  chapterLists: chapters,
                  loadReadingTarget: target,
                  sourceSelections: choices,
                  openMedia: (_, media, {previewMetadata, canNavigate}) async {
                    readerCalls++;
                    readerMedia = media;
                    readerMetadata = previewMetadata;
                  },
                  openRelated: (_) {},
                  openSourceSearch: (_, _, _, _, _) {},
                  selectSourceSearch: (_, _, _, ids, language) async {
                    manualIds = ids;
                    manualLanguage = language;
                    return null;
                  },
                  openMangaChapter: (_, _, _, _, _) async {
                    readerCalls++;
                  },
                  openNovelChapter: (_, _, _, _, _) async {
                    readerCalls++;
                  },
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> open(WidgetTester tester) async {
    tester
        .widget<FilledButton>(
          find.ancestor(
            of: find.text('Read'),
            matching: find.byType(FilledButton),
          ),
        )
        .onPressed!();
    await tester.pumpAndSettle();
  }
}

final _entry = CatalogEntry(
  id: const CatalogEntryId(provider: 'fixture', value: 'series'),
  title: 'Fixture series',
  type: MediaType.manga,
);

class _Source
    implements MangaSearchSource, MangaPageSource, MediaSourcePresentation {
  _Source(this.languageCode);
  @override
  final String languageCode;
  @override
  SourceId get id => SourceId('fixture:$languageCode');
  @override
  String get name => 'Provider $languageCode';
  @override
  String get displayName => 'Provider';
  @override
  String get presentationGroupId => 'fixture';
  Completer<MangaSearchPage>? gate;
  MangaSearchPage results() => MangaSearchPage(
    page: 1,
    hasNextPage: false,
    results: [
      MangaPreview(
        media: Media(
          title: _entry.title,
          type: MediaType.manga,
          source: SourceMediaRef(sourceId: id, itemId: 'exact-series'),
        ),
        metadata: MediaMetadata(
          title: _entry.title,
          cover: SourceMediaRef(sourceId: id, itemId: 'cover'),
        ),
      ),
    ],
  );
  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async =>
      gate?.future ?? results();
  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => [];
  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

class _Provider implements CatalogProvider {
  @override
  String get id => 'fixture';
  @override
  Future<CatalogDiscovery> discover() async => CatalogDiscovery(sections: {});
  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) async => null;
  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async =>
      [];
  @override
  Future<void> close() async {}
}

class _Choices implements CatalogSourceSelectionRepository {
  int saves = 0;
  Media? selected;
  @override
  Future<Media?> load(CatalogEntry entry) async => selected;
  @override
  Future<void> save(CatalogEntry entry, Media media) async {
    saves++;
    selected = media;
  }
}

class _Cache implements ByteCache {
  @override
  Future<Uint8List?> read(String namespace, String key) async => null;
  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {}
  @override
  Future<void> close() async {}
}

class _Progress implements ProgressRepository {
  @override
  Future<MediaProgress?> load(SourceMediaRef media) async => null;
  @override
  Future<void> save(MediaProgress progress) async {}
  @override
  Future<void> delete(SourceMediaRef media) async {}
}

class _Continuation implements SeriesContinuationRepository {
  @override
  Future<SourceMediaRef?> load(SourceMediaRef series) async => null;
  @override
  Future<void> save(SeriesContinuation continuation) async {}
}
