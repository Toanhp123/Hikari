import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';

class MemoryStore implements PluginStateStore {
  PluginState state = PluginState();
  bool fail = false;
  int saves = 0;
  @override
  Future<PluginState> load() async {
    if (fail) throw StateError('private error');
    return state;
  }

  @override
  Future<void> save(PluginState value) async {
    saves++;
    state = value;
  }
}

class Reader implements PluginRepositoryReader {
  bool fail = false;
  @override
  Future<PluginRepositorySnapshot> read(PluginRepository repository) async {
    if (fail) throw StateError('private URL');
    return repository.snapshot ??
        PluginRepositorySnapshot(name: 'Repository', offers: []);
  }
}

PluginManagement management({
  required MemoryStore store,
  bool supported = true,
  Reader? reader,
  void Function()? nativeCall,
}) => PluginManagement(
  supported: supported,
  inventory: const CombinedPluginInventory([]),
  store: store,
  reader: reader ?? Reader(),
  readApks: () async {
    nativeCall?.call();
    return [];
  },
  readNovels: () async => [],
  readApkRuntime: () async =>
      ApkRuntimeEvidence(initialized: false, loaded: [], errors: {}),
  readNovelRuntime: () async => NovelRuntimeSnapshot(
    initialization: NovelRuntimeInitialization.notInitialized,
    failure: null,
    loaded: [],
    issues: [],
  ),
);

