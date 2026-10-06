import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/open_novel_chapter.dart';
import 'package:hikari/application/media/prefetch_manga_chapter.dart';
import 'package:hikari/application/media/prefetch_manga_pages.dart';
import 'package:hikari/application/media/prefetch_novel_chapter.dart';
import 'package:hikari/application/media/read_manga_page.dart';
import 'package:hikari/application/media/read_novel_chapter_content.dart';
import 'package:hikari/application/media/read_novel_resource.dart';
import 'package:hikari/application/progress/save_series_chapter_progress.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/chapter_list_order.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/progress/series_continuation.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/remote_manga/manga_chapter_reader_page.dart';
import 'package:hikari/features/remote_manga/manga_chapter_reader_view_model.dart';
import 'package:hikari/features/remote_novel/novel_chapter_reader_page.dart';
import 'package:hikari/features/remote_novel/novel_chapter_reader_view_model.dart';

const _mangaId = SourceId('manga');
const _novelId = SourceId('novel');
const _mangaA = SourceMediaRef(sourceId: _mangaId, itemId: 'a');
const _mangaB = SourceMediaRef(sourceId: _mangaId, itemId: 'b');
const _novelA = SourceMediaRef(sourceId: _novelId, itemId: 'a');
const _novelB = SourceMediaRef(sourceId: _novelId, itemId: 'b');
const _novelC = SourceMediaRef(sourceId: _novelId, itemId: 'c');
final _png = Uint8List.fromList([
  137,
  80,
  78,
  71,
  13,
  10,
  26,
  10,
  0,
  0,
  0,
  13,
  73,
  72,
  68,
  82,
  0,
  0,
  0,
  1,
  0,
  0,
  0,
  1,
  8,
  6,
  0,
  0,
  0,
  31,
  21,
  196,
  137,
  0,
  0,
  0,
  13,
  73,
  68,
  65,
  84,
  120,
  156,
  99,
  248,
  207,
  192,
  240,
  31,
  0,
  5,
  0,
  1,
  255,
  137,
  153,
  61,
  29,
  0,
  0,
  0,
  0,
  73,
  69,
  78,
  68,
  174,
  66,
  96,
  130,
]);

class _Progress implements ProgressRepository {
  @override
  Future<void> delete(SourceMediaRef media) async {}

  @override
  Future<MediaProgress?> load(SourceMediaRef media) async => null;

  @override
  Future<void> save(MediaProgress progress) async {}
}

class _Continuations implements SeriesContinuationRepository {
  SourceMediaRef? chapter;

  @override
  Future<SourceMediaRef?> load(SourceMediaRef series) async => chapter;

  @override
  Future<void> save(SeriesContinuation continuation) async {
    chapter = continuation.chapter;
  }
}

class _DelayedProgress extends _Progress {
  final saveStarted = Completer<void>();
  final secondSaveStarted = Completer<void>();
  final allowSave = Completer<void>();
  final allowSecondSave = Completer<void>();
  final writes = <MediaProgress>[];
  final rows = <SourceMediaRef, MediaProgress>{};

  @override
  Future<MediaProgress?> load(SourceMediaRef media) async => rows[media];

  @override
  Future<void> save(MediaProgress progress) async {
    final isActivationBaseline = switch (progress.position) {
      PagePosition(:final pageIndex) => pageIndex == 0,
      TextPosition(:final progression) => progression == 0,
      _ => false,
    };
    if (!rows.containsKey(progress.media) && isActivationBaseline) {
      rows[progress.media] = progress;
      return;
    }
    writes.add(progress);
    if (!saveStarted.isCompleted) {
      saveStarted.complete();
      await allowSave.future;
    } else {
      if (!secondSaveStarted.isCompleted) secondSaveStarted.complete();
      await allowSecondSave.future;
    }
    rows[progress.media] = progress;
  }
}

class _FixedCache implements ByteCache {
  const _FixedCache();
  @override
  Future<Uint8List?> read(String namespace, String key) async => null;
  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {}
  @override
  Future<void> close() async {}
}

