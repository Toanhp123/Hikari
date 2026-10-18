import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/plugins/plugin_manager.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/infrastructure/plugins/file_plugin_state_store.dart';
import 'package:hikari/infrastructure/plugins/plugin_manifest_reader.dart';

void main() {
  test(
    'repository identity normalizes equivalent explicit HTTPS manifests',
    () {
      expect(
        RepositoryId('HTTPS://Example.COM:443/a/%69ndex.json'),
        RepositoryId('https://example.com/a/index.json'),
      );
      for (final url in [
        'http://example.com/index.json',
        'https://user@example.com/x',
        'https://example.com/x#fragment',
        'https://localhost/x',
        'https://127.0.0.1/x',
        'https://[::1]/x',
        'https://example.com/a/../index.json',
        'https://example.com/a/%2e%2e/index.json',
        'https://example.com/a/%252e%252e/index.json',
        'https://example.com/a%2fb/index.json',
      ]) {
        expect(() => RepositoryId(url), throwsFormatException, reason: url);
      }
    },
  );

  test('versions compare numerically, not lexically or by display name', () {
    expect(
      NovelPluginVersion('1.10.0').compareTo(NovelPluginVersion('1.9.9')),
      1,
    );
    expect(NovelPluginVersion('1.2').compareTo(NovelPluginVersion('1.2.0')), 0);
    expect(
      NovelPluginVersion('1.2.0-rc.10')
          .compareTo(NovelPluginVersion('1.2.0-rc.2')),
      1,
    );
    expect(
      NovelPluginVersion('1.2.0-rc.2').compareTo(NovelPluginVersion('1.2.0')),
      -1,
    );
    expect(
      ApkPluginVersion(10, 'old').compareTo(ApkPluginVersion(9, 'new')),
      1,
    );
    expect(() => NovelPluginVersion('latest'), throwsFormatException);
    expect(
      PluginId(PluginEcosystem.manga, 'same'),
      isNot(PluginId(PluginEcosystem.anime, 'same')),
    );
  });

  test('LNReader actual array keeps exact URLs and Unicode language', () async {
    final repo = PluginRepository(
      RepositoryId('https://example.com/plugins.min.json'),
      PluginEcosystem.novel,
    );
    final reader = PluginManifestReader(
      (url) async => utf8.encode(jsonEncode([novelJson()])),
    );
    final snapshot = await reader.read(repo);
    expect(snapshot.offers.single.id.upstreamId, 'skythewoodtranslations');
    expect(
      snapshot.offers.single.downloadUrl.toString(),
      'https://cdn.example.com/exact/sky.js',
    );
    expect(snapshot.offers.single.sources.single.language, '日本語');
    expect(snapshot.signerFingerprint, isNull);
    expect(
      pluginHttpsUrl('https://example.com/日本語.png').pathSegments.single,
      '日本語.png',
    );
  });

  test('manifest rejects malformed, duplicate and oversized data', () async {
    final repo = PluginRepository(
      RepositoryId('https://example.com/plugins.json'),
      PluginEcosystem.novel,
    );
    for (final payload in [
      <String, Object?>{},
      [novelJson(), novelJson()],
      [novelJson()..['version'] = 1],
      [novelJson()..['url'] = 'http://example.com/plugin.js'],
      [novelJson()..['name'] = 'x' * 4097],
    ]) {
      final reader = PluginManifestReader(
        (url) async => utf8.encode(jsonEncode(payload)),
      );
      await expectLater(reader.read(repo), throwsFormatException);
    }
    final reader = PluginManifestReader(
      (url) async => List.filled(PluginManifestReader.maxBytes + 1, 32),
    );
    await expectLater(reader.read(repo), throwsFormatException);
  });

  test(
    'APK v2 JSON preserves int64 IDs, explicit resources and multiple sources',
    () async {
      for (final ecosystem in [PluginEcosystem.manga, PluginEcosystem.anime]) {
        final repo = PluginRepository(
          RepositoryId('https://example.com/repo.json'),
          ecosystem,
        );
        final reader = PluginManifestReader(
          (url) async => utf8.encode(jsonEncode(apkJson())),
        );
        final snapshot = await reader.read(repo);
        expect(snapshot.signerFingerprint, 'a' * 64);
        expect(
          snapshot.offers.single.downloadUrl.toString(),
          'https://github.com/org/releases/download/tag/exact.apk',
        );
        expect(snapshot.offers.single.sources.map((s) => s.upstreamId), [
          '6289731484943315811',
          '2',
        ]);
      }
    },
  );

  test('index chains reject cycles and unsafe targets', () async {
    final repo = PluginRepository(
      RepositoryId('https://example.com/repo.json'),
      PluginEcosystem.manga,
    );
    for (final target in [
      'https://example.com/repo.json',
      'https://localhost/index.pb',
    ]) {
      final reader = PluginManifestReader(
        (url) async => utf8.encode(
          jsonEncode({
            'index_v2': target,
            'meta': {'name': 'Repo'},
          }),
        ),
      );
      await expectLater(reader.read(repo), throwsFormatException);
    }
  });

  test('durable lifecycle retains last good offers, ownership, inventory and explicit trust', () async {
    final directory = await Directory.systemTemp.createTemp(
      'hikari-plugin-test-',
    );
    addTearDown(() => directory.delete(recursive: true));
    final store = FilePluginStateStore(directory: directory);
    final inventory = TestInventory();
    var broken = false;
    var signer = 'a' * 64;
    final reader = PluginManifestReader(
      (url) async => broken
          ? utf8.encode('{')
          : utf8.encode(jsonEncode(apkJson(signer: signer))),
    );
    var manager = await PluginManager.open(
      store: store,
      reader: reader,
      inventory: inventory,
      activatedAtStartup: {},
    );
    final first = await manager.addRepository(
      'https://one.example/repo.json',
      PluginEcosystem.manga,
    );
    await expectLater(
      manager.addRepository(
        'https://ONE.example:443/repo.json',
        PluginEcosystem.manga,
      ),
      throwsStateError,
    );
    final second = await manager.addRepository(
      'https://two.example/repo.json',
      PluginEcosystem.manga,
    );
    await manager.refresh(first.id);
    await manager.refresh(second.id);
    final id = manager.state.repositories.first.snapshot!.offers.single.id;
    expect(manager.offersFor(id), hasLength(2));
    expect(manager.state.owners[id], isNull);
    await manager.selectOwner(id, first.id);
    expect(manager.approvalFor(id), isNull);
    await manager.approve(
      PluginApproval(
        id: id,
        repositoryId: first.id,
        kind: PluginApprovalKind.signer,
        digest: signer,
      ),
    );
    inventory.installed = [
      InstalledPlugin(
        id: id,
        version: ApkPluginVersion(106004, '1.6.4'),
        digest: signer,
        compatibility: PluginCompatibility.compatible,
      ),
    ];
    await manager.reconcileInstalled();
    expect(manager.activationFor(id), PluginActivation.restartRequired);
    broken = true;
    await manager.refresh(first.id);
    expect(manager.state.repositories.first.snapshot!.offers, hasLength(1));
    expect(manager.state.repositories.first.refreshError, isNotNull);
    broken = false;
    signer = 'b' * 64;
    await manager.refresh(first.id);
    expect(manager.approvalFor(id)!.digest, 'a' * 64);
    expect(
      manager.updateOfferFor(id),
      isNull,
      reason: 'changed signer requires reapproval',
    );
    await manager.removeRepository(first.id);
    expect(manager.state.owners[id], first.id);
    expect(manager.state.installed.single.id, id);
    expect(manager.approvalFor(id)!.digest, 'a' * 64);
    expect(
      manager.updateOfferFor(id),
      isNull,
      reason: 'no silent ownership transfer',
    );
    manager = await PluginManager.open(
      store: FilePluginStateStore(directory: directory),
      reader: reader,
      inventory: inventory,
      activatedAtStartup: {},
    );
    expect(manager.state.owners[id], first.id);
    expect(manager.state.installed.single.id, id);
    expect(manager.approvalFor(id)!.digest, 'a' * 64);
    expect(manager.state.repositories.single.id, second.id);
  });

  test(
    'failed atomic publish preserves old durable and manager state',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'hikari-plugin-atomic-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final store = FilePluginStateStore(directory: directory);
      final manager = await PluginManager.open(
        store: store,
        reader: PluginManifestReader((url) async => []),
        inventory: TestInventory(),
        activatedAtStartup: {},
      );
      await manager.addRepository(
        'https://one.example/index.json',
        PluginEcosystem.novel,
      );
      // A directory at the temporary-file path deterministically fails before publish.
      await Directory('${directory.path}/plugins.json.tmp').create();
      await expectLater(
        manager.addRepository(
          'https://two.example/index.json',
          PluginEcosystem.novel,
        ),
        throwsA(isA<FileSystemException>()),
      );
      expect(manager.state.repositories, hasLength(1));
      expect(
        (await FilePluginStateStore(directory: directory).load()).repositories,
        hasLength(1),
      );
    },
  );

  test(
    'protobuf respects different manga/anime source fields and gzip bounds',
    () async {
      for (final ecosystem in [PluginEcosystem.manga, PluginEcosystem.anime]) {
        final root = protoRepository(ecosystem);
        final repo = PluginRepository(
          RepositoryId('https://example.com/index.pb'),
          ecosystem,
        );
        for (final bytes in [root, gzip.encode(root)]) {
          final snapshot = await PluginManifestReader((url) async => bytes)
              .read(repo);
          expect(
            snapshot.offers.single.sources.single.upstreamId,
            '6289731484943315811',
          );
          expect(
            snapshot.offers.single.isTorrent,
            ecosystem == PluginEcosystem.anime,
          );
        }
        await expectLater(
          PluginManifestReader((url) async => root.sublist(0, root.length - 1))
              .read(repo),
          throwsFormatException,
        );
      }
      final repo = PluginRepository(
        RepositoryId('https://example.com/index.pb'),
        PluginEcosystem.manga,
      );
      await expectLater(
        PluginManifestReader(
          (url) async =>
              gzip.encode(List.filled(PluginManifestReader.maxBytes + 1, 32)),
        ).read(repo),
        throwsFormatException,
      );
    },
  );

  test(
    'protobuf JSON-like length prefixes remain protobuf inline and remote',
    () async {
      for (final ecosystem in [PluginEcosystem.manga, PluginEcosystem.anime]) {
        final repo = PluginRepository(
          RepositoryId('https://example.com/repo.json'),
          ecosystem,
        );
        for (final length in [91, 123]) {
          final inline = protoRepository(
            ecosystem,
            repositoryName: 'R' * length,
          );
          expect(inline.take(2), [10, length]);
          final snapshot = await PluginManifestReader((url) async => inline)
              .read(repo);
          expect(snapshot.name, 'R' * length);
          expect(snapshot.offers, hasLength(1));
          final list = protoRepository(ecosystem, standaloneLength: length);
          expect(list.take(2), [10, length]);
          final root = utf8.encode(
            jsonEncode({
              'name': 'Remote',
              'signingKey': 'a' * 64,
              'extensionListUrl': 'https://cdn.example.com/list.pb',
            }),
          );
          for (final payload in [list, gzip.encode(list)]) {
            final remote = await PluginManifestReader(
              (url) async => url.host == 'cdn.example.com' ? payload : root,
            ).read(repo);
            expect(remote.name, 'Remote');
            expect(remote.signerFingerprint, 'a' * 64);
            expect(remote.offers, hasLength(1));
          }
        }
        for (final malformed in ['{', '[', ' \n{broken', ' \n[broken']) {
          await expectLater(
            PluginManifestReader((url) async => utf8.encode(malformed))
                .read(repo),
            throwsFormatException,
          );
        }
      }
    },
  );

  test('external v2 extension list retains repository metadata', () async {
    final root = apkJson();
    final list = root.remove('extensionList');
    root['extensionListUrl'] = 'https://cdn.example.com/list.json';
    final repo = PluginRepository(
      RepositoryId('https://example.com/repo.json'),
      PluginEcosystem.manga,
    );
    final snapshot = await PluginManifestReader(
      (url) async =>
          utf8.encode(jsonEncode(url.host == 'cdn.example.com' ? list : root)),
    ).read(repo);
    expect(snapshot.name, 'Repo');
    expect(snapshot.signerFingerprint, 'a' * 64);
    expect(snapshot.offers.single.id.upstreamId, 'org.example.plugin');
  });

  test('legacy explicit APK URL wins over filename convention', () async {
    final repo = PluginRepository(
      RepositoryId('https://example.com/index.min.json'),
      PluginEcosystem.manga,
    );
    final snapshot = await PluginManifestReader(
      (url) async => utf8.encode(
        jsonEncode(
          url.path.endsWith('repo.json')
              ? {
                  'meta': {'name': 'Legacy', 'signingKeyFingerprint': 'a' * 64},
                }
              : [
                  {
                    'name': 'Plugin',
                    'pkg': 'org.example.plugin',
                    'apk': 'plugin.apk',
                    'lang': 'en',
                    'code': 10,
                    'version': '1.6.10',
                    'nsfw': 0,
                    'apkUrls': [
                      'https://github.com/org/releases/download/v1/exact.apk',
                    ],
                    'sources': [
                      {'id': '1', 'name': 'One', 'lang': 'en', 'baseUrl': ''},
                    ],
                  },
                ],
        ),
      ),
    ).read(repo);
    expect(
      snapshot.offers.single.downloadUrl.toString(),
      'https://github.com/org/releases/download/v1/exact.apk',
    );
  });

  test(
    'restart marks pending operations interrupted without replay or activation',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'hikari-plugin-recovery-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final store = FilePluginStateStore(directory: directory);
      final id = PluginId(PluginEcosystem.novel, 'novel');
      final repo = RepositoryId('https://example.com/plugins.json');
      await store.save(
        PluginState(
          owners: {id: repo},
          approvals: [
            PluginApproval(
              id: id,
              repositoryId: repo,
              kind: PluginApprovalKind.codeDigest,
              digest: 'b' * 64,
              origins: {'https://site.example'},
            ),
          ],
          operations: [
            PendingPluginOperation(
              id,
              PluginOperation.install,
              PluginOperationStatus.pending,
            ),
          ],
        ),
      );
      final manager = await PluginManager.open(
        store: store,
        reader: PluginManifestReader((url) async => []),
        inventory: TestInventory(),
        activatedAtStartup: {},
      );
      expect(
        manager.state.operations.single.status,
        PluginOperationStatus.interrupted,
      );
      expect(manager.activationFor(id), PluginActivation.notInstalled);
      expect((await store.load()).approvals.single.origins, {
        'https://site.example',
      });
      await File('${directory.path}/plugins.json')
          .writeAsString('{"schema":999}');
      await expectLater(store.load(), throwsFormatException);
    },
  );

  test('startup revision stays pinned; anime deferred; novel update candidate visible', () async {
    final directory = await Directory.systemTemp.createTemp(
      'hikari-plugin-activation-',
    );
    addTearDown(() => directory.delete(recursive: true));
    final id = PluginId(PluginEcosystem.novel, 'skythewoodtranslations');
    final old = InstalledPlugin(
      id: id,
      version: NovelPluginVersion('1.0.0'),
      digest: 'a' * 64,
      compatibility: PluginCompatibility.compatible,
    );
    final inventory = TestInventory()..installed = [old];
    final manager = await PluginManager.open(
      store: FilePluginStateStore(directory: directory),
      reader: PluginManifestReader(
        (url) async => utf8.encode(jsonEncode([novelJson()])),
      ),
      inventory: inventory,
      activatedAtStartup: {id: old},
    );
    final repo = await manager.addRepository(
      'https://example.com/plugins.json',
      PluginEcosystem.novel,
    );
    await manager.refresh(repo.id);
    await manager.selectOwner(id, repo.id);
    expect(manager.updateCandidateFor(id), isNotNull);
    inventory.installed = [
      InstalledPlugin(
        id: id,
        version: NovelPluginVersion('1.10.0'),
        digest: 'b' * 64,
        compatibility: PluginCompatibility.compatible,
      ),
    ];
    await manager.reconcileInstalled();
    expect(manager.activationFor(id), PluginActivation.restartRequired);
    expect(manager.activeRevisionFor(id)!.digest, 'a' * 64);
    inventory.installed = [];
    await manager.reconcileInstalled();
    expect(manager.activationFor(id), PluginActivation.notInstalled);
    final anime = PluginId(PluginEcosystem.anime, 'org.example.anime');
    inventory.installed = [
      InstalledPlugin(
        id: anime,
        version: ApkPluginVersion(1, '1'),
        digest: 'a' * 64,
        compatibility: PluginCompatibility.compatible,
      ),
    ];
    await manager.reconcileInstalled();
    expect(manager.activationFor(anime), PluginActivation.runtimeDeferred);
  });

  test(
    'legitimate optional assets and empty source index do not poison repo',
    () async {
      final repo = PluginRepository(
        RepositoryId('https://example.com/plugins.json'),
        PluginEcosystem.novel,
      );
      final snapshot = await PluginManifestReader(
        (url) async => utf8.encode(
          jsonEncode([
            novelJson()..['customCSS'] = 'https://cdn.example.com/style.css',
            novelJson()
              ..['id'] = 'other'
              ..['customJS'] = 'https://cdn.example.com/extra.js',
          ]),
        ),
      ).read(repo);
      expect(snapshot.offers.first.siteUrl!.origin, 'https://sky.example.com');
      expect(
        snapshot.offers.first.customCssUrl.toString(),
        'https://cdn.example.com/style.css',
      );
      expect(
        snapshot.offers.last.compatibility,
        PluginCompatibility.unsupportedModule,
      );
      final apk = apkJson();
      final extensions = (apk['extensionList'] as Map)['extensions'] as List;
      (extensions.single as Map).remove('sources');
      final apkRepo = PluginRepository(
        RepositoryId('https://example.com/index.json'),
        PluginEcosystem.manga,
      );
      final apkSnapshot = await PluginManifestReader(
        (url) async => utf8.encode(jsonEncode(apk)),
      ).read(apkRepo);
      expect(apkSnapshot.offers.single.sources, isEmpty);
    },
  );

  test('consent validates code digest and explicit HTTPS origins', () {
    final id = PluginId(PluginEcosystem.novel, 'novel');
    final repo = RepositoryId('https://example.com/plugins.json');
    expect(
      () => PluginApproval(
        id: id,
        repositoryId: repo,
        kind: PluginApprovalKind.codeDigest,
        digest: 'bad',
        origins: {'https://site.example'},
      ),
      throwsFormatException,
    );
    expect(
      () => PluginApproval(
        id: id,
        repositoryId: repo,
        kind: PluginApprovalKind.codeDigest,
        digest: 'a' * 64,
      ),
      throwsFormatException,
    );
    expect(
      () => PluginApproval(
        id: id,
        repositoryId: repo,
        kind: PluginApprovalKind.codeDigest,
        digest: 'a' * 64,
        origins: {'https://site.example/path'},
      ),
      throwsFormatException,
    );
  });
}

