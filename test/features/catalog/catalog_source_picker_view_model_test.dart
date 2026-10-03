import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/catalog/resolve_catalog_source.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/features/catalog/catalog_source_picker_view_model.dart';

void main() {
  test(
    'thirty concrete variants collapse; global language restores exact IDs',
    () {
      final model = _modelWithSources([
        for (var i = 0; i < 30; i++)
          _MangaSource(
            id: SourceId('test:$i'),
            name: 'MangaDex',
            languageCode: 'l$i',
            presentationGroupId: 'package:dex',
          ),
      ], title: 'Title');
      addTearDown(model.dispose);
      expect(model.state.sourceGroups, hasLength(1));
      expect(model.state.sourceGroups.single, hasLength(30));
      model.chooseGroup(model.state.sources.first);
      expect(model.variantsForSelectedGroup, hasLength(30));
      expect(model.handleSystemBack(), isTrue);
      expect(model.state.status, CatalogSourcePickerStatus.choosing);
      model.selectLanguage('L29');
      expect(model.state.visibleSources.single.id, const SourceId('test:29'));
      expect(model.state.sourceGroups.single, hasLength(1));
    },
  );

  test(
    'group identity and name must agree; unknown and all remain separate',
    () {
      final model = _modelWithSources([
        _MangaSource(
          id: const SourceId('a'),
          name: 'Same',
          languageCode: 'en',
          presentationGroupId: 'one',
        ),
        _MangaSource(
          id: const SourceId('b'),
          name: 'Same',
          languageCode: 'fr',
          presentationGroupId: 'one',
        ),
        _MangaSource(
          id: const SourceId('c'),
          name: 'Other',
          languageCode: 'ja',
          presentationGroupId: 'one',
        ),
        _MangaSource(
          id: const SourceId('d'),
          name: 'Same',
          languageCode: 'de',
          presentationGroupId: 'two',
        ),
        _MangaSource(
          id: const SourceId('e'),
          name: 'Same',
          languageCode: 'all',
          presentationGroupId: 'one',
        ),
        _MangaSource(
          id: const SourceId('f'),
          name: 'Same',
          presentationGroupId: 'one',
        ),
        _MangaSource(id: const SourceId('g'), name: 'Same', languageCode: 'es'),
      ], title: 'Title');
      addTearDown(model.dispose);
      expect(model.state.sourceGroups.map((group) => group.length), [
        2,
        1,
        1,
        1,
        1,
        1,
      ]);
      model.chooseGroup(model.state.sources.first);
      expect(model.variantsForSelectedGroup.map((source) => source.id), [
        const SourceId('a'),
        const SourceId('b'),
      ]);
    },
  );

  test('duplicate concrete languages never collapse ambiguous variants', () {
    final model = _modelWithSources([
      for (var i = 0; i < 3; i++)
        _MangaSource(
          id: SourceId('test:$i'),
          name: 'Same',
          languageCode: i == 2 ? 'fr' : 'en',
          presentationGroupId: 'one',
        ),
    ], title: 'Title');
    addTearDown(model.dispose);
    expect(model.state.sourceGroups, hasLength(3));
    model.selectLanguage('en');
    expect(model.state.visibleSources.map((source) => source.id).toSet(), {
      const SourceId('test:0'),
      const SourceId('test:1'),
    });
  });

  test(
    'exact resolution returns media without exposing candidate choice',
    () async {
      final source = _MangaSource(
        onSearch: (_) async => MangaSearchPage(
          results: [_preview('Frieren')],
          hasNextPage: false,
          page: 1,
        ),
      );
      final model = _model(source, title: 'Frieren');
      addTearDown(model.dispose);

      final resolved = await model.selectSource(model.state.sources.single);

      expect(resolved?.title, 'Frieren');
      expect(model.state.status, CatalogSourcePickerStatus.resolving);
      expect(model.state.candidates, isEmpty);
    },
  );

  test('filters sources by normalized provider language', () {
    final model = _modelWithSources([
      _MangaSource(
        id: const SourceId('test:en'),
        name: 'English',
        languageCode: 'EN_us',
      ),
      _MangaSource(
        id: const SourceId('test:fr'),
        name: 'French',
        languageCode: 'fr',
      ),
      _MangaSource(id: const SourceId('test:unknown'), name: 'Unknown'),
    ], title: 'Title');
    addTearDown(model.dispose);

    expect(model.state.languageCodes, ['en-us', 'fr']);
    expect(model.state.visibleSources, hasLength(3));
    model.selectLanguage('en_us');
    expect(model.state.selectedLanguage, 'en-us');
    expect(model.state.visibleSources.map((source) => source.id), [
      const SourceId('test:en'),
    ]);
    model.selectLanguage(null);
    expect(model.state.visibleSources, hasLength(3));
  });

  test('language codes exclude unknown and all-language sources', () {
    final model = _modelWithSources([
      _MangaSource(
        id: const SourceId('test:all'),
        name: 'All',
        languageCode: 'all',
      ),
      _MangaSource(id: const SourceId('test:unknown'), name: 'Unknown'),
      _MangaSource(
        id: const SourceId('test:en'),
        name: 'English',
        languageCode: 'en',
      ),
    ], title: 'Title');
    addTearDown(model.dispose);

    expect(model.state.languageCodes, ['en']);
    model.selectLanguage('all');
    expect(model.state.selectedLanguage, isNull);
  });

  test('language chips hidden when fewer than two languages are known', () {
    final model = _modelWithSources([
      _MangaSource(
        id: const SourceId('test:en'),
        name: 'English',
        languageCode: 'en',
      ),
      _MangaSource(id: const SourceId('test:unknown'), name: 'Unknown'),
    ], title: 'Title');
    addTearDown(model.dispose);

    expect(model.state.showLanguageFilter, isFalse);
    expect(model.state.visibleSources, hasLength(2));
    expect(model.state.languageCodes, ['en']);
  });

  test('language filter invalidates pending source resolution', () async {
    final pending = Completer<MangaSearchPage>();
    final english = _MangaSource(
      id: const SourceId('test:en'),
      name: 'English',
      languageCode: 'en',
      onSearch: (_) => pending.future,
    );
    final french = _MangaSource(
      id: const SourceId('test:fr'),
      name: 'French',
      languageCode: 'fr',
    );
    final model = _modelWithSources([english, french], title: 'Title');
    addTearDown(model.dispose);

    final resolution = model.selectSource(model.state.sources.first);
    await Future<void>.delayed(Duration.zero);
    model.selectLanguage('fr');
    pending.complete(
      MangaSearchPage(
        results: [_preview('Title', sourceId: english.id)],
        hasNextPage: false,
        page: 1,
      ),
    );
    await resolution;

    expect(model.state.status, CatalogSourcePickerStatus.choosing);
    expect(model.state.selectedLanguage, 'fr');
    expect(model.state.visibleSources.single.id, french.id);
  });

  test(
    'empty result and failed search have distinct recovery states',
    () async {
      var fail = true;
      final model = _model(
        _MangaSource(
          onSearch: (_) async {
            if (fail) throw StateError('offline');
            return _emptySearch('Title');
          },
        ),
        title: 'Title',
      );
      addTearDown(model.dispose);
      await model.selectSource(model.state.sources.single);
      expect(model.state.status, CatalogSourcePickerStatus.error);
      fail = false;
      await model.retry();
      expect(model.state.status, CatalogSourcePickerStatus.empty);
      model.chooseAnotherSource();
      expect(model.state.status, CatalogSourcePickerStatus.choosing);
    },
  );

  test('no compatible sources stays distinct from an empty match', () {
    final model = _modelWithSources([], title: 'Title');
    addTearDown(model.dispose);
    expect(model.state.sources, isEmpty);
    expect(model.state.status, CatalogSourcePickerStatus.choosing);
    expect(model.state.showLanguageFilter, isFalse);
  });

  test('disposing picker prevents pending exact match from opening', () async {
    final pending = Completer<MangaSearchPage>();
    final model = _model(
      _MangaSource(onSearch: (_) => pending.future),
      title: 'Title',
    );
    final result = model.selectSource(model.state.sources.single);
    model.dispose();
    pending.complete(
      MangaSearchPage(
        results: [_preview('Title')],
        hasNextPage: false,
        page: 1,
      ),
    );
    expect(await result, isNull);
  });

  test('ambiguous results remain visible for user confirmation', () async {
    final source = _MangaSource(
      onSearch: (_) async => MangaSearchPage(
        results: [
          _preview('One Piece', itemId: 'a'),
          _preview('One Piece', itemId: 'b'),
        ],
        hasNextPage: false,
        page: 1,
      ),
    );
    final model = _model(source, title: 'One Piece');
    addTearDown(model.dispose);

    final resolved = await model.selectSource(model.state.sources.single);

    expect(resolved, isNull);
    expect(model.state.status, CatalogSourcePickerStatus.candidates);
    expect(model.state.candidates, hasLength(2));
  });

  test('stale resolution cannot replace a newer source choice', () async {
    final first = Completer<MangaSearchPage>();
    final slow = _MangaSource(
      id: const SourceId('test:slow'),
      name: 'Slow',
      onSearch: (_) => first.future,
    );
    final fast = _MangaSource(
      id: const SourceId('test:fast'),
      name: 'Fast',
      onSearch: (_) async => MangaSearchPage(
        results: [_preview('Title', sourceId: const SourceId('test:fast'))],
        hasNextPage: false,
        page: 1,
      ),
    );
    final registry = SourceRegistry([slow, fast]);
    final resolver = ResolveCatalogSource(
      searchManga: SearchManga(registry),
      searchNovels: SearchNovels(registry),
    );
    final model = CatalogSourcePickerViewModel(
      entry: _entry('Title'),
      resolver: resolver,
    );
    addTearDown(model.dispose);

    final slowPending = model.selectSource(model.state.sources.first);
    await Future<void>.delayed(Duration.zero);
    final fastResolved = await model.selectSource(model.state.sources.last);
    first.complete(
      MangaSearchPage(
        results: [_preview('Old', sourceId: const SourceId('test:slow'))],
        hasNextPage: false,
        page: 1,
      ),
    );
    await slowPending;

    expect(fastResolved?.source.sourceId, const SourceId('test:fast'));
    expect(model.state.selectedSource?.id, const SourceId('test:fast'));
    expect(model.state.status, CatalogSourcePickerStatus.resolving);
  });
}

