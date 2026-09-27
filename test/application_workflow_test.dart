import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/progress.dart';

const _novelSourceId = SourceId('novel');
const _mangaSourceId = SourceId('manga');
const _videoSourceId = SourceId('video');

class _MemoryProgressRepository implements ProgressRepository {
  final records = <SourceMediaRef, MediaProgress>{};
  int loadCount = 0;

  @override
  Future<void> delete(SourceMediaRef media) async => records.remove(media);

  @override
  Future<MediaProgress?> load(SourceMediaRef media) async {
    loadCount++;
    return records[media];
  }

  @override
  Future<void> save(MediaProgress progress) async {
    records[progress.media] = progress;
  }
}

class _NovelSource implements NovelTextSource {
  @override
  SourceId get id => _novelSourceId;

  @override
  String get name => 'Novel source';

  @override
  Future<String> readText(SourceMediaRef media) async => 'text';
}

class _VideoSource implements DirectVideoSource {
  @override
  SourceId get id => _videoSourceId;

  @override
  String get name => 'Video source';

  @override
  String playbackLocator(SourceMediaRef media) => 'play://${media.itemId}';
}

class _MangaSource
    implements MangaSearchSource, MangaChapterSource, MangaPageSource {
  @override
  SourceId get id => _mangaSourceId;

  @override
  String get name => 'Manga source';

  @override
  Future<List<Media>> search(String query) async => const [];

  @override
  Future<List<MangaChapter>> chapters(SourceMediaRef manga) async => const [];

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => [
    SourceMediaRef(sourceId: id, itemId: '${readable.itemId}/0'),
  ];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

class _SearchOnlySource implements MangaSearchSource {
  @override
  SourceId get id => const SourceId('search-only');

  @override
  String get name => 'Search only';

  @override
  Future<List<Media>> search(String query) async => const [];
}

class _InvalidSearchSource implements MangaSearchSource, MangaPageSource {
  @override
  SourceId get id => const SourceId('invalid-search');

  @override
  String get name => 'Invalid search';

  @override
  Future<List<Media>> search(String query) async => const [
    Media(
      title: 'Wrong source',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId('other'), itemId: 'series'),
    ),
  ];

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

class _UnavailableMangaSource extends _MangaSource
    implements MediaSourceAvailability {
  @override
  bool get isAvailable => false;
}

class _UnavailableNovelSource extends _NovelSource
    implements MediaSourceAvailability {
  @override
  bool get isAvailable => false;
}

void main() {
  test(
    'source registry resolves capabilities without provider-specific logic',
    () {
      final manga = _MangaSource();
      final novel = _NovelSource();
      final registry = SourceRegistry([manga, novel]);

      expect(registry.require(_mangaSourceId), same(manga));
      expect(
        registry.requireCapability<MangaPageSource>(_mangaSourceId),
        same(manga),
      );
      expect(registry.withCapability<MangaSearchSource>(), [manga]);
      expect(
        () => registry.requireCapability<NovelTextSource>(_mangaSourceId),
        throwsStateError,
      );
    },
  );

  test('source registry rejects duplicate stable ids', () {
    expect(
      () => SourceRegistry([_NovelSource(), _NovelSource()]),
      throwsStateError,
    );
  });

  test('manga search exposes only sources whose results can be opened', () {
    final manga = _MangaSource();
    final workflow = SearchManga(SourceRegistry([manga, _SearchOnlySource()]));

    expect(workflow.options, hasLength(1));
    expect(workflow.options.single.id, _mangaSourceId);
  });

  test('manga search rejects malformed source-scoped results', () async {
    final source = _InvalidSearchSource();
    final workflow = SearchManga(SourceRegistry([source]));

    await expectLater(
      workflow.execute(sourceId: source.id, query: 'test'),
      throwsStateError,
    );
  });

  test('manga search excludes and rejects unavailable sources', () async {
    final source = _UnavailableMangaSource();
    final workflow = SearchManga(SourceRegistry([source]));

    expect(workflow.options, isEmpty);
    await expectLater(
      workflow.execute(sourceId: source.id, query: 'test'),
      throwsStateError,
    );
  });

  test(
    'open media requires and resolves direct video playback capability',
    () async {
      const media = Media(
        title: 'Episode',
        type: MediaType.anime,
        source: SourceMediaRef(sourceId: _videoSourceId, itemId: 'episode'),
      );
      final repository = _MemoryProgressRepository();
      final workflow = OpenMedia(SourceRegistry([_VideoSource()]), repository);

      final target = await workflow.execute(media);

      expect(target, isA<VideoOpenTarget>());
      expect((target as VideoOpenTarget).locator, 'play://episode');
      expect(repository.loadCount, 1);
    },
  );

  test('anime requires an explicit direct video capability', () async {
    const media = Media(
      title: 'Not directly playable',
      type: MediaType.anime,
      source: SourceMediaRef(sourceId: _novelSourceId, itemId: 'episode'),
    );
    final workflow = OpenMedia(
      SourceRegistry([_NovelSource()]),
      _MemoryProgressRepository(),
    );

    await expectLater(workflow.execute(media), throwsStateError);
  });

  test(
    'open media prepares direct reader progress and save workflow',
    () async {
      const ref = SourceMediaRef(sourceId: _novelSourceId, itemId: 'book');
      final repository = _MemoryProgressRepository();
      repository.records[ref] = MediaProgress(
        media: ref,
        position: TextPosition(progression: 0.4),
        completed: false,
        updatedAt: DateTime.utc(2026),
      );
      final workflow = OpenMedia(SourceRegistry([_NovelSource()]), repository);

      final target = await workflow.execute(
        const Media(title: 'Book', type: MediaType.lightNovel, source: ref),
      );

      expect(target, isA<NovelReaderOpenTarget>());
      final novel = target as NovelReaderOpenTarget;
      expect(
        (novel.progress.initialProgress!.position as TextPosition).progression,
        0.4,
      );
      await novel.progress.save(TextPosition(progression: 0.7), false);
      expect(
        (repository.records[ref]!.position as TextPosition).progression,
        0.7,
      );
      expect(repository.records[ref]!.updatedAt.isUtc, isTrue);
    },
  );

  test(
    'series open defers progress until a concrete chapter is selected',
    () async {
      const series = SourceMediaRef(sourceId: _mangaSourceId, itemId: 'series');
      const chapter = MangaChapter(
        title: 'Chapter 1',
        source: SourceMediaRef(sourceId: _mangaSourceId, itemId: 'chapter'),
      );
      final repository = _MemoryProgressRepository();
      final source = _MangaSource();
      final registry = SourceRegistry([source]);
      final openMedia = OpenMedia(registry, repository);
      final openChapter = OpenMangaChapter(registry, repository);

      final seriesTarget = await openMedia.execute(
        const Media(title: 'Series', type: MediaType.manga, source: series),
      );
      expect(seriesTarget, isA<MangaSeriesOpenTarget>());
      expect(repository.loadCount, 0);

      final chapterTarget = await openChapter.execute(chapter);
      expect(repository.loadCount, 1);
      expect(chapterTarget.pages, [
        const SourceMediaRef(sourceId: _mangaSourceId, itemId: 'chapter/0'),
      ]);
      expect(chapterTarget.source, same(source));
    },
  );

  test(
    'open chapter rejects a registered source that is unavailable',
    () async {
      const chapter = MangaChapter(
        title: 'Unavailable chapter',
        source: SourceMediaRef(sourceId: _mangaSourceId, itemId: 'chapter'),
      );
      final workflow = OpenMangaChapter(
        SourceRegistry([_UnavailableMangaSource()]),
        _MemoryProgressRepository(),
      );

      await expectLater(workflow.execute(chapter), throwsStateError);
    },
  );

  test('open media rejects a registered source that is unavailable', () async {
    const media = Media(
      title: 'Unavailable',
      type: MediaType.lightNovel,
      source: SourceMediaRef(sourceId: _novelSourceId, itemId: 'book'),
    );
    final workflow = OpenMedia(
      SourceRegistry([_UnavailableNovelSource()]),
      _MemoryProgressRepository(),
    );

    await expectLater(workflow.execute(media), throwsStateError);
  });
}
