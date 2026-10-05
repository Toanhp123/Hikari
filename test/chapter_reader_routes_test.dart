import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/open_novel_chapter.dart';
import 'package:hikari/application/media/prefetch_manga_pages.dart';
import 'package:hikari/application/media/prefetch_novel_chapter.dart';
import 'package:hikari/application/media/read_manga_page.dart';
import 'package:hikari/application/media/read_novel_chapter_content.dart';
import 'package:hikari/application/media/read_novel_resource.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
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
  218,
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
