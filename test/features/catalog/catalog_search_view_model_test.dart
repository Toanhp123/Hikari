import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/catalog/search_catalog.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_search_view_model.dart';

void main() {
  test('filter changes rerun only the submitted query', () async {
    final provider = _CatalogProvider();
    final model = CatalogSearchViewModel(SearchCatalog(provider));
    addTearDown(model.dispose);

    model.updateQuery('Frieren');
    await model.submitQuery('Frieren');
    expect(provider.calls, [('Frieren', null)]);

    await model.selectFilter(CatalogSearchFilter.manga);
    expect(provider.calls.last, ('Frieren', MediaType.manga));

    model.updateQuery('Bleach');
    await model.selectFilter(CatalogSearchFilter.anime);
    expect(provider.calls.length, 2);
    expect(model.state.status, CatalogSearchStatus.idle);
  });

  test('editing input invalidates a pending submitted search', () async {
    final provider = _ControlledCatalogProvider();
    final model = CatalogSearchViewModel(SearchCatalog(provider));
    addTearDown(model.dispose);

    final pending = model.submitQuery('First');
    model.updateQuery('Second');
    provider.complete('First');
    await pending;

    expect(model.state.inputQuery, 'Second');
    expect(model.state.status, CatalogSearchStatus.idle);
    expect(model.state.entries, isEmpty);
  });

  test('stale search result cannot replace the latest query', () async {
    final provider = _ControlledCatalogProvider();
    final model = CatalogSearchViewModel(SearchCatalog(provider));
    addTearDown(model.dispose);

    final first = model.submitQuery('First');
    final second = model.submitQuery('Second');
    provider.complete('Second');
    await second;
    provider.complete('First');
    await first;

    expect(model.state.submittedQuery, 'Second');
    expect(model.state.entries.single.title, 'Second');
  });
}

class _CatalogProvider implements CatalogProvider {
  final calls = <(String, MediaType?)>[];

  @override
  String get id => 'test';

  @override
  Future<CatalogDiscovery> discover() async => CatalogDiscovery(sections: {});

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async {
    calls.add((query, type));
    return [
      CatalogEntry(
        id: CatalogEntryId(provider: id, value: query),
        title: query,
        type: type ?? MediaType.anime,
      ),
    ];
  }

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) async => null;

  @override
  Future<void> close() async {}
}

final class _ControlledCatalogProvider extends _CatalogProvider {
  final pending = <String, Completer<List<CatalogEntry>>>{};

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) {
    calls.add((query, type));
    return (pending[query] ??= Completer<List<CatalogEntry>>()).future;
  }

  void complete(String query) {
    pending[query]!.complete([
      CatalogEntry(
        id: CatalogEntryId(provider: id, value: query),
        title: query,
        type: MediaType.anime,
      ),
    ]);
  }
}
