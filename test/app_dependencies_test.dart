import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';

void main() {
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

      // Subsequent dispose is idempotent
      await appOwned.dispose();
      expect(ownedProvider.closeCalls, 1);
    },
  );
}

final class _TrackedCatalogProvider implements CatalogProvider {
  int closeCalls = 0;

  @override
  String get id => 'tracked';

  @override
  Future<CatalogDiscovery> discover() async =>
      CatalogDiscovery(sections: const {});

  @override
  Future<CatalogDetails?> details(CatalogMediaId id) async => null;

  @override
  Future<void> close() async {
    closeCalls++;
  }
}
