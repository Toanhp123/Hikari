import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/feedback/hikari_notice_banner.dart';
import 'package:hikari/core/ui/media/hikari_artwork.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/features/catalog/catalog_source_picker_view_model.dart';
import 'package:hikari/features/catalog/widgets/catalog_source_candidate_list.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/features/library/widgets/library_button.dart';
import 'package:hikari/features/source_search/source_search_view_model.dart';
import 'package:hikari/features/source_search/widgets/source_search_content.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';
import 'package:drift/native.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/remote_manga/manga_series_page.dart';
import 'package:hikari/features/remote_manga/manga_series_view_model.dart';
import 'package:hikari/features/remote_novel/novel_series_page.dart';
import 'package:hikari/features/remote_novel/novel_series_view_model.dart';

const _source = SourceId('preview');
SourceMediaRef _ref(String id) => SourceMediaRef(sourceId: _source, itemId: id);

class _Novel implements NovelSeriesSource {
  _Novel(this.load, {this.sourceId = _source});
  final SourceId sourceId;
  final Future<NovelDetails> Function() load;
  @override
  SourceId get id => sourceId;
  @override
  String get name => 'Source';
  @override
  Future<NovelDetails> loadDetails(SourceMediaRef ref) => load();
}

class _Manga implements MangaSeriesSource {
  int calls = 0;
  @override
  SourceId get id => _source;
  @override
  String get name => 'Source';
  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef ref) async {
    calls++;
    return MangaSeriesDetails(
      metadata: MediaMetadata(title: 'Full'),
      chapters: [],
      chapterListOrder: ChapterListOrder.readingOrder,
    );
  }
}

class _PendingLibrary implements LibraryRepository {
  final pending = Completer<void>();
  LibraryEntry? entry;
  int writes = 0;
  @override
  Future<bool> contains(SourceMediaRef media) async => entry != null;
  @override
  Future<List<LibraryEntry>> loadAll() async => [?entry];
  @override
  Future<void> remove(SourceMediaRef media) async {
    entry = null;
  }

  @override
  Future<void> upsert(LibraryEntry value) async {
    writes++;
    await pending.future;
    entry = value;
  }
}

