import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/plugins/manage_apk_plugins.dart';
import 'package:hikari/application/plugins/plugin_manager.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/infrastructure/plugins/method_channel_apk_plugins.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('hikari/plugins');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  tearDown(() => messenger.setMockMethodCallHandler(channel, null));
  test(
    'native inventory preserves trust, lineage and unsupported signer status',
    () async {
      messenger.setMockMethodCallHandler(channel, (call) async {
        expect(call.method, 'inventory');
        return [
          row(),
          row()
            ..['packageName'] = 'org.other'
            ..['signers'] = ['a' * 64, 'b' * 64]
            ..['compatibility'] = 'unsupportedSigner'
            ..['trusted'] = false,
        ];
      });
      final items = await MethodChannelApkPlugins().inventory();
      expect(items.first.currentSigners, ['a' * 64]);
      expect(items.first.signerHistory, ['b' * 64, 'a' * 64]);
      expect(items.first.trust, ApkTrust.approved);
      expect(items.last.compatibility, ApkCompatibility.unsupportedSigner);
    },
  );
  test(
    'stage sends exact offer identity and approval only opaque validated token',
    () async {
      final calls = <MethodCall>[];
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call);
        if (call.method == 'stage') {
          return {'token': 'stage-123', 'identity': row()};
        }
        if (call.method == 'approve') return row();
        if (call.method == 'metadata') return Uint8List.fromList([123, 125]);
        return null;
      });
      final gateway = MethodChannelApkPlugins();
      final staged = await gateway.stage(
        ApkStageRequest(offer: offer(), expectedSigner: 'a' * 64),
      );
      await gateway.approveStaged(staged.token);
      await gateway.discardStaged(staged.token);
      expect(calls[2].method, 'discard');
      expect(calls[2].arguments, {'token': 'stage-123'});
      expect((calls.first.arguments as Map)['versionCode'], 10);
      expect((calls.first.arguments as Map)['expectedSigner'], 'a' * 64);
      expect(calls[1].arguments, {'token': 'stage-123'});
      expect(
        await gateway.fetchMetadata(
          Uri.parse('https://example.com/index.json'),
        ),
        [123, 125],
      );
    },
  );
  test('application waits for native approval then reconciles without Dart trust authority', () async {
    final store = MemoryState();
    final gateway = MethodChannelApkPlugins();
    messenger.setMockMethodCallHandler(channel, (call) async => []);
    final manager = await PluginManager.open(
      store: store,
      reader: EmptyReader(),
      inventory: NativeApkInventory(gateway),
      activatedAtStartup: {},
    );
    final actions = ManageApkPlugins(gateway: gateway, manager: manager);
    var approved = false;
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'approve') {
        approved = true;
        return row();
      }
      if (call.method == 'inventory') {
        expect(approved, isTrue);
        return [row()];
      }
      return null;
    });
    await actions.approveStaged('stage-123');
    expect(manager.state.installed, hasLength(1));
    expect(
      manager.state.approvals,
      isEmpty,
      reason: 'native owns executable trust',
    );
    store.fail = true;
    await expectLater(
      actions.approveStaged('stage-123'),
      throwsA(isA<ApkApprovalReconciliationFailure>()),
    );
  });
  test(
    'install returns durable pending operation, not launch success',
    () async {
      final calls = <MethodCall>[];
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call);
        if (call.method == 'canInstall') return false;
        if (call.method == 'openInstallSettings') return null;
        if (call.method == 'installOperations') return [operationRow()];
        return operationRow();
      });
      final gateway = MethodChannelApkPlugins();
      final operation = await gateway.installStaged(
        'stage-123',
        PluginOperation.install,
      );
      expect(operation.status, ApkInstallStatus.awaitingResult);
      expect(operation.isTerminal, isFalse);
      expect(calls.first.arguments, {'token': 'stage-123', 'kind': 'install'});
      expect((await gateway.installOperations()).single.id, operation.id);
      expect(await gateway.canInstall(), isFalse);
      await gateway.openInstallSettings();
      expect(
        (await gateway.resumeInstallConfirmation(operation.id)).isTerminal,
        isFalse,
      );
      await gateway.cancelInstall(operation.id);
      expect(calls.last.arguments, {'operationId': operation.id});
    },
  );
  test(
    'install response validation and inventory reconciliation are separate',
    () async {
      final store = MemoryState();
      final gateway = MethodChannelApkPlugins();
      messenger.setMockMethodCallHandler(channel, (call) async => []);
      final manager = await PluginManager.open(
        store: store,
        reader: EmptyReader(),
        inventory: NativeApkInventory(gateway),
        activatedAtStartup: {},
      );
      final actions = ManageApkPlugins(gateway: gateway, manager: manager);
      var inventoryReads = 0;
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'inventory') {
          inventoryReads++;
          return [row()];
        }
        if (call.method == 'installOperations') {
          return [operationRow()..['status'] = 'succeeded'];
        }
        return operationRow();
      });
      expect(
        (await actions.installStaged(
          'stage-123',
          PluginOperation.update,
        )).isTerminal,
        isFalse,
      );
      expect(inventoryReads, 0);
      expect(
        (await actions.installOperations()).single.status,
        ApkInstallStatus.succeeded,
      );
      expect(inventoryReads, 1);
      expect(
        manager.activationFor(offer().id),
        PluginActivation.unapproved,
        reason: 'inventory must not invent Dart trust or source activation',
      );
      store.fail = true;
      await expectLater(
        actions.installOperations(),
        throwsA(isA<ApkInstallReconciliationFailure>()),
      );
      messenger.setMockMethodCallHandler(
        channel,
        (call) async => operationRow()..['status'] = 'invented',
      );
      await expectLater(
        gateway.installStaged('stage-123', PluginOperation.install),
        throwsFormatException,
      );
      await expectLater(
        gateway.installStaged('stage-123', PluginOperation.uninstall),
        throwsArgumentError,
      );
    },
  );
  test('explicit retirement preserves unknown outcome and releases only native authority', () async {
    final gateway = MethodChannelApkPlugins();
    messenger.setMockMethodCallHandler(channel, (call) async => []);
    final manager = await PluginManager.open(
      store: MemoryState(),
      reader: EmptyReader(),
      inventory: NativeApkInventory(gateway),
      activatedAtStartup: {},
    );
    final actions = ManageApkPlugins(gateway: gateway, manager: manager);
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'retireInstall');
      expect(call.arguments, {
        'operationId': operationRow()['id'],
        'abandonOwnedSession': false,
        'acknowledgeUnrevokedRemoval': false,
      });
      return operationRow()
        ..['status'] = 'retired_unknown'
        ..['reason'] = 'retired_unknown_outcome';
    });
    final retired = await actions.retireInstall(
      operationRow()['id']! as String,
    );
    expect(retired.status, ApkInstallStatus.retiredUnknown);
    expect(retired.isTerminal, isTrue);
    expect(retired.sessionId, 42);
    expect(retired.signer, 'a' * 64);
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect((call.arguments as Map)['abandonOwnedSession'], isTrue);
      throw PlatformException(code: 'plugin_operation_failed');
    });
    await expectLater(
      actions.retireInstall(retired.id, abandonOwnedSession: true),
      throwsA(isA<PlatformException>()),
    );
  });
  test(
    'uninstall binds displayed identity and never treats launch as removal',
    () async {
      final gateway = MethodChannelApkPlugins();
      messenger.setMockMethodCallHandler(channel, (call) async => [row()]);
      final identity = (await gateway.inventory()).single;
      messenger.setMockMethodCallHandler(channel, (call) async {
        expect(call.method, 'uninstall');
        expect(call.arguments, {
          'family': 'manga',
          'packageName': 'org.example.plugin',
          'versionCode': 10,
          'expectedSigner': 'a' * 64,
        });
        return operationRow()
          ..['kind'] = 'uninstall'
          ..['sessionId'] = null
          ..['stageTransferred'] = false;
      });
      final submitted = await gateway.uninstall(identity);
      expect(submitted.kind, PluginOperation.uninstall);
      expect(submitted.isTerminal, isFalse);
      for (final status in [
        'absent_before_request',
        'externally_removed',
        'succeeded',
        'cancelled',
        'interrupted',
      ]) {
        messenger.setMockMethodCallHandler(
          channel,
          (_) async => [
            operationRow()
              ..['kind'] = 'uninstall'
              ..['sessionId'] = null
              ..['stageTransferred'] = false
              ..['status'] = status,
          ],
        );
        final operation = (await gateway.installOperations()).single;
        expect(operation.isTerminal, status != 'interrupted');
        expect(
          operation.status == ApkInstallStatus.succeeded,
          status == 'succeeded',
        );
      }
    },
  );
  test(
    'malformed native metadata rejects and native errors propagate',
    () async {
      messenger.setMockMethodCallHandler(
        channel,
        (call) async => [
          row()..['signers'] = ['bad'],
        ],
      );
      await expectLater(
        MethodChannelApkPlugins().inventory(),
        throwsFormatException,
      );
      messenger.setMockMethodCallHandler(
        channel,
        (call) async => throw PlatformException(code: 'approval_failed'),
      );
      await expectLater(
        MethodChannelApkPlugins().approveStaged('stage-123'),
        throwsA(isA<PlatformException>()),
      );
    },
  );
}

