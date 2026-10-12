import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_detail_view_model.dart';
import 'package:hikari/features/home/home_view_model.dart';

void main() {
  test(
    'Home displays stale discovery while refreshing and keeps it offline',
    () async {
      final provider = _SnapshotProvider()..fresh = false;
      final model = HomeViewModel(
        null,
        discoverCatalog: DiscoverCatalog(provider),
      );
      addTearDown(model.dispose);
      await _drain();
      expect(
        model
            .state
            .catalogDiscovery
            ?.sections[CatalogSection.featured]
            ?.single
            .title,
        'Cached',
      );
      expect(provider.refreshCalls, 1);
      provider.completeDiscoveryError();
      await _drain();
      expect(model.state.catalogError, isNotNull);
      expect(
        model
            .state
            .catalogDiscovery
            ?.sections[CatalogSection.featured]
            ?.single
            .title,
        'Cached',
      );

      final refresh = model.reloadCatalog(forceRefresh: true);
      await _drain();
      expect(provider.refreshCalls, 2);
      provider.completeDiscovery('Updated');
      await refresh;
      expect(model.state.catalogError, isNull);
      expect(
        model
            .state
            .catalogDiscovery
            ?.sections[CatalogSection.featured]
            ?.single
            .title,
        'Updated',
      );
    },
  );

  test(
    'fresh Home discovery avoids network except on explicit refresh',
    () async {
      final provider = _SnapshotProvider()..fresh = true;
      final model = HomeViewModel(
        null,
        discoverCatalog: DiscoverCatalog(provider),
      );
      addTearDown(model.dispose);
      await _drain();
      expect(provider.refreshCalls, 0);
      expect(model.state.catalogDiscovery, isNotNull);
      final manual = model.refresh();
      await _drain();
      expect(provider.refreshCalls, 1);
      provider.completeDiscovery('Manual');
      await manual;
      expect(
        model
            .state
            .catalogDiscovery
            ?.sections[CatalogSection.featured]
            ?.single
            .title,
        'Manual',
      );
    },
  );

  test(
    'Detail uses stale cached content and reports failed revalidation',
    () async {
      final provider = _SnapshotProvider()..fresh = false;
      final model = CatalogDetailViewModel(
        initialEntry: _entry('Initial'),
        loadDetails: LoadCatalogEntryDetails(provider),
      );
      addTearDown(model.dispose);

      final first = model.load();
      await _drain();
      expect(model.state.details?.entry.title, 'Cached');
      expect(model.state.refreshing, isTrue);
      provider.completeDetailError();
      await first;
      expect(model.state.refreshFailed, isTrue);
      expect(model.state.details?.entry.title, 'Cached');

      final retry = model.load(forceRefresh: true);
      await _drain();
      provider.completeDetail('Fresh');
      await retry;
      expect(model.state.entry.title, 'Fresh');
      expect(model.state.refreshFailed, isFalse);
      expect(provider.detailRefreshCalls, 2);
    },
  );

  test('fresh detail shows immediately without remote call', () async {
    final provider = _SnapshotProvider()..fresh = true;
    final model = CatalogDetailViewModel(
      initialEntry: _entry('Initial'),
      loadDetails: LoadCatalogEntryDetails(provider),
    );
    addTearDown(model.dispose);
    await model.load();
    expect(model.state.entry.title, 'Cached');
    expect(provider.detailRefreshCalls, 0);
  });
}

Future<void> _drain() async {
  await Future<void>.delayed(Duration.zero);
  await Future<void>.delayed(Duration.zero);
}

const _id = CatalogEntryId(provider: 'anilist', value: '42');
CatalogEntry _entry(String title) =>
    CatalogEntry(id: _id, title: title, type: MediaType.anime);
CatalogDiscovery _discovery(String title) => CatalogDiscovery(
  sections: {
    CatalogSection.featured: [_entry(title)],
  },
);

final class _SnapshotProvider
    implements CatalogProvider, CatalogSnapshotProvider {
  bool fresh = false;
  int refreshCalls = 0;
  int detailRefreshCalls = 0;
  final _discoveryWaiters = <Completer<CatalogDiscovery>>[];
  final _detailWaiters = <Completer<CatalogEntryDetails?>>[];

  @override
  String get id => 'anilist';

  @override
  Future<CatalogSnapshot<CatalogDiscovery>?> cachedDiscovery() async =>
      CatalogSnapshot(_discovery('Cached'), isFresh: fresh);

  @override
  Future<CatalogSnapshot<CatalogEntryDetails>?> cachedDetails(
    CatalogEntryId id,
  ) async => CatalogSnapshot(
    CatalogEntryDetails(entry: _entry('Cached')),
    isFresh: fresh,
  );

  @override
  Future<CatalogDiscovery> refreshDiscovery() {
    refreshCalls++;
    final pending = Completer<CatalogDiscovery>();
    _discoveryWaiters.add(pending);
    return pending.future;
  }

  @override
  Future<CatalogEntryDetails?> refreshDetails(CatalogEntryId id) {
    detailRefreshCalls++;
    final pending = Completer<CatalogEntryDetails?>();
    _detailWaiters.add(pending);
    return pending.future;
  }

  void completeDiscovery(String title) =>
      _discoveryWaiters.removeAt(0).complete(_discovery(title));

  void completeDiscoveryError() =>
      _discoveryWaiters.removeAt(0).completeError(StateError('offline'));

  void completeDetail(String title) => _detailWaiters
      .removeAt(0)
      .complete(CatalogEntryDetails(entry: _entry(title)));

  void completeDetailError() =>
      _detailWaiters.removeAt(0).completeError(StateError('offline'));

  @override
  Future<CatalogDiscovery> discover() => refreshDiscovery();

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) =>
      refreshDetails(id);

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async =>
      const [];

  @override
  Future<void> close() async {}
}
