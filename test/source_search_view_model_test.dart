import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/features/source_search/source_search_view_model.dart';

void main() {
  test(
    'partial failure with empty successes stays empty; all failures error',
    () async {
      final partial = SourceSearchViewModel(
        searchManga: SearchManga(
          SourceRegistry([_MangaSource(), _FailingMangaSource()]),
        ),
        initialFilter: SourceSearchFilter.manga,
      );
      final failed = SourceSearchViewModel(
        searchManga: SearchManga(SourceRegistry([_FailingMangaSource()])),
        initialFilter: SourceSearchFilter.manga,
      );
      addTearDown(partial.dispose);
      addTearDown(failed.dispose);
      await partial.search('missing');
      expect(partial.state.status, SourceSearchStatus.empty);
      expect(partial.state.failedSourceCount, 1);
      await failed.search('missing');
      expect(failed.state.status, SourceSearchStatus.error);
      expect(failed.state.failedSourceCount, 1);
    },
  );

  const media = Media(
    title: 'Solo Leveling',
    type: MediaType.anime,
    source: SourceMediaRef(sourceId: SourceId.local, itemId: 'solo'),
  );

  test(
    'local catalog scans once across queries in one view-model session',
    () async {
      var scans = 0;
      final model = SourceSearchViewModel(
        scanLocalMedia: () async {
          scans++;
          return const [media];
        },
      );
      addTearDown(model.dispose);

      await model.search('Solo');
      expect(model.state.results.single.media, media);

      await model.search('Leveling');
      expect(model.state.results.single.media, media);
      expect(scans, 1);
    },
  );

  test('media type filter scopes local results', () async {
    const manga = Media(
      title: 'Solo Leveling',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'solo-manga'),
    );
    final model = SourceSearchViewModel(
      scanLocalMedia: () async => const [media, manga],
      initialFilter: SourceSearchFilter.manga,
    );
    addTearDown(model.dispose);

    await model.search('Solo');

    expect(model.state.results, hasLength(1));
    expect(model.state.results.single.media, manga);
  });

  test(
    'editing to an empty query invalidates pending source results',
    () async {
      final source = _ControlledMangaSource();
      final registry = SourceRegistry([source]);
      final model = SourceSearchViewModel(
        searchManga: SearchManga(registry),
        initialFilter: SourceSearchFilter.manga,
      );
      addTearDown(model.dispose);

      final pending = model.search('Old');
      await Future<void>.delayed(Duration.zero);
      await model.search('   ');
      source.complete('Old');
      await pending;

      expect(model.state.query, isEmpty);
      expect(model.state.status, SourceSearchStatus.idle);
      expect(model.state.results, isEmpty);
    },
  );

  test('newer source search wins over stale completion', () async {
    final source = _ControlledMangaSource();
    final registry = SourceRegistry([source]);
    final model = SourceSearchViewModel(
      searchManga: SearchManga(registry),
      initialFilter: SourceSearchFilter.manga,
    );
    addTearDown(model.dispose);

    final first = model.search('First');
    await Future<void>.delayed(Duration.zero);
    final second = model.search('Second');
    await Future<void>.delayed(Duration.zero);

    source.complete('Second');
    await second;
    source.complete('First');
    await first;

    expect(model.state.query, 'Second');
    expect(model.state.status, SourceSearchStatus.ready);
    expect(model.state.results.single.media.title, 'Second');
  });

  test('partial source failures keep successful results visible', () async {
    final working = _ResultMangaSource(
      id: const SourceId('test:working'),
      title: 'Working result',
    );
    final failing = _FailingMangaSource();
    final model = SourceSearchViewModel(
      searchManga: SearchManga(SourceRegistry([working, failing])),
      initialFilter: SourceSearchFilter.manga,
    );
    addTearDown(model.dispose);

    await model.search('query');

    expect(model.state.status, SourceSearchStatus.ready);
    expect(model.state.failedSourceCount, 1);
    expect(model.state.results.single.media.title, 'Working result');
  });

  test('scoped source search skips other sources and local media', () async {
    final selected = _ResultMangaSource(
      id: const SourceId('test:selected'),
      title: 'Selected result',
    );
    final other = _ResultMangaSource(
      id: const SourceId('test:other'),
      title: 'Other result',
    );
    var scans = 0;
    final model = SourceSearchViewModel(
      searchManga: SearchManga(SourceRegistry([selected, other])),
      scanLocalMedia: () async {
        scans++;
        return const [media];
      },
      initialFilter: SourceSearchFilter.manga,
      sourceId: selected.id,
    );
    addTearDown(model.dispose);

    await model.search('query');

    expect(selected.searches, 1);
    expect(other.searches, 0);
    expect(scans, 0);
    expect(model.state.results.single.media.title, 'Selected result');
  });

  test('source ID allowlist restricts remote searches', () async {
    final selected = _ResultMangaSource(
      id: const SourceId('test:selected'),
      title: 'Selected result',
    );
    final other = _ResultMangaSource(
      id: const SourceId('test:other'),
      title: 'Other result',
    );
    final model = SourceSearchViewModel(
      searchManga: SearchManga(SourceRegistry([selected, other])),
      initialFilter: SourceSearchFilter.manga,
      sourceIds: {selected.id},
    );
    addTearDown(model.dispose);

    await model.search('query');

    expect(selected.searches, 1);
    expect(other.searches, 0);
    expect(model.state.results.single.media.title, 'Selected result');
  });

  test('source result labels use clean multilingual presentation', () async {
    final english = _ResultMangaSource(
      id: const SourceId('mihon:dex:en'),
      title: 'English result',
      displayName: 'MangaDex',
      languageCode: 'EN',
    );
    final vietnamese = _ResultMangaSource(
      id: const SourceId('mihon:dex:vi'),
      title: 'Vietnamese result',
      displayName: 'MangaDex',
      languageCode: 'vi',
    );
    final multilingual = _ResultMangaSource(
      id: const SourceId('mihon:dex:all'),
      title: 'Multilingual result',
      displayName: 'MangaDex',
      languageCode: 'all',
    );
    final model = SourceSearchViewModel(
      searchManga: SearchManga(
        SourceRegistry([english, vietnamese, multilingual]),
      ),
      initialFilter: SourceSearchFilter.manga,
    );
    addTearDown(model.dispose);

    await model.search('query');

    expect(
      model.state.results.map((result) => result.sourceName),
      containsAll([
        'MangaDex · EN',
        'MangaDex · VI',
        'MangaDex · Multiple languages',
      ]),
    );
    expect(
      model.state.results.map((result) => result.sourceName),
      isNot(contains('MangaDex [en]')),
    );
  });

  test('source ID allowlist disables local media scan', () async {
    final source = _ResultMangaSource(
      id: const SourceId('test:selected'),
      title: 'Selected result',
    );
    var scans = 0;
    final model = SourceSearchViewModel(
      searchManga: SearchManga(SourceRegistry([source])),
      scanLocalMedia: () async {
        scans++;
        return const [media];
      },
      initialFilter: SourceSearchFilter.manga,
      sourceIds: {source.id},
    );
    addTearDown(model.dispose);

    await model.search('query');

    expect(source.searches, 1);
    expect(scans, 0);
    expect(model.state.results.single.media.title, 'Selected result');
  });

  test(
    'selected filter limits remote fan-out and reuses local catalog',
    () async {
      final manga = _MangaSource();
      final novel = _NovelSource();
      final registry = SourceRegistry([manga, novel]);
      var scans = 0;
      final model = SourceSearchViewModel(
        searchManga: SearchManga(registry),
        searchNovels: SearchNovels(registry),
        scanLocalMedia: () async {
          scans++;
          return const [media];
        },
        initialFilter: SourceSearchFilter.anime,
      );
      addTearDown(model.dispose);

      await model.search('Solo');
      expect(manga.searches, 0);
      expect(novel.searches, 0);
      expect(scans, 1);

      await model.selectFilter(SourceSearchFilter.manga);
      expect(manga.searches, 1);
      expect(novel.searches, 0);
      expect(scans, 1);

      await model.selectFilter(SourceSearchFilter.novel);
      expect(manga.searches, 1);
      expect(novel.searches, 1);
      expect(scans, 1);

      await model.selectFilter(SourceSearchFilter.all);
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

final class _ControlledMangaSource
    implements MangaSearchSource, MangaPageSource {
  final _pending = <String, Completer<MangaSearchPage>>{};

  @override
  SourceId get id => const SourceId('test:controlled');

  @override
  String get name => 'Controlled manga';

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) =>
      (_pending[query] ??= Completer<MangaSearchPage>()).future;

  void complete(String query) {
    _pending[query]!.complete(
      MangaSearchPage(
        results: [
          MangaPreview(
            media: Media(
              title: query,
              type: MediaType.manga,
              source: SourceMediaRef(sourceId: id, itemId: query),
            ),
          ),
        ],
        page: 1,
        hasNextPage: false,
      ),
    );
  }

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

final class _ResultMangaSource
    implements MangaSearchSource, MangaPageSource, MediaSourcePresentation {
  _ResultMangaSource({
    required this.id,
    required this.title,
    this.displayName = 'Working manga',
    this.languageCode,
  });

  @override
  final SourceId id;
  final String title;
  @override
  final String displayName;
  @override
  final String? languageCode;
  @override
  String? get presentationGroupId => null;
  int searches = 0;

  @override
  String get name => languageCode == null
      ? displayName
      : '$displayName [${languageCode!.toLowerCase()}]';

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async {
    searches++;
    return MangaSearchPage(
      results: [
        MangaPreview(
          media: Media(
            title: title,
            type: MediaType.manga,
            source: SourceMediaRef(sourceId: id, itemId: title),
          ),
        ),
      ],
      page: page,
      hasNextPage: false,
    );
  }

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

final class _FailingMangaSource implements MangaSearchSource, MangaPageSource {
  @override
  SourceId get id => const SourceId('test:failing');

  @override
  String get name => 'Failing manga';

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async {
    throw StateError('offline');
  }

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}
