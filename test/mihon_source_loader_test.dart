import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
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
  String? chapterMangaUrl;
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
  Future<List<MihonMangaItem>> search({
    required String sourceKey,
    required String query,
  }) async {
    searchedSource = sourceKey;
    return const [
      MihonMangaItem(
        title: 'Example',
        url: '/manga/$_mangaId',
        memo: '{"seriesId":"123"}',
      ),
    ];
  }

  @override
  Future<List<MihonChapterItem>> chapters({
    required String sourceKey,
    required String mangaUrl,
    String? mangaTitle,
    String? mangaMemo,
  }) async {
    chapterMangaUrl = mangaUrl;
    chapterMangaTitle = mangaTitle;
    chapterMangaMemo = mangaMemo;
    return const [
      MihonChapterItem(
        title: 'Chapter 1',
        url: '/chapter/$_chapterId',
        scanlator: 'Group',
        chapterNumber: 1,
        dateUpload: 123456789,
        memo: '{"chapterId":"456"}',
      ),
    ];
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

  test(
    'opaque persisted references survive extension absence and reinstall',
    () async {
      final db = UserDatabase(NativeDatabase.memory());
      addTearDown(db.close);
      final gateway = _FakeGateway(const [_mangaDex]);
      final source =
          (await MihonSourceLoader(gateway: gateway).loadSources()).single
              as MangaSearchSource;
      final media = (await source.search('example')).single;
      final chapter = (await (source as MangaChapterSource).chapters(
        media.source,
      )).single;
      expect(
        media.source.sourceId,
        const SourceId('mihon:2499283573021220255'),
      );
      expect(media.source.itemId, startsWith('mihon-v1:'));
      expect(chapter.source.itemId, startsWith('mihon-v1:'));
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

        final sources = await MihonSourceLoader(gateway: gateway).loadSources();
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
          await target.chapterSource.chapters(saved.source);
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

      final sources = await MihonSourceLoader(gateway: gateway).loadSources();

      expect(sources, hasLength(1));
      final source = sources.single;
      expect(source, isA<MangaSearchSource>());
      expect(source, isA<MangaChapterSource>());
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
          (await MihonSourceLoader(gateway: gateway).loadSources()).single
              as MangaSearchSource;

      expect(source.id, const SourceId('mihon:2499283573021220255'));

      final search = await source.search('example');
      expect(gateway.searchedSource, _mangaDex.sourceKey);
      expect(search.single.source.sourceId, source.id);
      expect(search.single.source.itemId, startsWith('mihon-v1:'));

      final chapterSource = source as MangaChapterSource;
      final chapters = await chapterSource.chapters(search.single.source);
      expect(gateway.chapterMangaUrl, '/manga/$_mangaId');
      expect(gateway.chapterMangaTitle, 'Example');
      expect(gateway.chapterMangaMemo, '{"seriesId":"123"}');
      expect(chapters.single.source.sourceId, source.id);
      expect(chapters.single.source.itemId, startsWith('mihon-v1:'));
      expect(chapters.single.scanlator, 'Group');

      final pageSource = source as MangaPageSource;
      final pages = await pageSource.pages(chapters.single.source);
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
        (await MihonSourceLoader(gateway: gateway).loadSources()).single
            as MangaSearchSource;

    final result = (await source.search('example')).single;
    expect(result.source.itemId, startsWith('mihon-v1:'));

    final chapters = await (source as MangaChapterSource).chapters(
      result.source,
    );
    expect(gateway.chapterMangaUrl, '/manga/$_mangaId');
    expect(gateway.chapterMangaTitle, 'Example');
    expect(gateway.chapterMangaMemo, '{"seriesId":"123"}');
    expect(chapters.single.source.itemId, startsWith('mihon-v1:'));

    await (source as MangaPageSource).pages(chapters.single.source);
    expect(gateway.pageChapterUrl, '/chapter/$_chapterId');
    expect(gateway.pageChapterTitle, 'Chapter 1');
    expect(gateway.pageChapterNumber, 1);
    expect(gateway.pageChapterScanlator, 'Group');
    expect(gateway.pageChapterDateUpload, 123456789);
    expect(gateway.pageChapterMemo, '{"chapterId":"456"}');
  });

  test('source loader without a platform plugin exposes no sources', () async {
    expect(await const MihonSourceLoader().loadSources(), isEmpty);
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
        (await MihonSourceLoader(gateway: gateway).loadSources()).single
            as MangaChapterSource;

    await expectLater(
      source.chapters(
        const SourceMediaRef(
          sourceId: SourceId('mihon:7'),
          itemId: 'mihon-v1:not-base64',
        ),
      ),
      throwsStateError,
    );
  });

  test('adapter rejects references from another source', () async {
    final gateway = _FakeGateway(const [_mangaDex]);
    final source =
        (await MihonSourceLoader(gateway: gateway).loadSources()).single
            as MangaPageSource;

    await expectLater(
      source.pages(
        const SourceMediaRef(sourceId: SourceId.local, itemId: _chapterId),
      ),
      throwsArgumentError,
    );
  });
}
