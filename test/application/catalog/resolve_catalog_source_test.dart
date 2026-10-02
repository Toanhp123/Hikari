import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/application/catalog/resolve_catalog_source.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';

void main() {
  test('lists only compatible sources for the catalog media type', () {
    final manga = _MangaSource();
    final novel = _NovelSource();
    final resolver = _resolver(manga, novel);

    expect(resolver.optionsFor(MediaType.manga).map((option) => option.id), [
      manga.id,
    ]);
    expect(
      resolver.optionsFor(MediaType.lightNovel).map((option) => option.id),
      [novel.id],
    );
    expect(resolver.optionsFor(MediaType.anime), isEmpty);
  });

  test('auto resolves one normalized exact title match', () async {
    final manga = _MangaSource(
      responses: {
        'Frieren: Beyond Journey\'s End': [
          _manga('Frieren Beyond Journeys End'),
          _manga('Frieren Anthology'),
        ],
      },
    );
    final resolver = _resolver(manga);
    final entry = _entry('Frieren: Beyond Journey\'s End');

    final result = await resolver.execute(entry: entry, sourceId: manga.id);

    expect(result.match?.media.title, 'Frieren Beyond Journeys End');
    expect(manga.queries, ['Frieren: Beyond Journey\'s End']);
  });

  test('tries catalog aliases before asking the user to choose', () async {
    final manga = _MangaSource(
      responses: {
        'Frieren': [_manga('Frieren Anthology')],
        'Sousou no Frieren': [_manga('Sousou no Frieren')],
      },
    );
    final resolver = _resolver(manga);
    final entry = _entry('Frieren');
    final details = CatalogEntryDetails(
      entry: entry,
      alternateTitles: ['Sousou no Frieren'],
      synonyms: ['Frieren at the Funeral'],
    );

    final result = await resolver.execute(
      entry: entry,
      details: details,
      sourceId: manga.id,
    );

    expect(result.match?.media.title, 'Sousou no Frieren');
    expect(manga.queries, ['Frieren', 'Sousou no Frieren']);
  });

  test('does not auto open a merely similar title', () async {
    final manga = _MangaSource(
      responses: {
        'One Piece': [_manga('One Piece Episode A')],
      },
    );
    final resolver = _resolver(manga);

    final result = await resolver.execute(
      entry: _entry('One Piece'),
      sourceId: manga.id,
    );

    expect(result.match, isNull);
    expect(result.candidates.single.media.title, 'One Piece Episode A');
  });

  test('keeps ambiguous exact matches for explicit confirmation', () async {
    final first = _manga('One Piece', itemId: 'one-piece-a');
    final second = _manga('One Piece', itemId: 'one-piece-b');
    final manga = _MangaSource(
      responses: {
        'One Piece': [first, second],
      },
    );
    final resolver = _resolver(manga);

    final result = await resolver.execute(
      entry: _entry('One Piece'),
      sourceId: manga.id,
    );

    expect(result.match, isNull);
    expect(
      result.candidates.map((candidate) => candidate.media.source.itemId),
      ['one-piece-a', 'one-piece-b'],
    );
  });
}

ResolveCatalogSource _resolver(_MangaSource manga, [_NovelSource? novel]) {
  final sources = SourceRegistry([manga, ?novel]);
  return ResolveCatalogSource(
    searchManga: SearchManga(sources),
    searchNovels: SearchNovels(sources),
  );
}

CatalogEntry _entry(String title) => CatalogEntry(
  id: const CatalogEntryId(provider: 'test', value: '1'),
  title: title,
  type: MediaType.manga,
);

MangaPreview _manga(String title, {String? itemId}) => MangaPreview(
  media: Media(
    title: title,
    type: MediaType.manga,
    source: SourceMediaRef(
      sourceId: const SourceId('test:manga'),
      itemId: itemId ?? title,
    ),
  ),
);

final class _MangaSource implements MangaSearchSource, MangaPageSource {
  _MangaSource({this._responses = const {}});

  final Map<String, List<MangaPreview>> _responses;
  final List<String> queries = [];

  @override
  SourceId get id => const SourceId('test:manga');

  @override
  String get name => 'Manga source [en]';

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async {
    queries.add(query);
    return MangaSearchPage(
      results: _responses[query] ?? const [],
      hasNextPage: false,
      page: page,
    );
  }

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

final class _NovelSource
    implements NovelSearchSource, NovelSeriesSource, NovelChapterSource {
  @override
  SourceId get id => const SourceId('test:novel');

  @override
  String get name => 'Novel source';

  @override
  Future<NovelSearchPage> search(String query, {int page = 1}) async =>
      NovelSearchPage(results: const [], page: page, hasNextPage: false);

  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async => NovelDetails(
    metadata: MediaMetadata(title: 'Novel'),
    chapters: const [],
  );

  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async =>
      RichReadingContent(html: '');

  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
}