void main() {
  test('OpenMedia hands preview to series without loading details and rejects foreign cover', () async {
    final db = UserDatabase(NativeDatabase.memory());
    addTearDown(db.close);
    final source = _Manga();
    final open = OpenMedia(
      SourceRegistry([source]),
      SqliteProgressRepository(db),
    );
    final media = Media(
      title: 'Stored title',
      type: MediaType.manga,
      source: _ref('series'),
    );
    final preview = MediaMetadata(title: 'Preview', cover: _ref('cover'));
    final target = await open.execute(
      media,
      previewMetadata: preview,
    ) as MangaSeriesOpenTarget;
    expect(target.previewMetadata, same(preview));
    expect(source.calls, 0);
    expect(
      (await open.execute(media) as MangaSeriesOpenTarget).previewMetadata,
      isNull,
    );
    await expectLater(
      open.execute(
        media,
        previewMetadata: MediaMetadata(
          title: 'Foreign',
          cover: const SourceMediaRef(
            sourceId: SourceId('anilist'),
            itemId: 'cover',
          ),
        ),
      ),
      throwsArgumentError,
    );
    expect(source.calls, 0);
  });

  testWidgets('cover change during pending library save keeps mutation busy', (
    tester,
  ) async {
    final repository = _PendingLibrary();
    final media = Media(
      title: 'Stored title',
      type: MediaType.manga,
      source: _ref('series'),
    );
    Widget button(String cover) => MaterialApp(
      theme: HikariTheme.darkTheme(),
      home: Scaffold(
        body: LibraryButton(
          repository: repository,
          media: media,
          artwork: _ref(cover),
        ),
      ),
    );
    await tester.pumpWidget(button('preview'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Add to library'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Add'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    await tester.pump();
    await tester.pumpWidget(button('full'));
    await tester.pump();
    expect(
      tester.widget<IconButton>(find.byType(IconButton)).onPressed,
      isNull,
    );
    expect(repository.writes, 1);
    repository.pending.complete();
    await tester.pumpAndSettle();
    expect(repository.entry!.artwork, _ref('preview'));
    expect(repository.entry!.media.title, 'Stored title');
  });
  testWidgets(
    'partial source search keeps results with status text, not a progress bar',
    (tester) async {
      final controller = TextEditingController(text: 'Test');
      addTearDown(controller.dispose);
      final media = Media(
        title: 'Found result',
        type: MediaType.manga,
        source: _ref('result'),
      );
      Widget host(SourceSearchStatus status) => MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: SourceSearchContent(
            controller: controller,
            state: SourceSearchUiState(
              query: 'Test',
              inputQuery: 'Test',
              status: status,
              results: [SourceSearchResult(media: media)],
            ),
            onQueryChanged: (_) {},
            onSubmitted: (_) {},
            onSelectFilter: (_) {},
            onRetry: () {},
            openMedia: (_, _, {previewMetadata}) {},
          ),
        ),
      );
      await tester.pumpWidget(host(SourceSearchStatus.loading));
      expect(find.text('Searching remaining sources…'), findsOneWidget);
      expect(find.byType(LinearProgressIndicator), findsNothing);
      expect(find.text('Found result'), findsOneWidget);
      await tester.pumpWidget(host(SourceSearchStatus.ready));
      expect(find.text('Searching remaining sources…'), findsNothing);
      expect(find.text('Found result'), findsOneWidget);
    },
  );

  for (final manga in [true, false]) {
    final kind = manga ? 'manga' : 'novel';
    final preview = MediaMetadata(
      title: 'Source preview',
      cover: _ref('cover'),
      summary: 'Summary',
      authors: ['Author'],
      genres: ['Genre'],
    );
    dynamic model(Future<MediaMetadata> Function() load) => manga
        ? MangaSeriesViewModel(
            () async => MangaSeriesDetails(
              metadata: await load(),
              chapterListOrder: ChapterListOrder.readingOrder,
              chapters: [],
            ),
            series: _ref('series'),
            previewMetadata: preview,
          )
        : NovelSeriesViewModel(
            () async => NovelDetails(
              metadata: await load(),
              chapterListOrder: ChapterListOrder.readingOrder,
              chapters: [],
            ),
            series: _ref('series'),
            previewMetadata: preview,
          );

    testWidgets('$kind series swap binds new preview and rejects old details', (
      tester,
    ) async {
      const aId = SourceId('source-a');
      const bId = SourceId('source-b');
      SourceMediaRef ref(SourceId id, String item) =>
          SourceMediaRef(sourceId: id, itemId: item);
      final aPreview = MediaMetadata(
        title: 'Preview A',
        cover: ref(aId, 'cover'),
      );
      final bPreview = MediaMetadata(
        title: 'Preview B',
        cover: ref(bId, 'cover'),
      );
      final aPending = Completer<MediaMetadata>();
      final bPending = Completer<MediaMetadata>();
      final reads = <SourceMediaRef>[];
      var aLoads = 0;
      var bLoads = 0;
      Future<MediaMetadata> loadA() {
        aLoads++;
        return aPending.future;
      }

      Future<MediaMetadata> loadB() {
        bLoads++;
        return bPending.future;
      }

      final aSource = _Novel(
        () async => NovelDetails(
          metadata: await loadA(),
          chapterListOrder: ChapterListOrder.readingOrder,
          chapters: [],
        ),
        sourceId: aId,
      );
      final bSource = _Novel(
        () async => NovelDetails(
          metadata: await loadB(),
          chapterListOrder: ChapterListOrder.readingOrder,
          chapters: [],
        ),
        sourceId: bId,
      );
      Widget page(bool useB) {
        final id = useB ? bId : aId;
        final media = Media(
          title: 'Stored',
          type: manga ? MediaType.manga : MediaType.lightNovel,
          source: ref(id, 'series'),
        );
        final initial = useB ? bPreview : aPreview;
        Future<Uint8List?> read(SourceMediaRef cover) async {
          expect(cover.sourceId, id);
          reads.add(cover);
          return null;
        }

        return MaterialApp(
          home: manga
              ? MangaSeriesPage(
                  media: media,
                  previewMetadata: initial,
                  sourceName: 'Source',
                  dependencyOwner: useB ? bSource : aSource,
                  loadDetails: () async => MangaSeriesDetails(
                    metadata: await (useB ? loadB() : loadA()),
                    chapterListOrder: ChapterListOrder.readingOrder,
                    chapters: [],
                  ),
                  readArtwork: read,
                  openChapter: (_, _, _, _) async {},
                )
              : NovelSeriesPage(
                  target: NovelSeriesOpenTarget(
                    media,
                    source: useB ? bSource : aSource,
                    previewMetadata: initial,
                  ),
                  readArtwork: read,
                  openChapter: (_, _, _, _) async {},
                ),
        );
      }

      await tester.pumpWidget(page(false));
      await tester.pump();
      expect(find.text('Preview A'), findsOneWidget);
      await tester.pumpWidget(page(true));
      await tester.pump();
      expect(find.text('Preview B'), findsOneWidget);
      expect(find.text('Preview A'), findsNothing);
      expect(reads, [aPreview.cover, bPreview.cover]);
      aPending.complete(MediaMetadata(title: 'Late A', cover: aPreview.cover));
      await tester.pump();
      expect(find.text('Late A'), findsNothing);
      expect(find.text('Preview B'), findsOneWidget);
      bPending.complete(MediaMetadata(title: 'Full B', cover: bPreview.cover));
      await tester.pumpAndSettle();
      expect(find.text('Full B'), findsOneWidget);
      expect(aLoads, 1);
      expect(bLoads, 1);
      await tester.pumpWidget(page(true));
      await tester.pumpAndSettle();
      expect(bLoads, 1);
      await tester.tap(find.byTooltip('Refresh'));
      await tester.pumpAndSettle();
      expect(bLoads, 2);
      expect(aLoads, 1);
      expect(reads, [aPreview.cover, bPreview.cover]);
      expect(find.text('Late A'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('$kind chosen catalog candidate preserves its own metadata', (
      tester,
    ) async {
      final candidate = CatalogSourcePickerCandidate(
        media: Media(
          title: 'Chosen',
          type: manga ? MediaType.manga : MediaType.lightNovel,
          source: _ref('chosen'),
        ),
        metadata: preview,
      );
      CatalogSourcePickerCandidate? selected;
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: Scaffold(
            body: CatalogSourceCandidateList(
              source: const CatalogSourcePickerSource(
                id: _source,
                displayName: 'Source',
              ),
              candidates: [candidate],
              onSelect: (value) => selected = value,
              onChooseAnother: () {},
              onManualSearch: () {},
              readArtwork: null,
            ),
          ),
        ),
      );
      await tester.tap(find.text('Chosen'));
      expect(selected, same(candidate));
      expect(selected!.metadata, same(preview));
    });

    testWidgets('$kind manual source search passes result metadata unchanged', (
      tester,
    ) async {
      final media = Media(
        title: 'Result',
        type: manga ? MediaType.manga : MediaType.lightNovel,
        source: _ref('series'),
      );
      final controller = TextEditingController();
      addTearDown(controller.dispose);
      Media? opened;
      MediaMetadata? received;
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: Scaffold(
            body: SourceSearchContent(
              controller: controller,
              state: SourceSearchUiState(
                status: SourceSearchStatus.ready,
                results: [SourceSearchResult(media: media, metadata: preview)],
              ),
              onQueryChanged: (_) {},
              onSubmitted: (_) {},
              onSelectFilter: (_) {},
              onRetry: () {},
              openMedia: (_, item, {previewMetadata}) {
                opened = item;
                received = previewMetadata;
              },
            ),
          ),
        ),
      );
      await tester.tapAt(tester.getCenter(find.text('Result')));
      expect(opened, same(media));
      expect(received, same(preview));
    });

    test(
      '$kind preview is not full details; full null fields authoritative',
      () async {
        final pending = Completer<MediaMetadata>();
        final dynamic vm = model(() => pending.future);
        addTearDown(() => vm.dispose());
        final loading = vm.load() as Future<void>;
        expect(vm.effectiveMetadata, same(preview));
        expect(vm.state.initialLoading, isTrue);
        expect(vm.state.details, isNull);
        expect(vm.primaryChapter, isNull);
        final full = MediaMetadata(title: 'Full');
        pending.complete(full);
        await loading;
        expect(vm.effectiveMetadata, same(full));
        expect(vm.effectiveMetadata.cover, isNull);
        expect(vm.effectiveMetadata.summary, isNull);
        expect(vm.effectiveMetadata.authors, isEmpty);
        expect(vm.effectiveMetadata.genres, isEmpty);
      },
    );

    test(
      '$kind error retains preview; retry replaces cover; refresh error keeps full',
      () async {
        var calls = 0;
        final full = MediaMetadata(title: 'Full', cover: _ref('new-cover'));
        final dynamic vm = model(() async {
          if (++calls != 2) throw StateError('offline');
          return full;
        });
        addTearDown(() => vm.dispose());
        await (vm.load() as Future<void>);
        expect(vm.state.failed, isTrue);
        expect(vm.effectiveMetadata, same(preview));
        await (vm.load() as Future<void>);
        expect(vm.effectiveMetadata, same(full));
        await (vm.load() as Future<void>);
        expect(vm.state.refreshFailed, isTrue);
        expect(vm.effectiveMetadata, same(full));
      },
    );

    test(
      '$kind owner replacement drops preview and ignores stale success',
      () async {
        final old = Completer<MediaMetadata>();
        final dynamic vm = model(() => old.future);
        addTearDown(() => vm.dispose());
        final loading = vm.load() as Future<void>;
        if (manga) {
          (vm as MangaSeriesViewModel).updateDependencies(
            () async => throw StateError('new owner'),
            null,
            ownerChanged: true,
          );
        } else {
          (vm as NovelSeriesViewModel).updateDependencies(
            () async => throw StateError('new owner'),
            null,
            ownerChanged: true,
          );
        }
        expect(vm.effectiveMetadata, isNull);
        old.complete(MediaMetadata(title: 'Stale'));
        await loading;
        await Future<void>.value();
        expect(vm.effectiveMetadata, isNull);
        expect(vm.state.failed, isTrue);
      },
    );

    test('$kind rejects foreign preview cover', () {
      final foreign = MediaMetadata(
        title: 'Foreign',
        cover: const SourceMediaRef(
          sourceId: SourceId('foreign'),
          itemId: 'cover',
        ),
      );
      expect(
        () => manga
            ? MangaSeriesViewModel(
                () async => throw UnimplementedError(),
                series: _ref('series'),
                previewMetadata: foreign,
              )
            : NovelSeriesViewModel(
                () async => throw UnimplementedError(),
                series: _ref('series'),
                previewMetadata: foreign,
              ),
        throwsArgumentError,
      );
    });

    testWidgets(
      '$kind preview error announces Retry and does not claim empty chapters',
      (tester) async {
        tester.view.physicalSize = const Size(375, 900);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        var calls = 0;
        final retry = Completer<MediaMetadata>();
        Future<MediaMetadata> load() async {
          if (++calls == 1) throw StateError('offline');
          return retry.future;
        }

        final media = Media(
          title: 'Stored title',
          type: manga ? MediaType.manga : MediaType.lightNovel,
          source: _ref('series'),
        );
        final source = _Novel(
          () async => NovelDetails(
            metadata: await load(),
            chapterListOrder: ChapterListOrder.readingOrder,
            chapters: [],
          ),
        );
        await tester.pumpWidget(
          MaterialApp(
            theme: HikariTheme.darkTheme(),
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                textScaler: const TextScaler.linear(1.5),
                disableAnimations: true,
              ),
              child: child!,
            ),
            home: manga
                ? MangaSeriesPage(
                    media: media,
                    sourceName: 'Source',
                    previewMetadata: preview,
                    loadDetails: () async => MangaSeriesDetails(
                      metadata: await load(),
                      chapterListOrder: ChapterListOrder.readingOrder,
                      chapters: [],
                    ),
                    openChapter: (_, _, _, _) async {},
                  )
                : NovelSeriesPage(
                    target: NovelSeriesOpenTarget(
                      media,
                      source: source,
                      previewMetadata: preview,
                    ),
                    openChapter: (_, _, _, _) async {},
                  ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('Source preview'), findsOneWidget);
        expect(find.text('Retry'), findsOneWidget);
        expect(find.text('No readable chapters found.'), findsNothing);
        expect(find.text('Start reading'), findsNothing);
        expect(
          tester
              .widget<Semantics>(
                find
                    .ancestor(
                      of: find.byType(HikariNoticeBanner),
                      matching: find.byType(Semantics),
                    )
                    .first,
              )
              .properties
              .liveRegion,
          isTrue,
        );
        tester.view.physicalSize = const Size(900, 375);
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
        await tester.ensureVisible(find.text('Retry'));
        await tester.tap(find.text('Retry'));
        await tester.pump();
        expect(calls, 2);
        expect(find.byType(CircularProgressIndicator), findsOneWidget);
        expect(find.byType(LinearProgressIndicator), findsNothing);
        expect(find.text('Source preview'), findsOneWidget);
        retry.complete(MediaMetadata(title: 'Full'));
        await tester.pumpAndSettle();
        expect(find.text('Full'), findsOneWidget);
        expect(find.text('Source preview'), findsNothing);
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets(
      '$kind pending preview survives rebuild and full same-cover without artwork reread',
      (tester) async {
        final pending = Completer<MediaMetadata>();
        var detailsReads = 0;
        var artworkReads = 0;
        Future<MediaMetadata> load() {
          detailsReads++;
          return pending.future;
        }

        final source = _Novel(
          () async => NovelDetails(
            metadata: await load(),
            chapterListOrder: ChapterListOrder.readingOrder,
            chapters: [],
          ),
        );
        final media = Media(
          title: 'Saved title',
          type: manga ? MediaType.manga : MediaType.lightNovel,
          source: _ref('series'),
        );
        Future<Uint8List?> artwork(SourceMediaRef _) async {
          artworkReads++;
          return null;
        }

        final scale = ValueNotifier<double>(1);
        addTearDown(scale.dispose);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        Widget page() => MaterialApp(
          theme: HikariTheme.darkTheme(),
          builder: (context, child) => ValueListenableBuilder<double>(
            valueListenable: scale,
            builder: (context, value, _) => MediaQuery(
              data: MediaQuery.of(context)
                  .copyWith(textScaler: TextScaler.linear(value)),
              child: Directionality(
                textDirection: TextDirection.rtl,
                child: child!,
              ),
            ),
          ),
          home: manga
              ? MangaSeriesPage(
                  media: media,
                  sourceName: 'Source',
                  previewMetadata: preview,
                  loadDetails: () async => MangaSeriesDetails(
                    metadata: await load(),
                    chapterListOrder: ChapterListOrder.readingOrder,
                    chapters: [],
                  ),
                  readArtwork: artwork,
                  dependencyOwner: source,
                  openChapter: (_, _, _, _) async {},
                )
              : NovelSeriesPage(
                  target: NovelSeriesOpenTarget(
                    media,
                    source: source,
                    previewMetadata: preview,
                  ),
                  readArtwork: artwork,
                  openChapter: (_, _, _, _) async {},
                ),
        );
        await tester.pumpWidget(page());
        await tester.pump();
        expect(find.text('Source preview'), findsOneWidget);
        expect(find.byType(CircularProgressIndicator), findsOneWidget);
        expect(find.byType(LinearProgressIndicator), findsNothing);
        expect(find.text('Start reading'), findsNothing);
        expect(find.text('No readable chapters found.'), findsNothing);
        expect(detailsReads, 1);
        expect(artworkReads, 1);
        await tester.pumpWidget(page());
        await tester.pump();
        expect(detailsReads, 1);
        expect(artworkReads, 1);
        pending.complete(MediaMetadata(title: 'Full', cover: preview.cover));
        await tester.pumpAndSettle();
        expect(find.byType(HikariArtwork), findsOneWidget);
        expect(find.text('Full'), findsOneWidget);
        expect(find.text('Source preview'), findsNothing);
        for (final textScale in [1.0, 2.0, 3.0]) {
          scale.value = textScale;
          for (final width in [
            375.0,
            599.0,
            600.0,
            601.0,
            839.0,
            840.0,
            841.0,
            1200.0,
            1440.0,
          ]) {
            tester.view.physicalSize = Size(width, 900);
            await tester.pumpAndSettle();
            expect(
              tester.takeException(),
              isNull,
              reason: '$kind $width at ${textScale}x',
            );
            expect(
              MediaQuery.textScalerOf(
                tester.element(find.byType(HikariArtwork)),
              ).scale(10),
              textScale * 10,
            );
            expect(detailsReads, 1);
            expect(artworkReads, 1);
          }
        }
        await tester.pumpWidget(const SizedBox());
      },
    );
  }
}
