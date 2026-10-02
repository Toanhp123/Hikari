import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_detail_view_model.dart';

void main() {
  const id = CatalogEntryId(provider: 'test', value: '1');
  final entry = CatalogEntry(id: id, title: 'Initial', type: MediaType.anime);

  test('failed refresh keeps the last loaded detail visible', () async {
    final provider = _DetailProvider();
    final model = CatalogDetailViewModel(
      initialEntry: entry,
      loadDetails: LoadCatalogEntryDetails(provider),
    );
    addTearDown(model.dispose);

    provider.result = CatalogEntryDetails(
      entry: CatalogEntry(id: id, title: 'Loaded', type: MediaType.anime),
    );
    await model.load();
    expect(model.state.entry.title, 'Loaded');

    provider.error = StateError('offline');
    await model.load();
    expect(model.state.entry.title, 'Loaded');
    expect(model.state.refreshFailed, isTrue);
  });

  test('newer detail load wins over stale completion', () async {
    final provider = _ControlledDetailProvider();
    final model = CatalogDetailViewModel(
      initialEntry: entry,
      loadDetails: LoadCatalogEntryDetails(provider),
    );
    addTearDown(model.dispose);

    final first = model.load();
    await Future<void>.delayed(Duration.zero);
    final second = model.load();
    await Future<void>.delayed(Duration.zero);

    provider.completeNext('Second');
    await second;
    provider.completeNext('First');
    await first;

    expect(model.state.entry.title, 'Second');
  });
}

class _DetailProvider implements CatalogProvider {
  CatalogEntryDetails? result;
  Object? error;

  @override
  String get id => 'test';

  @override
  Future<CatalogDiscovery> discover() async => CatalogDiscovery(sections: {});

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async =>
      const [];

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) async {
    final failure = error;
    if (failure != null) throw failure;
    return result;
  }

  @override
  Future<void> close() async {}
}

final class _ControlledDetailProvider extends _DetailProvider {
  final pending = <Completer<CatalogEntryDetails?>>[];

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) {
    final completer = Completer<CatalogEntryDetails?>();
    pending.add(completer);
    return completer.future;
  }

  void completeNext(String title) {
    final completer = pending.removeAt(pending.length - 1);
    completer.complete(
      CatalogEntryDetails(
        entry: CatalogEntry(
          id: const CatalogEntryId(provider: 'test', value: '1'),
          title: title,
          type: MediaType.anime,
        ),
      ),
    );
  }
}