class _MangaReaderSource extends _MangaSource implements MangaSeriesSource {
  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef chapter) async => [
    const SourceMediaRef(sourceId: _mangaId, itemId: 'a-page-0'),
    const SourceMediaRef(sourceId: _mangaId, itemId: 'a-page-1'),
  ];

  @override
  Future<MangaSeriesDetails> loadDetails(SourceMediaRef manga) async =>
      MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Series'),
        chapters: [MangaChapter(title: 'Chapter A', source: _mangaA)],
        chapterListOrder: ChapterListOrder.readingOrder,
      );
}

class _StaticNovelSource implements NovelChapterSource {
  @override
  SourceId get id => _novelId;
  @override
  String get name => 'Novel';
  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async =>
      RichReadingContent(html: List.filled(60, 'reading text').join(' '));
  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => _png;
}

Future<void> _pushReader(
  BuildContext context,
  Widget page,
  VoidCallback onReturned,
  ValueNotifier<int> refreshes,
) async {
  await Navigator.of(context)
      .push<void>(MaterialPageRoute<void>(builder: (_) => page));
  onReturned();
  refreshes.value++;
}

Widget _home(
  Widget reader,
  ValueListenable<int> refreshes,
  VoidCallback onReturned,
) => MaterialApp(
  home: Builder(
    builder: (context) => Scaffold(
      body: Column(
        children: [
          ValueListenableBuilder<int>(
            valueListenable: refreshes,
            builder: (_, count, _) => Text('Home refresh $count'),
          ),
          TextButton(
            onPressed: () => _pushReader(
              context,
              reader,
              onReturned,
              refreshes as ValueNotifier<int>,
            ),
            child: const Text('Open reader'),
          ),
        ],
      ),
    ),
  ),
);

Future<void> _testDelayedRemoteExit(
  WidgetTester tester, {
  required bool novel,
  required bool systemBack,
}) async {
  final progress = _DelayedProgress();
  final refreshes = ValueNotifier<int>(0);
  addTearDown(() => refreshes.dispose());
  final manga = _MangaReaderSource();
  final novelSource = _StaticNovelSource();
  final reader = ReadNovelChapterContent(const _FixedCache());
  final continuations = _Continuations();
  final page = novel
      ? await _createNovelReaderRoute(
          novelSource,
          progress,
          reader,
          continuations,
        )
      : await _createMangaReaderRoute(manga, progress, continuations);
  var returned = false;
  await tester.pumpWidget(_home(page, refreshes, () => returned = true));

  await tester.tap(find.text('Open reader'));
  await tester.pumpAndSettle();
  if (novel) {
    final scroll = tester
        .widget<SingleChildScrollView>(find.byType(SingleChildScrollView))
        .controller!;
    scroll.jumpTo(scroll.position.maxScrollExtent * .6);
  } else {
    final image = tester.widget<Image>(find.byType(Image));
    await tester.runAsync(
      () => precacheImage(
        image.image,
        tester.element(find.byType(MangaReaderPage)),
      ),
    );
    await tester.pumpAndSettle();
    await progress.saveStarted.future;
    await tester.tap(find.byTooltip('Next page'));
    await tester.pumpAndSettle();
    expect(progress.writes, hasLength(1));
    expect(progress.writes.single.position, isA<PagePosition>());
    expect((progress.writes.single.position as PagePosition).pageIndex, 0);
    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 20));
    });
    await tester.pumpAndSettle();
    expect(find.byType(Image), findsOneWidget);
  }
  if (systemBack) {
    await tester.binding.handlePopRoute();
  } else {
    await tester.tap(find.byTooltip('Back').last);
  }
  await tester.pump();
  await progress.saveStarted.future;
  expect(find.text('Open reader'), findsNothing);
  expect(refreshes.value, 0);
  if (novel) {
    final scroll = tester
        .widget<SingleChildScrollView>(find.byType(SingleChildScrollView))
        .controller!;
    final frozenAt = scroll.offset;
    // Closing state intentionally rejects gesture input.
    await tester.drag(
      find.byType(SingleChildScrollView),
      const Offset(0, -300),
      warnIfMissed: false,
    );
    await tester.pumpAndSettle();
    expect(scroll.offset, frozenAt);
    expect(find.byTooltip('Reading Preferences'), findsOneWidget);
    await tester.tap(
      find.byTooltip('Reading Preferences'),
      warnIfMissed: false,
    );
    await tester.pumpAndSettle();
    expect(find.text('Color Theme'), findsNothing);
    expect(find.text('Font Size'), findsNothing);
  } else {
    expect(find.byTooltip('Next page'), findsOneWidget);
    await tester.tap(find.byTooltip('Next page'), warnIfMissed: false);
    expect(progress.writes, hasLength(1));
  }
  progress.allowSave.complete();
  if (!novel) {
    for (var i = 0; i < 5 && !progress.secondSaveStarted.isCompleted; i++) {
      await tester.pump();
    }
    expect(progress.secondSaveStarted.isCompleted, isTrue);
    expect(progress.writes, hasLength(2));
    expect(find.byType(MangaChapterReaderPage), findsOneWidget);
    expect(find.text('Open reader'), findsNothing);
    expect(refreshes.value, 0);
    progress.allowSecondSave.complete();
  }
  await tester.pumpAndSettle();
  expect(find.text('Open reader'), findsOneWidget);
  expect(refreshes.value, 1);
  expect(returned, isTrue);
  expect(continuations.chapter, novel ? _novelA : _mangaA);
  if (novel) {
    expect(progress.writes, hasLength(1));
  } else {
    expect(progress.writes, hasLength(2));
    expect(progress.rows[_mangaA]?.position, isA<PagePosition>());
    expect((progress.rows[_mangaA]!.position as PagePosition).pageIndex, 1);
  }
}

