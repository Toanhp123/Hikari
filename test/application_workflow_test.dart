import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/publication.dart';
import 'package:hikari/domain/media/reading.dart';
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
  Future<void> save(MediaProgress progress) async =>
      records[progress.media] = progress;
}

class _NovelSource implements NovelTextSource {
  @override
  SourceId get id => _novelSourceId;
  @override
  String get name => 'Novel source';
  @override
  Future<String> readText(SourceMediaRef media) async => 'text';
}

class _PublicationSource implements PublicationSource, NovelTextSource {
  _PublicationSource({required this.canOpen});
  final bool canOpen;
  @override
  SourceId get id => _novelSourceId;
  @override
  String get name => 'Publication source';
  @override
  bool canOpenPublication(SourceMediaRef publication) => canOpen;
  @override
  Future<Publication> publication(SourceMediaRef publication) async =>
      Publication(
        metadata: const MediaMetadata(title: 'Book'),
        spine: const [
          PublicationSection(resource: 'chapter.xhtml', title: 'Chapter'),
        ],
      );
  @override
  Future<NovelChapterContent> readSection(
    SourceMediaRef publication,
    String resource,
  ) async => NovelChapterContent(html: '<p>chapter</p>');
  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
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
    implements MangaSearchSource, MangaSeriesSource, MangaPageSource {
  @override
  SourceId get id => _mangaSourceId;
  @override
  String get name => 'Manga source';
  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async =>
      MangaSearchPage(results: const [], hasNextPage: false, page: page);
  @override
  Future<MangaSeriesDetails> loadSeries(SourceMediaRef manga) async =>
      const MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Series'),
        chapters: [],
      );
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
  Future<MangaSearchPage> search(String query, {int page = 1}) async =>
      MangaSearchPage(results: const [], hasNextPage: false, page: page);
}

class _InvalidSearchSource implements MangaSearchSource, MangaPageSource {
  @override
  SourceId get id => const SourceId('invalid-search');
  @override
  String get name => 'Invalid search';
  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async =>
      MangaSearchPage(
        results: const [
          MangaPreview(
            media: Media(
              title: 'Wrong source',
              type: MediaType.manga,
              source: SourceMediaRef(
                sourceId: SourceId('other'),
                itemId: 'series',
              ),
            ),
          ),
        ],
        hasNextPage: false,
        page: page,
      );
  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];
  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