Map<String, Object?> novelJson() => {
  'id': 'skythewoodtranslations',
  'name': 'Sky',
  'lang': '日本語',
  'version': '1.10.0',
  'site': 'https://sky.example.com',
  'url': 'https://cdn.example.com/exact/sky.js',
  'iconUrl': 'https://cdn.example.com/icon.png',
};

Map<String, Object?> apkJson({String? signer}) => {
  'name': 'Repo',
  'signingKey': signer ?? 'a' * 64,
  'extensionList': {
    'extensions': [
      {
        'name': 'Same display name',
        'packageName': 'org.example.plugin',
        'resources': {
          'apkUrl': 'https://github.com/org/releases/download/tag/exact.apk',
          'iconUrl': 'https://cdn.example.com/icon.png',
        },
        'extensionLib': '1.6',
        'versionCode': '106004',
        'versionName': '1.6.4',
        'sources': [
          {'id': '6289731484943315811', 'name': 'One', 'language': 'en'},
          {'id': '2', 'name': 'Two', 'language': 'ja'},
        ],
      },
    ],
  },
};

List<int> protoRepository(
  PluginEcosystem ecosystem, {
  String repositoryName = 'Repo',
  int? standaloneLength,
}) {
  List<int> number(BigInt value) {
    final bytes = <int>[];
    do {
      final low = (value & BigInt.from(127)).toInt();
      value >>= 7;
      bytes.add(low | (value == BigInt.zero ? 0 : 128));
    } while (value != BigInt.zero);
    return bytes;
  }

  List<int> message(int field, List<int> bytes) => [
    ...number(BigInt.from(field * 8 + 2)),
    ...number(BigInt.from(bytes.length)),
    ...bytes,
  ];
  List<int> text(int field, String value) => message(field, utf8.encode(value));
  List<int> integer(int field, String value) => [
    ...number(BigInt.from(field * 8)),
    ...number(BigInt.parse(value)),
  ];
  final source = [
    ...integer(1, '6289731484943315811'),
    ...text(2, 'Source'),
    ...text(3, 'en'),
  ];
  final extension = standaloneLength != null
      ? [
          ...text(1, 'P'),
          ...text(2, 'org.p'),
          ...message(3, [
            ...text(1, 'https://e.com/a'),
            ...text(2, 'https://e.com/i'),
          ]),
          ...text(4, '1.6'),
          ...integer(5, '1'),
          ...text(6, '1'),
          ...text(20, 'x' * (standaloneLength - 59)),
        ]
      : [
          ...text(1, 'Plugin'),
          ...text(2, 'org.example.plugin'),
          ...message(3, [
            ...text(1, 'https://cdn.example.com/exact.apk'),
            ...text(2, 'https://cdn.example.com/icon.png'),
          ]),
          ...text(4, '1.6'),
          ...integer(5, '106004'),
          ...text(6, '1.6.4'),
          if (ecosystem == PluginEcosystem.anime) ...integer(8, '1'),
          ...message(ecosystem == PluginEcosystem.manga ? 8 : 9, source),
        ];
  if (standaloneLength != null) return message(1, extension);
  return [
    ...text(1, repositoryName),
    ...text(3, 'a' * 64),
    ...message(101, message(1, extension)),
  ];
}

class TestInventory implements PluginInventory {
  List<InstalledPlugin> installed = [];
  @override
  Future<List<InstalledPlugin>> readInstalled() async => installed;
}