Future<Widget> _createMangaReaderRoute(
  _MangaReaderSource source,
  _DelayedProgress progress,
  _Continuations continuations,
) async {
  final workflow = OpenMangaChapter(SourceRegistry([source]), progress);
  final chapter = MangaChapter(title: 'Chapter A', source: _mangaA);
  final saveContinuation = SaveSeriesChapterProgress(
    continuations,
    const SourceMediaRef(sourceId: _mangaId, itemId: 'series'),
  );
  final model = MangaChapterReaderViewModel(
    initialTarget: await workflow.execute(chapter),
    chaptersInReadingOrder: [chapter],
    openChapter: workflow,
    onChapterActivated: (target) => saveContinuation.activate(
      target.progress,
      target.chapter.source,
      PagePosition(pageIndex: 0, pageCount: target.pages.length),
    ),
    onProgress: (target, position, completed) => saveContinuation.execute(
      target.progress,
      target.chapter.source,
      position,
      completed,
    ),
  );
  return MangaChapterReaderPage(
    viewModel: model,
    readPage: (source, page) => source.readPage(page),
    reloadPage: (source, page) => source.readPage(page),
    createPrefetch: () =>
        PrefetchMangaPages(ReadMangaPage(const _FixedCache())),
    prefetchPages: (prefetch, source, pages, index) =>
        prefetch.execute(source, pages, index),
  );
}

