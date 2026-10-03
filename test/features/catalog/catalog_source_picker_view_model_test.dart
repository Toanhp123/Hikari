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
import 'package:hikari/features/catalog/catalog_source_picker_view_model.dart';

void main() {
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
      expect(model.state.status, CatalogSourcePickerStatus.resolved);
      expect(model.state.candidates, isEmpty);
    },
  );

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
    expect(model.state.status, CatalogSourcePickerStatus.resolved);
  });
}

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

final class _MangaSource implements MangaSearchSource, MangaPageSource {
  _MangaSource({
    this.id = const SourceId('test:manga'),
    this.name = 'Manga source',
    required this.onSearch,
  });

  @override
  final SourceId id;

  @override
  final String name;

  final Future<MangaSearchPage> Function(String query) onSearch;

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) =>
      onSearch(query);

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}
