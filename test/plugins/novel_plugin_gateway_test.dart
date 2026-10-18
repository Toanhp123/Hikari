import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/plugins/manage_novel_plugins.dart';
import 'package:hikari/application/plugins/plugin_manager.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/infrastructure/plugins/method_channel_novel_plugins.dart';

import 'apk_plugin_gateway_test.dart' as helpers;

Map<String, Object?> artifact({String version = '1.0'}) => {
  'id': 'inert',
  'name': 'Inert',
  'version': version,
  'repository': 'https://example.com/index.json',
  'site': 'https://example.com/novels',
  'origins': ['https://example.com'],
  'digest': 'a' * 64,
};
Map<String, Object?> inventoryRow() => {
  'current': artifact(),
  'previous': null,
  'status': 'ready',
  'previousStatus': null,
  'admission': 'unknown',
  'previousAdmission': null,
  'restartRequired': true,
};
PluginOffer offer() => PluginOffer(
  repositoryId: RepositoryId('https://example.com/index.json'),
  id: PluginId(PluginEcosystem.novel, 'inert'),
  name: 'Inert',
  version: NovelPluginVersion('1.0'),
  downloadUrl: 'https://example.com/plugin.js',
  iconUrl: 'https://example.com/icon.png',
  siteUrl: 'https://example.com/novels',
  sources: <PluginSourceDescriptor>[],
);
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('hikari/plugins');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  tearDown(() => messenger.setMockMethodCallHandler(channel, null));
  test(
    'selected tuple goes through native consent before manager mirror',
    () async {
      final store = helpers.MemoryState();
      final selected = offer();
      store.state = PluginState(
        repositories: [
          PluginRepository(
            selected.repositoryId,
            PluginEcosystem.novel,
            snapshot: PluginRepositorySnapshot(
              name: 'Test',
              offers: [selected],
            ),
          ),
        ],
        owners: {selected.id: selected.repositoryId},
      );
      const gateway = MethodChannelNovelPlugins();
      var installed = false;
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'novelInventory') {
          return installed ? [inventoryRow()] : [];
        }
        if (call.method == 'novelStage') {
          expect((call.arguments as Map)['metadata'], {
            'id': 'inert',
            'name': 'Inert',
            'version': '1.0',
            'repository': selected.repositoryId.toString(),
            'site': selected.siteUrl.toString(),
            'origins': ['https://example.com'],
          });
          return {'token': 'stage-123', 'artifact': artifact()};
        }
        if (call.method == 'novelAdmit') {
          expect((call.arguments as Map)['artifact'], artifact());
          return [inventoryRow()..['admission'] = 'admitted'];
        }
        if (call.method == 'novelInstall') {
          if (!installed) expect(store.state.approvals, isEmpty);
          expect((call.arguments as Map)['artifact'], artifact());
          installed = true;
          return [inventoryRow()];
        }
        throw StateError('Unexpected fake native call');
      });
      final manager = await PluginManager.open(
        store: store,
        reader: helpers.EmptyReader(),
        inventory: NativeNovelInventory(gateway),
        activatedAtStartup: {},
      );
      final management = ManageNovelPlugins(gateway: gateway, manager: manager);
      final staged = await management.stage(
        selected.id,
        origins: ['https://example.com'],
      );
      final result = await management.install(staged);
      expect(result.single.restartRequired, isTrue);
      expect(result.single.admission, NovelAdmission.unknown);
      expect(manager.state.approvals.single.digest, 'a' * 64);
      expect(
        manager.state.installed.single.compatibility,
        PluginCompatibility.unknown,
      );
      expect(
        (await management.admit(result.single.current)).single.admission,
        NovelAdmission.admitted,
      );
      store.fail = true;
      await expectLater(
        management.install(staged),
        throwsA(isA<NovelReconciliationFailure>()),
      );
    },
  );
  test('explicit admission returns bounded evidence, not activation', () async {
    const gateway = MethodChannelNovelPlugins();
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'novelAdmit') {
        expect((call.arguments as Map)['artifact'], artifact());
      } else {
        expect(call.method, 'novelInventory');
      }
      return [inventoryRow()..['admission'] = 'admitted'];
    });
    final displayed = (await gateway.inventory()).single.current;
    final admitted = await gateway.admit(displayed);
    expect(admitted.single.admission, NovelAdmission.admitted);
    expect(admitted.single.restartRequired, isTrue);
    expect(
      (await NativeNovelInventory(
        gateway,
      ).readInstalled()).single.compatibility,
      PluginCompatibility.unknown,
    );
    messenger.setMockMethodCallHandler(channel, (_) async {
      throw PlatformException(
        code: 'novel_admission_rejected',
        message: 'Novel admission rejected',
      );
    });
    await expectLater(
      gateway.admit(displayed),
      throwsA(
        isA<NovelAdmissionRejected>().having(
          (error) => error.reason,
          'sanitized reason',
          'execution-rejected',
        ),
      ),
    );
  });
  test('malformed special evidence remains typed and keeps separately valid branches', () async {
    const gateway = MethodChannelNovelPlugins();
    messenger.setMockMethodCallHandler(channel, (_) async => [inventoryRow()]);
    final displayed = (await gateway.inventory()).single.current;
    final validPointer = {'current': artifact(), 'previous': null};
    for (final bad in [
      null,
      <String, Object?>{},
      List.filled(501, validPointer),
      [validPointer, validPointer],
      [
        {'current': artifact(), 'previous': artifact()..['id'] = 'other'},
      ],
      [
        {'current': artifact()..['secret'] = 'raw', 'previous': null},
      ],
    ]) {
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => throw PlatformException(
          code: 'novel_publication_unknown',
          details: {
            'publication': 'unknown',
            'lastConfirmedInventory': [inventoryRow()],
            'attemptedPointers': bad,
          },
        ),
      );
      await expectLater(
        gateway.admit(displayed),
        throwsA(
          isA<NovelPublicationFailure>()
              .having((e) => e.inventory?.length, 'valid confirmed retained', 1)
              .having((e) => e.attempted, 'bad attempted omitted', isNull),
        ),
      );
    }
    for (final bad in [
      null,
      <String, Object?>{},
      List.filled(501, inventoryRow()),
      [inventoryRow(), inventoryRow()],
      [inventoryRow()..['previous'] = artifact()],
      [inventoryRow()..['status'] = 'raw'],
      [inventoryRow()..['current'] = (artifact()..['digest'] = 42)],
    ]) {
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => throw PlatformException(
          code: 'novel_publication_unknown',
          details: {
            'publication': 'unknown',
            'lastConfirmedInventory': bad,
            'attemptedPointers': [validPointer],
          },
        ),
      );
      await expectLater(
        gateway.admit(displayed),
        throwsA(
          isA<NovelPublicationFailure>()
              .having((e) => e.inventory, 'bad confirmed omitted', isNull)
              .having(
                (e) => e.attempted?.length,
                'valid attempted retained',
                1,
              ),
        ),
      );
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => throw PlatformException(
          code: 'novel_pointer_sync_failed',
          details: bad,
        ),
      );
      await expectLater(
        gateway.admit(displayed),
        throwsA(
          isA<NovelPublicationFailure>().having(
            (e) => e.inventory,
            'bad committed omitted',
            isNull,
          ),
        ),
      );
    }
    for (final reason in [
      'execution-rejected',
      'unsupported-module',
      'missing-required-method',
      'host-operation-denied',
      'export-metadata-mismatch',
    ]) {
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => throw PlatformException(
          code: 'novel_admission_rejected',
          details: {'reason': reason},
        ),
      );
      await expectLater(
        gateway.admit(displayed),
        throwsA(
          isA<NovelAdmissionRejected>().having(
            (e) => e.reason,
            'known bounded reason',
            reason,
          ),
        ),
      );
    }
  });
  test('publication failures preserve typed evidence and fail closed on malformed details', () async {
    const gateway = MethodChannelNovelPlugins();
    messenger.setMockMethodCallHandler(channel, (_) async => [inventoryRow()]);
    final displayed = (await gateway.inventory()).single.current;
    for (final malformed in [false, true]) {
      messenger.setMockMethodCallHandler(channel, (_) async {
        throw PlatformException(
          code: 'novel_publication_unknown',
          details: malformed
              ? {'secret': 'raw'}
              : {
                  'publication': 'unknown',
                  'lastConfirmedInventory': [inventoryRow()],
                  'attemptedPointers': [
                    {
                      'current': artifact(version: '2.0'),
                      'previous': artifact(),
                    },
                  ],
                },
        );
      });
      await expectLater(
        gateway.admit(displayed),
        throwsA(
          isA<NovelPublicationFailure>()
              .having(
                (error) => error.kind,
                'kind',
                NovelPublicationFailureKind.publicationUnknown,
              )
              .having(
                (error) => error.inventory?.length,
                'confirmed inventory',
                malformed ? null : 1,
              )
              .having(
                (error) =>
                    error.attempted?.single.current.metadata.version.value,
                'attempted version',
                malformed ? null : '2.0',
              ),
        ),
      );
    }
    messenger.setMockMethodCallHandler(channel, (_) async {
      throw PlatformException(
        code: 'novel_pointer_sync_failed',
        details: [inventoryRow()],
      );
    });
    await expectLater(
      gateway.uninstall(displayed),
      throwsA(
        isA<NovelPublicationFailure>()
            .having(
              (error) => error.kind,
              'kind',
              NovelPublicationFailureKind.pointerSyncFailed,
            )
            .having(
              (error) => error.inventory?.single.current.digest,
              'committed digest',
              'a' * 64,
            ),
      ),
    );
    messenger.setMockMethodCallHandler(channel, (_) async {
      throw PlatformException(
        code: 'novel_admission_rejected',
        details: {'reason': 'raw JS https://secret.example'},
      );
    });
    await expectLater(
      gateway.admit(displayed),
      throwsA(
        isA<NovelAdmissionRejected>().having(
          (error) => error.reason,
          'reason',
          'execution-rejected',
        ),
      ),
    );
  });

  test(
    'runtime evidence preserves removed loaded revisions without activation',
    () async {
      const gateway = MethodChannelNovelPlugins();
      messenger.setMockMethodCallHandler(channel, (call) async {
        expect(call.method, 'novelRuntimeSnapshot');
        return {
          'initialization': 'ready',
          'failure': null,
          'loaded': [
            {
              'id': 'inert',
              'name': 'Inert',
              'site': 'https://example.com/novels',
              'artifact': artifact(),
            },
          ],
          'issues': [
            {'artifact': artifact(version: '2.0'), 'reason': 'corrupt'},
          ],
        };
      });
      final snapshot = await gateway.runtimeSnapshot();
      expect(snapshot.initialization, NovelRuntimeInitialization.ready);
      expect(snapshot.loaded.single.artifact!.digest, 'a' * 64);
      expect(snapshot.issues.single.reason, 'corrupt');
      expect(snapshot.loadedCurrent(snapshot.loaded.single.artifact!), isTrue);
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => [inventoryRow()..['current'] = artifact(version: '2.0')],
      );
      expect(
        snapshot.loadedCurrent((await gateway.inventory()).single.current),
        isFalse,
      );
      for (final invalid in <Object?>[
        null,
        {
          'initialization': 'notInitialized',
          'failure': null,
          'loaded': <Object?>[],
          'issues': [
            {'artifact': artifact(), 'reason': 'missing'},
          ],
        },
        {
          'initialization': 'failed',
          'failure': null,
          'loaded': <Object?>[],
          'issues': <Object?>[],
        },
        {
          'initialization': 'ready',
          'failure': null,
          'loaded': [
            {
              'id': 'other',
              'name': 'Inert',
              'site': 'https://example.com/novels',
              'artifact': artifact(),
            },
          ],
          'issues': <Object?>[],
        },
        {
          'initialization': 'ready',
          'failure': null,
          'loaded': <Object?>[],
          'issues': [
            {'artifact': artifact(), 'reason': 'raw JS error'},
          ],
        },
      ]) {
        messenger.setMockMethodCallHandler(channel, (_) async => invalid);
        await expectLater(gateway.runtimeSnapshot(), throwsFormatException);
      }
      expect(
        snapshot.pendingRemoval([]).single.artifact!.metadata.id.upstreamId,
        'inert',
      );
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => {
          'initialization': 'ready',
          'failure': 'collision',
          'loaded': <Object?>[],
          'issues': <Object?>[],
        },
      );
      await expectLater(gateway.runtimeSnapshot(), throwsFormatException);
    },
  );
  test('bare-root URLs round-trip without broadening frozen consent', () async {
    final selected = PluginOffer(
      repositoryId: RepositoryId('https://example.com'),
      id: PluginId(PluginEcosystem.novel, 'inert'),
      name: 'Inert',
      version: NovelPluginVersion('1.0'),
      downloadUrl: 'https://example.com/plugin.js',
      iconUrl: 'https://example.com/icon.png',
      siteUrl: 'https://example.com',
      sources: <PluginSourceDescriptor>[],
    );
    const gateway = MethodChannelNovelPlugins();
    final returned = artifact()
      ..['site'] = 'https://example.com/'
      ..['repository'] = 'https://example.com/';
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => {'token': 'root-stage', 'artifact': returned},
    );
    final staged = await gateway.stage(selected, ['https://example.com']);
    expect(staged.artifact.metadata.site.origin, 'https://example.com');
    for (final changed in [
      {
        ...returned,
        'origins': ['https://example.com', 'https://other.com'],
      },
      {...returned, 'site': 'https://example.com/other'},
      {...returned, 'repository': 'https://other.com/'},
      {...returned, 'version': '2.0'},
    ]) {
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => {'token': 'root-stage', 'artifact': changed},
      );
      await expectLater(
        gateway.stage(selected, ['https://example.com']),
        throwsFormatException,
      );
    }
  });
  test(
    'bounded native codec rejects fabricated activation and malformed evidence',
    () async {
      const gateway = MethodChannelNovelPlugins();
      for (final value in <Object?>[
        null,
        List.filled(501, inventoryRow()),
        [inventoryRow(), inventoryRow()],
        [inventoryRow()..['restartRequired'] = false],
        [inventoryRow()..['admission'] = 'compatible'],
        [inventoryRow()..['current'] = (artifact()..['digest'] = 'bad')],
        [
          inventoryRow()
            ..['current'] = (artifact()
              ..['origins'] = ['https://example.com/path']),
        ],
        [inventoryRow()..['current'] = (artifact()..['version'] = 'latest')],
        [inventoryRow()..['current'] = (artifact()..['unknown'] = true)],
      ]) {
        messenger.setMockMethodCallHandler(channel, (_) async => value);
        await expectLater(gateway.inventory(), throwsFormatException);
      }
      messenger.setMockMethodCallHandler(channel, null);
      await expectLater(
        gateway.inventory(),
        throwsA(isA<MissingPluginException>()),
      );
    },
  );
}
