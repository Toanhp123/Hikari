import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/core/cache/cache_maintenance.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';

void main() {
  test('owned cache close retries when disposal retries', () async {
    final db = UserDatabase(NativeDatabase.memory());
    addTearDown(db.close);
    final cache = _TrackedCache()..failClose = true;
    final dependencies = AppDependencies.create(
      database: db,
      cache: cache,
      ownsCache: true,
    );
    await expectLater(dependencies.dispose(), throwsStateError);
    cache.failClose = false;
    await dependencies.dispose();
    expect(cache.closeCalls, 2);
  });

  test('default app cache advertises scoped maintenance', () async {
    final db = UserDatabase(NativeDatabase.memory());
    addTearDown(db.close);
    final dependencies = AppDependencies.create(database: db);
    expect(dependencies.cacheManagement, isNotNull);
    await dependencies.dispose();
  });

  test(
    'composition root registers cache sizes and clear from one policy',
    () async {
      final db = UserDatabase(NativeDatabase.memory());
      addTearDown(db.close);
      final cache = _TrackedMaintenanceCache();
      final dependencies = AppDependencies.create(database: db, cache: cache);
      addTearDown(dependencies.dispose);

      final usage = await dependencies.cacheManagement!.usage();
      expect(usage.byCategory.keys.toList(), [
        'Source artwork',
        'Manga pages',
        'Manga page lists',
        'Remote chapter lists',
        'Novel chapters',
        'Novel resources',
        'Catalog metadata',
      ]);
      expect(usage.totalBytes, 7);
      expect(cache.sizedNamespaces, [
        'source-artwork-v1',
        'manga-page-v1',
        'manga-page-list-v1',
        'remote-chapter-list-v1',
        'novel-chapter-content-v1',
        'novel-resource-v1',
        'anilist-metadata',
      ]);

      await dependencies.cacheManagement!.clear();
      expect(cache.clearedNamespaces, cache.sizedNamespaces);
      expect(cache.closed, isFalse);
    },
  );

  test(
    'AppDependencies manages catalog provider ownership and dispose semantics',
    () async {
      final db = UserDatabase(NativeDatabase.memory());
      addTearDown(db.close);

      // Caller-owned (default when injected)
      final externalProvider = _TrackedCatalogProvider();
      final callerOwned = AppDependencies.create(
        database: db,
        catalogProvider: externalProvider,
      );
      expect(callerOwned.ownsCatalogProvider, isFalse);
      await callerOwned.dispose();
      expect(externalProvider.closeCalls, 0);

      // Explicitly transferred ownership
      final ownedProvider = _TrackedCatalogProvider();
      final appOwned = AppDependencies.create(
        database: db,
        catalogProvider: ownedProvider,
        ownsCatalogProvider: true,
      );
      expect(appOwned.ownsCatalogProvider, isTrue);
      await appOwned.dispose();
      expect(ownedProvider.closeCalls, 1);

      // Injected cache stays caller-owned unless ownership transferred.
      final callerCache = _TrackedCache();
      final cacheCallerOwned = AppDependencies.create(
        database: db,
        cache: callerCache,
      );
      expect(cacheCallerOwned.ownsCache, isFalse);
      expect(cacheCallerOwned.cacheManagement, isNull);
      await cacheCallerOwned.dispose();
      expect(callerCache.closeCalls, 0);

      final ownedCache = _TrackedCache();
      final cacheAppOwned = AppDependencies.create(
        database: db,
        cache: ownedCache,
        ownsCache: true,
      );
      expect(cacheAppOwned.ownsCache, isTrue);
      expect(cacheAppOwned.cacheManagement, isNull);
      await cacheAppOwned.dispose();
      expect(ownedCache.closeCalls, 1);
      await cacheAppOwned.dispose();
      expect(ownedCache.closeCalls, 1);
    },
  );
}

final class _TrackedCache implements ByteCache {
  int closeCalls = 0;
  @override
  Future<Uint8List?> read(String namespace, String key) async => null;
  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {}
  @override
  Future<void> close() async {
    closeCalls++;
    if (failClose) throw StateError('close failed');
  }

  bool failClose = false;
}

final class _TrackedCatalogProvider implements CatalogProvider {
  int closeCalls = 0;

  @override
  String get id => 'tracked';

  @override
  Future<CatalogDiscovery> discover() async =>
      CatalogDiscovery(sections: const {});

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async =>
      const [];

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) async => null;

  @override
  Future<void> close() async {
    closeCalls++;
  }
}

final class _TrackedMaintenanceCache implements ByteCache, CacheMaintenance {
  List<String> sizedNamespaces = [];
  List<String> clearedNamespaces = [];
  bool closed = false;

  @override
  Future<Uint8List?> read(String namespace, String key) async => null;

  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {}

  @override
  Future<Map<String, int>> sizes(Iterable<String> namespaces) async {
    sizedNamespaces = List.of(namespaces);
    return {for (final namespace in sizedNamespaces) namespace: 1};
  }

  @override
  Future<void> clearNamespaces(Iterable<String> namespaces) async {
    clearedNamespaces = List.of(namespaces);
  }

  @override
  Future<void> close() async {
    closed = true;
  }
}