class _ForeignMangaSource extends _MangaSource {
  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async =>
      MangaSearchPage(
        results: [
          MangaPreview(
            media: Media(
              title: 'Series',
              type: MediaType.manga,
              source: SourceMediaRef(sourceId: id, itemId: 'series'),
            ),
            metadata: const MediaMetadata(
              title: 'Series',
              cover: SourceMediaRef(
                sourceId: SourceId('foreign'),
                itemId: 'cover',
              ),
            ),
          ),
        ],
        page: page,
        hasNextPage: false,
      );
  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => [
    const SourceMediaRef(sourceId: SourceId('foreign'), itemId: 'page'),
  ];
  @override
  Future<MangaSeriesDetails> loadSeries(SourceMediaRef manga) async =>
      const MangaSeriesDetails(
        metadata: MediaMetadata(title: 'Series'),
        chapters: [
          MangaChapter(
            title: 'Foreign',
            source: SourceMediaRef(
              sourceId: SourceId('foreign'),
              itemId: 'chapter',
            ),
          ),
        ],
      );
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

class _RichNovelSource implements NovelSeriesSource, NovelChapterSource {
  @override
  SourceId get id => _novelSourceId;
  @override
  String get name => 'Rich novel';
  @override
  Future<NovelDetails> novelDetails(SourceMediaRef novel) async => NovelDetails(
    metadata: const MediaMetadata(title: 'Rich book'),
    chapters: [],
  );
  @override
  Future<NovelChapterContent> chapterContent(SourceMediaRef chapter) async =>
      NovelChapterContent(html: '<p>Rich text</p>');
  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
}

void main() {
  test('manga rejects foreign cover chapters and pages', () async {
    final source = _ForeignMangaSource();
    final registry = SourceRegistry([source]);
    final progress = _MemoryProgressRepository();
    final ref = SourceMediaRef(sourceId: source.id, itemId: 'series');
    await expectLater(
      SearchManga(registry).execute(sourceId: source.id, query: 'test'),
      throwsStateError,
    );
    final target = await OpenMedia(
      registry,
      progress,
    ).execute(Media(title: 'Series', type: MediaType.manga, source: ref));
    await expectLater(
      (target as MangaSeriesOpenTarget).loadDetails(),
      throwsStateError,
    );
    await expectLater(
      OpenMangaChapter(
        registry,
        progress,
      ).execute(MangaChapter(title: 'Chapter', source: ref)),
      throwsStateError,
    );
  });
  test(
    'rich novel series routes without requiring plain text capability',
    () async {
      final repository = _MemoryProgressRepository();
      final target =
          await OpenMedia(
            SourceRegistry([_RichNovelSource()]),
            repository,
          ).execute(
            const Media(
              title: 'Rich book',
              type: MediaType.lightNovel,
              source: SourceMediaRef(sourceId: _novelSourceId, itemId: 'book'),
            ),
          );
      expect(target.runtimeType.toString(), 'NovelSeriesOpenTarget');
      expect(repository.loadCount, 0);
    },
  );
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
    await expectLater(
      SearchManga(SourceRegistry([source]))
          .execute(sourceId: source.id, query: 'test'),
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

  test('open media requires direct video capability', () async {
    const media = Media(
      title: 'Episode',
      type: MediaType.anime,
      source: SourceMediaRef(sourceId: _videoSourceId, itemId: 'episode'),
    );
    final target = await OpenMedia(
      SourceRegistry([_VideoSource()]),
      _MemoryProgressRepository(),
    ).execute(media);
    expect(target, isA<VideoOpenTarget>());
    expect((target as VideoOpenTarget).locator, 'play://episode');
  });

  test('anime requires explicit direct video capability', () async {
    const media = Media(
      title: 'Episode',
      type: MediaType.anime,
      source: SourceMediaRef(sourceId: _novelSourceId, itemId: 'episode'),
    );
    await expectLater(
      OpenMedia(
        SourceRegistry([_NovelSource()]),
        _MemoryProgressRepository(),
      ).execute(media),
      throwsStateError,
    );
  });

  test(
    'publication source takes light novel routing before TXT fallback',
    () async {
      const ref = SourceMediaRef(sourceId: _novelSourceId, itemId: 'book');
      final publication = _PublicationSource(canOpen: true);
      final target =
          await OpenMedia(
            SourceRegistry([publication]),
            _MemoryProgressRepository(),
          ).execute(
            const Media(title: 'Book', type: MediaType.lightNovel, source: ref),
          );
      expect(target, isA<PublicationReaderOpenTarget>());
      expect(
        (target as PublicationReaderOpenTarget).publicationSource,
        same(publication),
      );
      final fallback =
          await OpenMedia(
            SourceRegistry([_PublicationSource(canOpen: false)]),
            _MemoryProgressRepository(),
          ).execute(
            const Media(title: 'Book', type: MediaType.lightNovel, source: ref),
          );
      expect(fallback, isA<NovelReaderOpenTarget>());
    },
  );

  test('series open defers progress until chapter selected', () async {
    const series = SourceMediaRef(sourceId: _mangaSourceId, itemId: 'series');
    const chapter = MangaChapter(
      title: 'Chapter 1',
      source: SourceMediaRef(sourceId: _mangaSourceId, itemId: 'chapter'),
    );
    final repository = _MemoryProgressRepository();
    final source = _MangaSource();
    final registry = SourceRegistry([source]);
    final seriesTarget = await OpenMedia(registry, repository).execute(
      const Media(title: 'Series', type: MediaType.manga, source: series),
    );
    expect(seriesTarget, isA<MangaSeriesOpenTarget>());
    expect(repository.loadCount, 0);
    final chapterTarget = await OpenMangaChapter(
      registry,
      repository,
    ).execute(chapter);
    expect(repository.loadCount, 1);
    expect(chapterTarget.pages, [
      const SourceMediaRef(sourceId: _mangaSourceId, itemId: 'chapter/0'),
    ]);
    expect(chapterTarget.source, same(source));
  });

  test('open chapter rejects unavailable source', () async {
    const chapter = MangaChapter(
      title: 'Unavailable chapter',
      source: SourceMediaRef(sourceId: _mangaSourceId, itemId: 'chapter'),
    );
    await expectLater(
      OpenMangaChapter(
        SourceRegistry([_UnavailableMangaSource()]),
        _MemoryProgressRepository(),
      ).execute(chapter),
      throwsStateError,
    );
  });

  test('open media rejects unavailable source', () async {
    const media = Media(
      title: 'Unavailable',
      type: MediaType.lightNovel,
      source: SourceMediaRef(sourceId: _novelSourceId, itemId: 'book'),
    );
    await expectLater(
      OpenMedia(
        SourceRegistry([_UnavailableNovelSource()]),
        _MemoryProgressRepository(),
      ).execute(media),
      throwsStateError,
    );
  });
}