Map<String, Object?> operationRow() => {
  'id': '123e4567-e89b-42d3-a456-426614174000',
  'family': 'manga',
  'packageName': 'org.example.plugin',
  'versionCode': 10,
  'signer': 'a' * 64,
  'kind': 'install',
  'sessionId': 42,
  'status': 'awaiting_result',
  'stageTransferred': true,
  'reason': null,
};

Map<String, Object?> row() => {
  'family': 'manga',
  'packageName': 'org.example.plugin',
  'versionCode': 10,
  'versionName': '1.6.10',
  'library': '1.6',
  'entrypoint': '.Plugin',
  'signers': ['a' * 64],
  'history': ['b' * 64, 'a' * 64],
  'compatibility': 'compatible',
  'trusted': true,
};

class MemoryState implements PluginStateStore {
  PluginState state = PluginState();
  bool fail = false;
  @override
  Future<PluginState> load() async => state;
  @override
  Future<void> save(PluginState value) async {
    if (fail) throw StateError('write failed');
    state = value;
  }
}

class EmptyReader implements PluginRepositoryReader {
  @override
  Future<PluginRepositorySnapshot> read(PluginRepository repository) async =>
      PluginRepositorySnapshot(name: 'Empty', offers: []);
}

PluginOffer offer() => PluginOffer(
  repositoryId: RepositoryId('https://example.com/index.json'),
  id: PluginId(PluginEcosystem.manga, 'org.example.plugin'),
  name: 'Plugin',
  version: ApkPluginVersion(10, '1.6.10'),
  downloadUrl: 'https://cdn.example.com/exact.apk',
  iconUrl: 'https://cdn.example.com/icon.png',
  sources: [],
);
