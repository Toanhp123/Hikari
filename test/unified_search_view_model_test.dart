import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/search/unified_search_view_model.dart';

void main() {
  const media = Media(
    title: 'Solo Leveling',
    type: MediaType.anime,
    source: SourceMediaRef(sourceId: SourceId.local, itemId: 'solo'),
  );

  test(
    'local catalog scans once across queries in one view-model session',
    () async {
      var scans = 0;
      final model = UnifiedSearchViewModel(
        scanLocalMedia: () async {
          scans++;
          return const [media];
        },
      );
      addTearDown(model.dispose);

      await model.search('Solo');
      expect(model.state.visibleResults.single.media, media);

      await model.search('Leveling');
      expect(model.state.visibleResults.single.media, media);
      expect(scans, 1);
    },
  );

  test('media type filter scopes local results', () async {
    const manga = Media(
      title: 'Solo Leveling',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'solo-manga'),
    );
    final model = UnifiedSearchViewModel(
      scanLocalMedia: () async => const [media, manga],
      initialFilter: SearchMediaTypeFilter.manga,
    );
    addTearDown(model.dispose);

    await model.search('Solo');

    expect(model.state.results, hasLength(1));
    expect(model.state.results.single.media, manga);
  });

  test(
    'selected filter limits remote fan-out and reuses local catalog',
    () async {
      final manga = _MangaSource();
      final novel = _NovelSource();
      final registry = SourceRegistry([manga, novel]);
      var scans = 0;
      final model = UnifiedSearchViewModel(
        searchManga: SearchManga(registry),
        searchNovels: SearchNovels(registry),
        scanLocalMedia: () async {
          scans++;
          return const [media];
        },
        initialFilter: SearchMediaTypeFilter.anime,
      );
      addTearDown(model.dispose);

      await model.search('Solo');
      expect(manga.searches, 0);
      expect(novel.searches, 0);
      expect(scans, 1);

      await model.selectFilter(SearchMediaTypeFilter.manga);
      expect(manga.searches, 1);
      expect(novel.searches, 0);
      expect(scans, 1);

      await model.selectFilter(SearchMediaTypeFilter.novel);
      expect(manga.searches, 1);
      expect(novel.searches, 1);
      expect(scans, 1);

      await model.selectFilter(SearchMediaTypeFilter.all);
      expect(manga.searches, 2);
      expect(novel.searches, 2);
      expect(scans, 1);
    },
  );
}

final class _MangaSource implements MangaSearchSource, MangaPageSource {
  int searches = 0;

  @override
  SourceId get id => const SourceId('test:manga');

  @override
  String get name => 'Test manga';

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async {
    searches++;
    return MangaSearchPage(results: const [], hasNextPage: false, page: page);
  }

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

final class _NovelSource
    implements NovelSearchSource, NovelSeriesSource, NovelChapterSource {
  int searches = 0;

  @override
  SourceId get id => const SourceId('test:novel');

  @override
  String get name => 'Test novel';

  @override
  Future<NovelSearchPage> search(String query, {int page = 1}) async {
    searches++;
    return NovelSearchPage(results: const [], page: page, hasNextPage: false);
  }

  @override
  Future<NovelDetails> loadDetails(SourceMediaRef novel) async => NovelDetails(
    metadata: MediaMetadata(title: 'Novel'),
    chapters: [],
  );

  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async =>
      RichReadingContent(html: '');

  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
}