CatalogSourcePickerViewModel _modelWithSources(
  List<_MangaSource> sources, {
  required String title,
}) {
  final registry = SourceRegistry(sources);
  return CatalogSourcePickerViewModel(
    entry: _entry(title),
    resolver: ResolveCatalogSource(
      searchManga: SearchManga(registry),
      searchNovels: SearchNovels(registry),
    ),
  );
}

Future<MangaSearchPage> _emptySearch(String query) async =>
    MangaSearchPage(results: const [], hasNextPage: false, page: 1);

CatalogSourcePickerViewModel _model(
  _MangaSource source, {
  required String title,
}) {
  final registry = SourceRegistry([source]);
  return CatalogSourcePickerViewModel(
    entry: _entry(title),
    resolver: ResolveCatalogSource(
      searchManga: SearchManga(registry),
      searchNovels: SearchNovels(registry),
    ),
  );
}

CatalogEntry _entry(String title) => CatalogEntry(
  id: const CatalogEntryId(provider: 'test', value: '1'),
  title: title,
  type: MediaType.manga,
);

MangaPreview _preview(
  String title, {
  String? itemId,
  SourceId sourceId = const SourceId('test:manga'),
}) => MangaPreview(
  media: Media(
    title: title,
    type: MediaType.manga,
    source: SourceMediaRef(sourceId: sourceId, itemId: itemId ?? title),
  ),
);

final class _MangaSource
    implements MangaSearchSource, MangaPageSource, MediaSourcePresentation {
  _MangaSource({
    this.id = const SourceId('test:manga'),
    this.name = 'Manga source',
    this.languageCode,
    this.presentationGroupId,
    Future<MangaSearchPage> Function(String query)? onSearch,
  }) : onSearch = onSearch ?? _emptySearch;

  @override
  final SourceId id;

  @override
  final String name;

  @override
  final String? languageCode;

  @override
  String get displayName => name;

  @override
  final String? presentationGroupId;

  final Future<MangaSearchPage> Function(String query) onSearch;

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) =>
      onSearch(query);

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}