Future<Widget> _createNovelReaderRoute(
  _StaticNovelSource source,
  _DelayedProgress progress,
  ReadNovelChapterContent readContent,
  _Continuations continuations,
) async {
  final workflow = OpenNovelChapter(
    SourceRegistry([source]),
    progress,
    readContent,
  );
  final chapter = NovelChapter(title: 'Chapter A', source: _novelA);
  final saveContinuation = SaveSeriesChapterProgress(
    continuations,
    const SourceMediaRef(sourceId: _novelId, itemId: 'series'),
  );
  final model = NovelChapterReaderViewModel(
    initialTarget: await workflow.execute(chapter),
    chaptersInReadingOrder: [chapter],
    openChapter: workflow,
    onChapterActivated: (target) => saveContinuation.activate(
      target.progress,
      target.chapter.source,
      TextPosition(progression: 0),
    ),
    onProgress: (target, position, completed) => saveContinuation.execute(
      target.progress,
      target.chapter.source,
      position,
      completed,
    ),
  );
  final resource = ReadNovelResource(const _FixedCache());
  return NovelChapterReaderPage(
    viewModel: model,
    reloadContent: (source, chapter) => readContent.reload(source, chapter),
    readResource: resource.execute,
    reloadResource: resource.reload,
    createPrefetch: () =>
        PrefetchNovelChapter(SourceRegistry([source]), readContent, resource),
    prefetchChapter: (_, _) async {},
  );
}

class _Cache implements ByteCache {
  final values = <String, Uint8List>{};

  @override
  Future<Uint8List?> read(String namespace, String key) async =>
      values['$namespace/$key'];

  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {}

  @override
  Future<void> close() async {}
}

class _MangaSource implements MangaPageSource {
  final firstB = Completer<List<SourceMediaRef>>();
  final retryStarted = Completer<void>();
  final retryB = Completer<List<SourceMediaRef>>();
  final lateStarted = Completer<void>();
  final lateB = Completer<List<SourceMediaRef>>();
  int bOpens = 0;

  @override
  SourceId get id => _mangaId;
  @override
  String get name => 'Manga source';

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef chapter) {
    if (chapter == _mangaB) {
      bOpens++;
      if (bOpens == 1) return firstB.future;
      if (bOpens == 2) {
        if (!retryStarted.isCompleted) retryStarted.complete();
        return retryB.future;
      }
      if (bOpens == 3) {
        if (!lateStarted.isCompleted) lateStarted.complete();
        return lateB.future;
      }
    }
    return Future.value([
      SourceMediaRef(sourceId: id, itemId: '${chapter.itemId}-page'),
    ]);
  }

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => _png;
}

class _MangaPrefetchSource implements MangaPageSource {
  final pageLists = <String>[];
  final pageReads = <String>[];

  @override
  SourceId get id => _mangaId;

  @override
  String get name => 'Manga prefetch source';

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef chapter) async {
    pageLists.add(chapter.itemId);
    final count = chapter == _mangaA ? 8 : 3;
    return List.generate(
      count,
      (index) =>
          SourceMediaRef(sourceId: id, itemId: '${chapter.itemId}-$index'),
    );
  }

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async {
    pageReads.add(page.itemId);
    return _png;
  }
}

class _NovelSource implements NovelChapterSource {
  final firstB = Completer<RichReadingContent>();
  final retryStarted = Completer<void>();
  final retryB = Completer<RichReadingContent>();
  final lateStarted = Completer<void>();
  final lateB = Completer<RichReadingContent>();
  int bOpens = 0;

  @override
  SourceId get id => _novelId;
  @override
  String get name => 'Novel source';

  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) {
    if (chapter == _novelB) {
      bOpens++;
      if (bOpens == 1) return firstB.future;
      if (bOpens == 2) {
        if (!retryStarted.isCompleted) retryStarted.complete();
        return retryB.future;
      }
      if (bOpens == 3) {
        if (!lateStarted.isCompleted) lateStarted.complete();
        return lateB.future;
      }
    }
    return Future.value(RichReadingContent(html: '<p>${chapter.itemId}</p>'));
  }

  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => _png;
}

