import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/reading.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/infrastructure/extensions/mihon/mihon_extension_gateway.dart';
import 'package:hikari/infrastructure/extensions/mihon/mihon_source_loader.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';

const _mangaId = '11111111-2222-3333-4444-555555555555';
const _chapterId = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';

final class _FakeGateway implements MihonExtensionGateway {
  _FakeGateway(this.descriptors);

  final List<MihonSourceDescriptor> descriptors;
  String? searchedSource;
  int? searchedPage;
  String? chapterMangaUrl;
  String? artworkUrl;
  MihonMangaItem? seriesManga;
  String revision = '1';
  String? chapterMangaTitle;
  String? chapterMangaMemo;
  String? pageChapterUrl;
  String? pageChapterTitle;
  double? pageChapterNumber;
  String? pageChapterScanlator;
  int? pageChapterDateUpload;
  String? pageChapterMemo;
  MihonPageItem? readPageItem;

  @override
  Future<List<MihonSourceDescriptor>> listSources() async => descriptors;

  @override
  Future<MihonSearchPage> search({
    required String sourceKey,
    required String query,
    required int page,
  }) async {
    searchedSource = sourceKey;
    searchedPage = page;
    return MihonSearchPage(
      items: [
        MihonMangaItem(
          title: revision == '1' ? 'Example' : 'Renamed $revision',
          url: '/manga/$_mangaId',
          memo: '{"seriesId":"123"}',
        ),
      ],
      hasNextPage: true,
    );
  }

  @override
  Future<MihonSeriesResult> loadSeries({
    required String sourceKey,
    required String mangaUrl,
    String? mangaTitle,
    String? mangaMemo,
  }) async {
    chapterMangaUrl = mangaUrl;
    chapterMangaTitle = mangaTitle;
    chapterMangaMemo = mangaMemo;
    return MihonSeriesResult(
      manga:
          seriesManga ??
          const MihonMangaItem(
            title: 'Example details',
            url: '/manga/$_mangaId',
            summary: 'Summary',
            authors: ['Author'],
            artists: ['Artist'],
            genres: ['Genre'],
            status: 'ongoing',
            rawStatus: 'Ongoing',
            memo: '{"seriesId":"123"}',
          ),
      chapters: [
        MihonChapterItem(
          title: revision == '1' ? 'Chapter 1' : 'Renamed chapter',
          url: '/chapter/$_chapterId',
          scanlator: revision == '1' ? 'Group' : 'New group',
          chapterNumber: revision == '1' ? 1 : 1.5,
          dateUpload: revision == '1' ? 123456789 : 987654321,
          memo: revision == '1' ? '{"chapterId":"456"}' : 'new opaque memo',
        ),
      ],
    );
  }

  @override
  Future<Uint8List> readArtwork({
    required String sourceKey,
    required String url,
  }) async {
    artworkUrl = url;
    return Uint8List.fromList([4, 5, 6]);
  }

  @override
  Future<List<MihonPageItem>> pages({
    required String sourceKey,
    required String chapterUrl,
    String? chapterTitle,
    double? chapterNumber,
    String? chapterScanlator,
    int? chapterDateUpload,
    String? chapterMemo,
  }) async {
    pageChapterUrl = chapterUrl;
    pageChapterTitle = chapterTitle;
    pageChapterNumber = chapterNumber;
    pageChapterScanlator = chapterScanlator;
    pageChapterDateUpload = chapterDateUpload;
    pageChapterMemo = chapterMemo;
    return const [
      MihonPageItem(
        index: 0,
        url: 'https://example.test/page/0',
        imageUrl: 'https://cdn.example.test/0.jpg',
      ),
    ];
  }

  @override
  Future<Uint8List> readPage({
    required String sourceKey,
    required MihonPageItem page,
  }) async {
    readPageItem = page;
    return Uint8List.fromList([1, 2, 3]);
  }
}