void main() {
  test('APK runtime evidence owns immutable native value copies', () {
    final loaded = <RunningApkRevision>[
      (
        id: PluginId(PluginEcosystem.manga, 'org.example.plugin'),
        versionCode: 1,
        signer: 'a' * 64,
      ),
    ];
    final errors = <String, String>{'org.example.other': 'untrusted'};
    final evidence = ApkRuntimeEvidence(
      initialized: true,
      loaded: loaded,
      errors: errors,
    );
    loaded.clear();
    errors.clear();
    expect(evidence.loaded, hasLength(1));
    expect(evidence.errors, {'org.example.other': 'untrusted'});
    expect(() => evidence.loaded.clear(), throwsUnsupportedError);
    expect(() => evidence.errors.clear(), throwsUnsupportedError);
  });
  test(
    'failed async open remains retryable and does not replace persisted state',
    () async {
      final store = MemoryStore()..fail = true;
      final session = management(store: store);
      await session.refresh();
      expect(session.manager, isNull);
      expect(session.error, isNotNull);
      store.fail = false;
      await session.refresh();
      expect(session.manager, isNotNull);
      expect(session.error, isNull);
    },
  );
  test(
    'failure of Novel evidence does not block APK evidence or policy browsing',
    () async {
      var apkCalls = 0;
      var runtimeCalls = 0;
      final session = PluginManagement(
        supported: true,
        store: MemoryStore(),
        reader: Reader(),
        inventory: const CombinedPluginInventory([]),
        readApks: () async {
          apkCalls++;
          return [];
        },
        readNovels: () async => throw StateError('Novel provider unavailable'),
        readApkRuntime: () async {
          runtimeCalls++;
          return ApkRuntimeEvidence(initialized: true, loaded: [], errors: {});
        },
        readNovelRuntime: () async =>
            throw StateError('Novel runtime unavailable'),
      );
      await session.refresh();
      expect(apkCalls, 1);
      expect(runtimeCalls, 1);
      expect(session.apkRuntime?.initialized, isTrue);
      expect(session.novelEvidenceError, isNotNull);
      expect(session.apkEvidenceError, isNull);
      expect(session.manager, isNotNull);
      expect(session.error, isNull);
    },
  );

  test('failed APK read retains last good immutable inventory', () async {
    var shouldFail = false;
    final identity = ApkPluginIdentity(
      id: PluginId(PluginEcosystem.manga, 'org.example.manga'),
      version: ApkPluginVersion(3, '3'),
      library: '1.4',
      entrypoint: 'Main',
      currentSigners: ['a' * 64],
      signerHistory: [],
      compatibility: ApkCompatibility.compatible,
      trust: ApkTrust.approved,
    );
    final session = PluginManagement(
      supported: true,
      store: MemoryStore(),
      reader: Reader(),
      inventory: const CombinedPluginInventory([]),
      readApks: () async {
        if (shouldFail) throw StateError('private URL');
        return [identity];
      },
      readNovels: () async => [],
      readApkRuntime: () async =>
          ApkRuntimeEvidence(initialized: true, loaded: [], errors: {}),
      readNovelRuntime: () async => NovelRuntimeSnapshot(
        initialization: NovelRuntimeInitialization.notInitialized,
        failure: null,
        loaded: [],
        issues: [],
      ),
    );
    await session.refresh();
    shouldFail = true;
    await session.refresh();
    expect(session.apks.single, same(identity));
    expect(() => session.apks.clear(), throwsUnsupportedError);
    expect(session.apkEvidenceError, isNotNull);
    expect(session.novelEvidenceError, isNull);
  });

  test(
    'combined inventory does not publish partial family results on failure',
    () async {
      final installed = InstalledPlugin(
        id: PluginId(PluginEcosystem.manga, 'org.example.manga'),
        version: ApkPluginVersion(1, '1'),
        digest: 'a' * 64,
        compatibility: PluginCompatibility.compatible,
      );
      final repositoryId = RepositoryId('https://example.org/index.json');
      final repository = PluginRepository(
        repositoryId,
        PluginEcosystem.manga,
        snapshot: PluginRepositorySnapshot(
          name: 'Updates',
          offers: [
            PluginOffer(
              repositoryId: repositoryId,
              id: installed.id,
              name: 'Manga update',
              version: ApkPluginVersion(2, '2'),
              downloadUrl: 'https://example.org/plugin.apk',
              iconUrl: 'https://example.org/icon.png',
              sources: [],
            ),
          ],
        ),
      );
      final store = MemoryStore()
        ..state = PluginState(
          installed: [installed],
          repositories: [repository],
          owners: {installed.id: repositoryId},
        );
      final first = TestInventory([installed]);
      final second = TestInventory([])..fail = true;
      final session = PluginManagement(
        supported: true,
        store: store,
        reader: Reader(),
        inventory: CombinedPluginInventory([first, second]),
        readApks: () async => [],
        readNovels: () async => [],
        readApkRuntime: () async =>
            ApkRuntimeEvidence(initialized: false, loaded: [], errors: {}),
        readNovelRuntime: () async => NovelRuntimeSnapshot(
          initialization: NovelRuntimeInitialization.notInitialized,
          failure: null,
          loaded: [],
          issues: [],
        ),
      );
      await session.refresh();
      expect(store.state.installed.single, same(installed));
      expect(
        store.saves,
        0,
      ); // Unavailable family must never publish partial inventory.
      expect(session.manager, isNotNull);
      expect(session.manager!.inventoryFresh, isFalse);
      expect(session.manager!.updateCandidates, isEmpty);
      expect(session.error, isNotNull);
      second.fail = false;
      await session.refresh();
      expect(session.manager, isNotNull);
      expect(session.manager!.inventoryFresh, isTrue);
      expect(store.saves, 1);
      expect(store.state.installed.single.id, installed.id);
      expect(session.manager!.updateCandidates, hasLength(1));
      // A later outage must withdraw update suggestions without overwriting
      // the last reconciled inventory or treating an absence as uninstall.
      second.fail = true;
      await session.refresh();
      expect(session.manager!.inventoryFresh, isFalse);
      expect(session.manager!.updateCandidates, isEmpty);
      expect(store.saves, 1);
      expect(store.state.installed.single.id, installed.id);
      expect(session.error, isNotNull);
    },
  );

  test(
    'unsupported session never calls native readers or opens state',
    () async {
      var calls = 0;
      final session = management(
        store: MemoryStore()..fail = true,
        supported: false,
        nativeCall: () => calls++,
      );
      await session.refresh();
      expect(calls, 0);
      expect(session.manager, isNull);
    },
  );
}

class TestInventory implements PluginInventory {
  TestInventory(this.rows);
  List<InstalledPlugin> rows;
  bool fail = false;
  @override
  Future<List<InstalledPlugin>> readInstalled() async {
    if (fail) throw StateError('native inventory unavailable');
    return rows;
  }
}
