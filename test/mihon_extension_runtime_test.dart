import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/mihon/mihon_extension_gateway.dart';
import 'package:hikari/infrastructure/mihon/mihon_extension_runtime.dart';

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
  test(
    'runtime adapts installed extension sources to Hikari media sources',
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

      final sources = await MihonExtensionRuntime(gateway: gateway)
          .loadSources();

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
    'MangaDex English preserves legacy Hikari identity end to end',
    () async {
      final gateway = _FakeGateway(const [_mangaDex]);
      final source =
          (await MihonExtensionRuntime(gateway: gateway).loadSources()).single
              as MangaSearchSource;

      expect(source.id, const SourceId('mangadex'));

      final search = await source.search('example');
      expect(gateway.searchedSource, _mangaDex.sourceKey);
      expect(
        search.single.source,
        const SourceMediaRef(sourceId: SourceId('mangadex'), itemId: _mangaId),
      );

      final chapterSource = source as MangaChapterSource;
      final chapters = await chapterSource.chapters(search.single.source);
      expect(gateway.chapterMangaUrl, '/manga/$_mangaId');
      expect(gateway.chapterMangaTitle, isNull);
      expect(gateway.chapterMangaMemo, isNull);
      expect(
        chapters.single.source,
        const SourceMediaRef(
          sourceId: SourceId('mangadex'),
          itemId: _chapterId,
        ),
      );
      expect(chapters.single.scanlator, 'Group');

      final pageSource = source as MangaPageSource;
      final pages = await pageSource.pages(chapters.single.source);
      expect(gateway.pageChapterUrl, '/chapter/$_chapterId');
      expect(gateway.pageChapterTitle, isNull);
      expect(gateway.pageChapterNumber, isNull);
      expect(gateway.pageChapterScanlator, isNull);
      expect(gateway.pageChapterDateUpload, isNull);
      expect(gateway.pageChapterMemo, isNull);
      expect(pages, hasLength(1));
      expect(pages.single.sourceId, const SourceId('mangadex'));

      expect(await pageSource.readPage(pages.single), [1, 2, 3]);
      expect(gateway.readPageItem?.index, 0);
      expect(gateway.readPageItem?.url, 'https://example.test/page/0');
      expect(gateway.readPageItem?.imageUrl, 'https://cdn.example.test/0.jpg');
      expect(gateway.readPageItem?.uri, isNull);
    },
  );

  test('non-MangaDex extension references remain opaque', () async {
    final descriptor = const MihonSourceDescriptor(
      sourceKey: '7',
      name: 'Opaque',
      language: 'en',
      packageName: 'opaque.extension',
      baseUrl: 'https://opaque.test',
    );
    final gateway = _FakeGateway([descriptor]);
    final source =
        (await MihonExtensionRuntime(gateway: gateway).loadSources()).single
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

  test(
    'MangaDex compatibility alias requires the legacy upstream source id',
    () async {
      final gateway = _FakeGateway(const [
        MihonSourceDescriptor(
          sourceKey: '99',
          name: 'MangaDex',
          language: 'en',
          packageName: 'eu.kanade.tachiyomi.extension.all.mangadex',
          baseUrl: 'https://mangadex.org',
        ),
      ]);

      final source = (await MihonExtensionRuntime(
        gateway: gateway,
      ).loadSources()).single;

      expect(source.id, const SourceId('mihon:99'));
    },
  );

  test(
    'MangaDex compatibility alias requires the official package identity',
    () async {
      final gateway = _FakeGateway(const [
        MihonSourceDescriptor(
          sourceKey: '99',
          name: 'MangaDex',
          language: 'en',
          packageName: 'example.spoofed.mangadex',
          baseUrl: 'https://mangadex.org',
        ),
      ]);

      final source = (await MihonExtensionRuntime(
        gateway: gateway,
      ).loadSources()).single;

      expect(source.id, const SourceId('mihon:99'));
    },
  );

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
        (await MihonExtensionRuntime(gateway: gateway).loadSources()).single
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
        (await MihonExtensionRuntime(gateway: gateway).loadSources()).single
            as MangaPageSource;

    await expectLater(
      source.pages(
        const SourceMediaRef(sourceId: SourceId.local, itemId: _chapterId),
      ),
      throwsArgumentError,
    );
  });
}