void main() {
  testWidgets(
    'manga next-chapter prefetch waits for the final five pages and reuses page list',
    (tester) async {
      final source = _MangaPrefetchSource();
      final cache = _Cache();
      final reader = ReadMangaPage(cache);
      final chapterPrefetch = PrefetchMangaChapter(
        SourceRegistry([source]),
        reader,
      );
      final workflow = OpenMangaChapter(SourceRegistry([source]), _Progress());
      final chapters = [
        MangaChapter(title: 'Chapter A', source: _mangaA),
        MangaChapter(title: 'Chapter B', source: _mangaB),
      ];
      final initial = await workflow.execute(chapters.first);
      source.pageLists.clear();
      final viewModel = MangaChapterReaderViewModel(
        initialTarget: initial,
        chaptersInReadingOrder: chapters,
        openChapter: workflow,
        pageListLoader: chapterPrefetch.loadPages,
        chapterPrefetch: chapterPrefetch,
      );

      await tester.pumpWidget(
        MaterialApp(
          home: MangaChapterReaderPage(
            viewModel: viewModel,
            readPage: reader.execute,
            reloadPage: reader.reload,
            createPrefetch: () => PrefetchMangaPages(reader),
            prefetchPages: (prefetch, pageSource, pages, index) =>
                prefetch.execute(pageSource, pages, index),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.runAsync(() async {
        await precacheImage(
          tester.widget<Image>(find.byType(Image)).image,
          tester.element(find.byType(MangaChapterReaderPage)),
        );
      });
      await tester.pumpAndSettle();

      expect(source.pageLists.where((id) => id == 'b'), isEmpty);
      for (var i = 0; i < 2; i++) {
        await tester.tap(find.byTooltip('Next page'));
        await tester.pumpAndSettle();
        expect(source.pageLists.where((id) => id == 'b'), isEmpty);
      }

      await tester.tap(find.byTooltip('Next page'));
      await tester.pumpAndSettle();
      expect(source.pageLists.where((id) => id == 'b'), hasLength(1));
      expect(source.pageReads.where((id) => id.startsWith('b-')).take(2), [
        'b-0',
        'b-1',
      ]);

      await tester.tap(find.byTooltip('Next chapter'));
      await tester.pumpAndSettle();
      expect(viewModel.state.target.chapter.source, _mangaB);
      expect(source.pageLists.where((id) => id == 'b'), hasLength(1));

      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets('novel reader prefetch window follows the current chapter', (
    tester,
  ) async {
    final source = _NovelSource();
    final contentCache = _Cache();
    final contentReader = ReadNovelChapterContent(contentCache);
    final resourceReader = ReadNovelResource(_Cache());
    final workflow = OpenNovelChapter(
      SourceRegistry([source]),
      _Progress(),
      contentReader,
    );
    final chapters = [
      NovelChapter(title: 'Chapter A', source: _novelA),
      NovelChapter(title: 'Chapter B', source: _novelB),
      NovelChapter(title: 'Chapter C', source: _novelC),
    ];
    final viewModel = NovelChapterReaderViewModel(
      initialTarget: await workflow.execute(chapters.first),
      chaptersInReadingOrder: chapters,
      openChapter: workflow,
    );
    final prefetched = <SourceMediaRef>[];

    await tester.pumpWidget(
      MaterialApp(
        home: NovelChapterReaderPage(
          viewModel: viewModel,
          reloadContent: contentReader.reload,
          readResource: resourceReader.execute,
          reloadResource: resourceReader.reload,
          createPrefetch: () => PrefetchNovelChapter(
            SourceRegistry([source]),
            contentReader,
            resourceReader,
          ),
          prefetchChapter: (_, chapter) async {
            prefetched.add(chapter.source);
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(prefetched, [_novelB]);

    source.firstB.complete(RichReadingContent(html: '<p>B</p>'));
    await tester.tap(find.byTooltip('Next chapter'));
    await tester.pumpAndSettle();

    expect(viewModel.state.target.chapter.source, _novelB);
    expect(prefetched, [_novelB, _novelC]);

    await tester.tap(find.byTooltip('Next chapter'));
    await tester.pumpAndSettle();

    expect(viewModel.state.target.chapter.source, _novelC);
    expect(prefetched, [_novelB, _novelC]);
    await tester.pumpWidget(const SizedBox());
  });

  for (final popBySystem in [true, false]) {
    for (final isNovel in [true, false]) {
      testWidgets(
        '${isNovel ? 'novel' : 'manga'} remote route drains save before ${popBySystem ? 'OS' : 'AppBar'} pop',
        (tester) => _testDelayedRemoteExit(
          tester,
          novel: isNovel,
          systemBack: popBySystem,
        ),
      );
    }
  }

  for (final lateFailure in [false, true]) {
    testWidgets(
      'manga adjacent failure retries; late failure=$lateFailure after pop is ignored',
      (tester) async {
        final source = _MangaSource();
        final cache = _Cache();
        final progress = _Progress();
        final workflow = OpenMangaChapter(SourceRegistry([source]), progress);
        final chapters = [
          MangaChapter(title: 'Chapter A', source: _mangaA),
          MangaChapter(title: 'Chapter B', source: _mangaB),
        ];
        final viewModel = MangaChapterReaderViewModel(
          initialTarget: await workflow.execute(chapters.first),
          chaptersInReadingOrder: chapters,
          openChapter: workflow,
        );
        await tester.pumpWidget(
          MaterialApp(
            home: Builder(
              builder: (context) => Scaffold(
                body: TextButton(
                  onPressed: () => Navigator.of(context).push<void>(
                    MaterialPageRoute<void>(
                      builder: (_) => MangaChapterReaderPage(
                        viewModel: viewModel,
                        readPage: (source, page) => source.readPage(page),
                        reloadPage: (source, page) => source.readPage(page),
                        createPrefetch: () =>
                            PrefetchMangaPages(ReadMangaPage(cache)),
                        prefetchPages: (prefetch, source, pages, index) =>
                            prefetch.execute(source, pages, index),
                      ),
                    ),
                  ),
                  child: const Text('Open'),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Open'));
        await tester.pumpAndSettle();
        expect(find.text('Chapter A'), findsOneWidget);

        await tester.tap(find.byTooltip('Next chapter'));
        await tester.pump();
        expect(source.bOpens, 1);
        source.firstB.completeError(StateError('offline'));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 400));
        expect(find.text('Chapter A'), findsOneWidget);
        expect(viewModel.state.target.chapter.source, _mangaA);
        expect(
          find.text('Could not open this chapter. Try again.'),
          findsOneWidget,
        );

        ScaffoldMessenger.of(tester.element(find.text('Chapter A')))
            .removeCurrentSnackBar();
        await tester.pump();
        await tester.tap(find.byTooltip('Next chapter'));
        await tester.pump();
        expect(source.retryStarted.isCompleted, isTrue);
        source.retryB.complete([
          const SourceMediaRef(sourceId: _mangaId, itemId: 'b-page'),
        ]);
        await tester.pump();
        expect(viewModel.state.target.chapter.source, _mangaB);
        expect(find.text('Chapter B'), findsOneWidget);
        await tester.pumpAndSettle();

        await tester.tap(find.byTooltip('Previous chapter'));
        await tester.pump();
        await tester.pumpAndSettle();
        expect(viewModel.state.target.chapter.source, _mangaA);
        expect(find.text('Chapter A'), findsOneWidget);
        await tester.tap(find.byTooltip('Next chapter'));
        await tester.pump();
        expect(source.lateStarted.isCompleted, isTrue);
        await tester.pageBack();
        expect(viewModel.state.target.chapter.source, _mangaA);
        if (lateFailure) {
          source.lateB.completeError(StateError('late offline'));
        } else {
          source.lateB.complete([
            const SourceMediaRef(sourceId: _mangaId, itemId: 'b-page'),
          ]);
        }
        await tester.runAsync(() async {
          await Future<void>.delayed(Duration.zero);
          await Future<void>.delayed(Duration.zero);
        });
        await tester.pump(const Duration(milliseconds: 50));
        expect(viewModel.state.target.chapter.source, _mangaA);
        expect(find.text('Chapter A'), findsOneWidget);
        expect(
          find.text('Could not open this chapter. Try again.'),
          findsNothing,
        );
        await tester.pumpAndSettle();
        await tester.pumpWidget(const SizedBox());
      },
    );

    testWidgets(
      'novel adjacent failure retries; late failure=$lateFailure after pop is ignored',
      (tester) async {
        final source = _NovelSource();
        final contentCache = _Cache();
        final workflowContent = ReadNovelChapterContent(contentCache);
        final readResource = ReadNovelResource(_Cache());
        final workflow = OpenNovelChapter(
          SourceRegistry([source]),
          _Progress(),
          workflowContent,
        );
        final chapters = [
          NovelChapter(title: 'Chapter A', source: _novelA),
          NovelChapter(title: 'Chapter B', source: _novelB),
        ];
        final viewModel = NovelChapterReaderViewModel(
          initialTarget: await workflow.execute(chapters.first),
          chaptersInReadingOrder: chapters,
          openChapter: workflow,
        );
        await tester.pumpWidget(
          MaterialApp(
            home: Builder(
              builder: (context) => Scaffold(
                body: TextButton(
                  onPressed: () => Navigator.of(context).push<void>(
                    MaterialPageRoute<void>(
                      builder: (_) => NovelChapterReaderPage(
                        viewModel: viewModel,
                        reloadContent: (source, chapter) =>
                            workflowContent.reload(source, chapter),
                        readResource: (source, resource) =>
                            readResource.execute(source, resource),
                        reloadResource: (source, resource) =>
                            readResource.reload(source, resource),
                        createPrefetch: () => PrefetchNovelChapter(
                          SourceRegistry([source]),
                          workflowContent,
                          readResource,
                        ),
                        prefetchChapter: (_, _) => Future<void>.value(),
                      ),
                    ),
                  ),
                  child: const Text('Open'),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Open'));
        await tester.pumpAndSettle();
        expect(find.text('Chapter A'), findsOneWidget);

        await tester.tap(find.byTooltip('Next chapter'));
        await tester.pump();
        expect(source.bOpens, 1);
        source.firstB.completeError(StateError('offline'));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 400));
        expect(find.text('Chapter A'), findsOneWidget);
        expect(viewModel.state.target.chapter.source, _novelA);
        expect(
          find.text('Could not open this chapter. Try again.'),
          findsOneWidget,
        );

        ScaffoldMessenger.of(tester.element(find.text('Chapter A')))
            .removeCurrentSnackBar();
        await tester.pump();
        await tester.tap(find.byTooltip('Next chapter'));
        await tester.pump();
        expect(source.retryStarted.isCompleted, isTrue);
        source.retryB.complete(RichReadingContent(html: '<p>B retry</p>'));
        await tester.pumpAndSettle();
        expect(viewModel.state.target.chapter.source, _novelB);
        expect(find.text('Chapter B'), findsOneWidget);
        await tester.pumpAndSettle();

        await tester.tap(find.byTooltip('Previous chapter'));
        await tester.pumpAndSettle();
        expect(viewModel.state.target.chapter.source, _novelA);
        contentCache.values.clear();
        await tester.tap(find.byTooltip('Next chapter'));
        await tester.pump();
        expect(source.lateStarted.isCompleted, isTrue);
        await tester.pageBack();
        expect(viewModel.state.target.chapter.source, _novelA);
        if (lateFailure) {
          source.lateB.completeError(StateError('late offline'));
        } else {
          source.lateB.complete(RichReadingContent(html: '<p>B late</p>'));
        }
        await tester.runAsync(() async {
          await Future<void>.delayed(Duration.zero);
          await Future<void>.delayed(Duration.zero);
        });
        await tester.pump(const Duration(milliseconds: 50));
        expect(viewModel.state.target.chapter.source, _novelA);
        expect(find.byType(NovelChapterReaderPage), findsOneWidget);
        expect(
          find.text('Could not open this chapter. Try again.'),
          findsNothing,
        );
        await tester.pumpAndSettle();
        await tester.pumpWidget(const SizedBox());
      },
    );
  }
}