const _mangaDex = MihonSourceDescriptor(
  sourceKey: '2499283573021220255',
  name: 'MangaDex',
  language: 'en',
  packageName: 'eu.kanade.tachiyomi.extension.all.mangadex',
  baseUrl: 'https://mangadex.org',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late UserDatabase database;
  setUp(() {
    database = UserDatabase(NativeDatabase.memory());
  });
  tearDown(() async {
    await database.close();
  });

  test('file restart retains updated continuation without search', () async {
    await database.close();
    final directory = await Directory.systemTemp.createTemp('mihon-state-');
    addTearDown(() async {
      await database.close();
      await directory.delete(recursive: true);
    });
    final file = File('${directory.path}/state.sqlite');
    database = UserDatabase(NativeDatabase(file));
    final gateway = _FakeGateway(const [_mangaDex]);
    var source =
        (await MihonSourceLoader(
              gateway: gateway,
              database: database,
            ).loadSources()).single
            as MangaSearchSource;
    final media = (await source.search('example')).results.single.media;
    final chapter = (await (source as MangaSeriesSource).loadSeries(
      media.source,
    )).chapters.single;
    gateway.revision = '2';
    final refreshed = (await source.search('example')).results.single.media;
    expect(refreshed.source, media.source);
    final refreshedChapter = (await (source as MangaSeriesSource).loadSeries(
      media.source,
    )).chapters.single;
    expect(refreshedChapter.source, chapter.source);
    await database.close();
    database = UserDatabase(NativeDatabase(file));
    source =
        (await MihonSourceLoader(
              gateway: gateway,
              database: database,
            ).loadSources()).single
            as MangaSearchSource;
    await (source as MangaPageSource).pages(chapter.source);
    expect(gateway.pageChapterMemo, 'new opaque memo');
    expect(gateway.pageChapterTitle, 'Renamed chapter');
    expect(gateway.pageChapterNumber, 1.5);
    expect(gateway.pageChapterScanlator, 'New group');
    expect(gateway.pageChapterDateUpload, 987654321);
    await (source as MangaSeriesSource).loadSeries(media.source);
    expect(gateway.chapterMangaTitle, 'Example details');
    expect(gateway.chapterMangaMemo, '{"seriesId":"123"}');
    await database.close();
  });

  test('version 2 file migrates identities and preserves unrelated user data', () async {
    await database.close();
    final directory = await Directory.systemTemp.createTemp('mihon-migration-');
    addTearDown(() async {
      await database.close();
      await directory.delete(recursive: true);
    });
    final file = File('${directory.path}/state.sqlite');
    database = UserDatabase(NativeDatabase(file));
    final oldId =
        'mihon-v1:${base64Url.encode(utf8.encode(jsonEncode({'kind': 'chapter', 'url': '/chapter/$_chapterId', 'title': 'Old chapter', 'memo': 'opaque memo', 'scanlator': 'Original group', 'chapterNumber': 1.5, 'dateUpload': 123})))}';
    final ref = SourceMediaRef(
      sourceId: const SourceId('mihon:2499283573021220255'),
      itemId: oldId,
    );
    final malformedIds = [
      for (final url in ['', '   '])
        for (final title in ['First', 'Second'])
          'mihon-v1:${base64Url.encode(utf8.encode(jsonEncode({'kind': 'chapter', 'url': url, 'title': title})))}',
    ];
    final progress = SqliteProgressRepository(database);
    for (final target in [
      ref,
      ...malformedIds.map(
        (itemId) => SourceMediaRef(sourceId: ref.sourceId, itemId: itemId),
      ),
      const SourceMediaRef(sourceId: SourceId.local, itemId: 'local'),
    ]) {
      await progress.save(
        MediaProgress(
          media: target,
          position: PagePosition(pageIndex: 2, pageCount: 5),
          completed: false,
          updatedAt: DateTime.utc(2026),
        ),
      );
    }
    await database.customStatement('DROP TABLE mihon_continuation_records');
    await database.customStatement('PRAGMA user_version = 2');
    await database.close();
    database = UserDatabase(NativeDatabase(file));
    final rows = await database.select(database.progressRecords).get();
    expect(rows, hasLength(2 + malformedIds.length));
    for (final itemId in malformedIds) {
      final preserved = rows.singleWhere((row) => row.itemId == itemId);
      expect(preserved.pageIndex, 2);
      expect(preserved.pageCount, 5);
    }
    final migrated = rows.singleWhere(
      (row) => row.itemId.startsWith('mihon-v2:'),
    );
    expect(migrated.itemId, startsWith('mihon-v2:'));
    expect(migrated.pageIndex, 2);
    expect(migrated.pageCount, 5);
    expect(rows.singleWhere((row) => row.sourceId == 'local').itemId, 'local');
    final gateway = _FakeGateway(const [_mangaDex]);
    final source = (await MihonSourceLoader(
      gateway: gateway,
      database: database,
    ).loadSources()).single;
    await (source as MangaPageSource).pages(
      SourceMediaRef(sourceId: ref.sourceId, itemId: migrated.itemId),
    );
    expect(gateway.pageChapterTitle, 'Old chapter');
    expect(gateway.pageChapterMemo, 'opaque memo');
    expect(gateway.pageChapterNumber, 1.5);
    expect(gateway.pageChapterScanlator, 'Original group');
    await database.close();
  });

  test('series identity ignores mutable provider title', () async {
    final gateway = _FakeGateway(const [_mangaDex]);
    final source =
        (await MihonSourceLoader(
              gateway: gateway,
              database: database,
            ).loadSources()).single
            as MangaSearchSource;
    final first = (await source.search('example')).results.single.media.source;
    gateway.revision = '2';
    final second = (await source.search('example')).results.single.media.source;
    expect(second, first);
  });

  test(
    'opaque persisted references survive extension absence and reinstall',
    () async {
      final db = database;
      final gateway = _FakeGateway(const [_mangaDex]);
      final source =
          (await MihonSourceLoader(
                gateway: gateway,
                database: database,
              ).loadSources()).single
              as MangaSearchSource;
      final media = (await source.search('example')).results.single.media;
      final chapter = (await (source as MangaSeriesSource).loadSeries(
        media.source,
      )).chapters.single;
      expect((await source.search('example')).hasNextPage, isTrue);
      expect(gateway.searchedPage, 1);
      expect(
        media.source.sourceId,
        const SourceId('mihon:2499283573021220255'),
      );
      expect(media.source.itemId, startsWith('mihon-v2:'));
      expect(chapter.source.itemId, startsWith('mihon-v2:'));
      final progress = SqliteProgressRepository(db);
      final initial = AppDependencies.create(database: db);
      await initial.libraryRepository.upsert(
        LibraryEntry(media: media, addedAt: DateTime.utc(2026)),
      );
      await progress.save(
        MediaProgress(
          media: chapter.source,
          position: PagePosition(pageIndex: 0, pageCount: 1),
          completed: false,
          updatedAt: DateTime.utc(2026),
        ),
      );
      await initial.dispose();

      for (var installation = 0; installation < 2; installation++) {
        final absent = AppDependencies.create(database: db);
        try {
          expect(absent.searchManga.options, isEmpty);
          await expectLater(absent.openMedia.execute(media), throwsStateError);
          await expectLater(
            absent.openMangaChapter.execute(chapter),
            throwsStateError,
          );
          expect(await absent.libraryRepository.contains(media.source), isTrue);
          expect(await progress.load(chapter.source), isNotNull);
        } finally {
          await absent.dispose();
        }

        final sources = await MihonSourceLoader(
          gateway: gateway,
          database: database,
        ).loadSources();
        final installed = AppDependencies.create(
          database: db,
          additionalSources: sources,
        );
        try {
          expect(
            installed.searchManga.options.single.id,
            media.source.sourceId,
          );
          final saved =
              (await installed.libraryRepository.loadAll()).single.media;
          final target =
              await installed.openMedia.execute(saved) as MangaSeriesOpenTarget;
          await target.chapterSource.loadSeries(saved.source);
          expect(gateway.chapterMangaUrl, '/manga/$_mangaId');
          final readable = await installed.openMangaChapter.execute(chapter);
          expect(gateway.pageChapterUrl, '/chapter/$_chapterId');
          expect(readable.progress.initialProgress?.media, chapter.source);
          final position =
              readable.progress.initialProgress!.position as PagePosition;
          expect(position.pageIndex, 0);
          expect(position.pageCount, 1);
          expect(
            await installed.libraryRepository.contains(media.source),
            isTrue,
          );
        } finally {
          await installed.dispose();
        }
      }
    },
  );

  test(
    'source loader adapts installed extension sources to Hikari media sources',
    () async {
      final gateway = _FakeGateway(const [
        MihonSourceDescriptor(
          sourceKey: '42',
          name: 'Example Source',
          language: 'vi',
          packageName: 'example.extension',
          baseUrl: 'https://example.test',
        ),
      ]);

      final sources = await MihonSourceLoader(
        gateway: gateway,
        database: database,
      ).loadSources();

      expect(sources, hasLength(1));
      final source = sources.single;
      expect(source, isA<MangaSearchSource>());
      expect(source, isA<MangaSeriesSource>());
      expect(source, isA<MangaPageSource>());
      expect(source.id, const SourceId('mihon:42'));
      expect(source.name, 'Example Source [vi]');
    },
  );

  test(
    'MangaDex uses generic extension identity and references end to end',
    () async {
      final gateway = _FakeGateway(const [_mangaDex]);
      final source =
          (await MihonSourceLoader(
                gateway: gateway,
                database: database,
              ).loadSources()).single
              as MangaSearchSource;

      expect(source.id, const SourceId('mihon:2499283573021220255'));

      final search = await source.search('example');
      expect(gateway.searchedSource, _mangaDex.sourceKey);
      expect(gateway.searchedPage, 1);
      final result = search.results.single;
      expect(result.media.source.sourceId, source.id);
      expect(result.media.source.itemId, startsWith('mihon-v2:'));

      final seriesSource = source as MangaSeriesSource;
      final details = await seriesSource.loadSeries(result.media.source);
      final chapters = details.chapters;
      expect(details.metadata.title, 'Example details');
      expect(details.metadata.authors, ['Author']);
      expect(details.metadata.status, PublicationStatus.ongoing);
      expect(details.metadata.rawStatus, 'Ongoing');
      expect(details.metadata.summary, 'Summary');
      expect(result.metadata, isNotNull);
      expect(result.metadata!.title, 'Example');
      expect(result.media.source.itemId, startsWith('mihon-v2:'));
      expect(result.media.source.sourceId, source.id);
      expect(result.media.title, 'Example');
      expect(chapters.single.source.sourceId, source.id);
      expect(chapters.single.source.itemId, startsWith('mihon-v2:'));
      expect(chapters.single.scanlator, 'Group');
      expect(chapters.single.chapterNumber, 1);
      expect(chapters.single.dateUpload, 123456789);

      final pageSource = source as MangaPageSource;
      final pages = await pageSource.pages(chapters.single.source);
      expect(gateway.chapterMangaUrl, '/manga/$_mangaId');
      expect(gateway.chapterMangaTitle, 'Example');
      expect(gateway.chapterMangaMemo, '{"seriesId":"123"}');
      expect(gateway.pageChapterUrl, '/chapter/$_chapterId');
      expect(gateway.pageChapterTitle, 'Chapter 1');
      expect(gateway.pageChapterNumber, 1);
      expect(gateway.pageChapterScanlator, 'Group');
      expect(gateway.pageChapterDateUpload, 123456789);
      expect(gateway.pageChapterMemo, '{"chapterId":"456"}');
      expect(pages, hasLength(1));
      expect(pages.single.sourceId, source.id);
      expect(await pageSource.readPage(pages.single), [1, 2, 3]);
      expect(gateway.readPageItem?.index, 0);
      expect(gateway.readPageItem?.url, 'https://example.test/page/0');
      expect(gateway.readPageItem?.imageUrl, 'https://cdn.example.test/0.jpg');
      expect(gateway.readPageItem?.uri, isNull);
    },
  );

  test('native camelcase status normalizes without losing metadata', () async {
    final gateway = _FakeGateway(const [_mangaDex]);
    final source = (await MihonSourceLoader(
      gateway: gateway,
      database: database,
    ).loadSources()).single;
    for (final (status, expected) in [
      ('publishingFinished', PublicationStatus.publishingFinished),
      ('onHiatus', PublicationStatus.onHiatus),
    ]) {
      gateway.seriesManga = MihonMangaItem(
        title: 'Title',
        url: '/manga/$_mangaId',
        status: status,
        rawStatus: 'native original',
        authors: ['Author'],
        artists: ['Artist'],
        genres: ['Genre'],
        rating: 8.5,
      );
      final details = await (source as MangaSeriesSource).loadSeries(
        (await (source as MangaSearchSource).search('example'))
            .results
            .single
            .media
            .source,
      );
      expect(details.metadata.status, expected);
      expect(details.metadata.rawStatus, 'native original');
      expect(details.metadata.artists, ['Artist']);
      expect(details.metadata.rating, 8.5);
    }
  });

  test('metadata maps on loaded series', () async {
    final gateway = _FakeGateway(const [_mangaDex]);
    final source = (await MihonSourceLoader(
      gateway: gateway,
      database: database,
    ).loadSources()).single;
    final result = await (source as MangaSearchSource).search('example');
    expect(result.results.single.metadata, isNotNull);
    final details = await (source as MangaSeriesSource).loadSeries(
      result.results.single.media.source,
    );
    expect(details.metadata.title, 'Example details');
    expect(details.metadata.authors, ['Author']);
  });

  test('extension references preserve opaque continuation state', () async {
    final descriptor = const MihonSourceDescriptor(
      sourceKey: '7',
      name: 'Opaque',
      language: 'en',
      packageName: 'opaque.extension',
      baseUrl: 'https://opaque.test',
    );
    final gateway = _FakeGateway([descriptor]);
    final source =
        (await MihonSourceLoader(
              gateway: gateway,
              database: database,
            ).loadSources()).single
            as MangaSearchSource;

    final result = (await source.search('example')).results.single;
    expect(result.media.source.itemId, startsWith('mihon-v2:'));
    final chapters = (await (source as MangaSeriesSource).loadSeries(
      result.media.source,
    )).chapters;
    expect(gateway.chapterMangaUrl, '/manga/$_mangaId');
    expect(gateway.chapterMangaTitle, 'Example');
    expect(gateway.chapterMangaMemo, '{"seriesId":"123"}');
    expect(chapters.single.source.itemId, startsWith('mihon-v2:'));

    await (source as MangaPageSource).pages(chapters.single.source);
    expect(gateway.pageChapterUrl, '/chapter/$_chapterId');
    expect(gateway.pageChapterTitle, 'Chapter 1');
    expect(gateway.pageChapterNumber, 1);
    expect(gateway.pageChapterScanlator, 'Group');
    expect(gateway.pageChapterDateUpload, 123456789);
    expect(gateway.pageChapterMemo, '{"chapterId":"456"}');
  });

  test('source loader without a platform plugin exposes no sources', () async {
    expect(await MihonSourceLoader(database: database).loadSources(), isEmpty);
  });

  test('stateful extension references reject malformed payloads', () async {
    final gateway = _FakeGateway(const [
      MihonSourceDescriptor(
        sourceKey: '7',
        name: 'Opaque',
        language: 'en',
        packageName: 'opaque.extension',
        baseUrl: 'https://opaque.test',
      ),
    ]);
    final source =
        (await MihonSourceLoader(
              gateway: gateway,
              database: database,
            ).loadSources()).single
            as MangaSeriesSource;

    await expectLater(
      source.loadSeries(
        const SourceMediaRef(
          sourceId: SourceId('mihon:7'),
          itemId: 'mihon-v1:not-base64',
        ),
      ),
      throwsStateError,
    );
  });

  test('stable references reject missing or corrupt continuation', () async {
    final gateway = _FakeGateway(const [_mangaDex]);
    final source = (await MihonSourceLoader(
      gateway: gateway,
      database: database,
    ).loadSources()).single;
    final media = (await (source as MangaSearchSource).search('example'))
        .results
        .single
        .media;
    await database.customStatement(
      'UPDATE mihon_continuation_records SET payload = ?',
      ['broken'],
    );
    await expectLater(
      (source as MangaSeriesSource).loadSeries(media.source),
      throwsStateError,
    );
    await database.delete(database.mihonContinuationRecords).go();
    await expectLater(
      (source as MangaSeriesSource).loadSeries(media.source),
      throwsStateError,
    );
  });

  test(
    'continuation cannot redirect stable identity or change resource kind',
    () async {
      final gateway = _FakeGateway(const [_mangaDex]);
      final source = (await MihonSourceLoader(
        gateway: gateway,
        database: database,
      ).loadSources()).single;
      final media = (await (source as MangaSearchSource).search('example'))
          .results
          .single
          .media;
      for (final state in [
        {'kind': 'manga', 'url': '/different'},
        {'kind': 'chapter', 'url': '/manga/$_mangaId'},
        {'kind': 'manga', 'url': ''},
      ]) {
        final payload =
            'mihon-v1:${base64Url.encode(utf8.encode(jsonEncode(state)))}';
        await database.customStatement(
          'UPDATE mihon_continuation_records SET payload = ?',
          [payload],
        );
        await expectLater(
          (source as MangaSeriesSource).loadSeries(media.source),
          throwsStateError,
        );
      }
      for (final itemId in ['raw-url', 'mihon-v2:not-base64']) {
        await expectLater(
          (source as MangaSeriesSource).loadSeries(
            SourceMediaRef(sourceId: media.source.sourceId, itemId: itemId),
          ),
          throwsStateError,
        );
      }
      await source.search('example');
      gateway.seriesManga = const MihonMangaItem(
        title: 'Different',
        url: '/different',
      );
      await expectLater(
        (source as MangaSeriesSource).loadSeries(media.source),
        throwsStateError,
      );
    },
  );

  test('adapter rejects references from another source', () async {
    final gateway = _FakeGateway(const [_mangaDex]);
    final source =
        (await MihonSourceLoader(
              gateway: gateway,
              database: database,
            ).loadSources()).single
            as MangaPageSource;

    await expectLater(
      source.pages(
        const SourceMediaRef(sourceId: SourceId.local, itemId: _chapterId),
      ),
      throwsArgumentError,
    );
  });
}
