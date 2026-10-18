import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';

class MemoryStore implements PluginStateStore {
  PluginState state = PluginState();
  bool fail = false;
  @override
  Future<PluginState> load() async {
    if (fail) throw StateError('private error');
    return state;
  }

  @override
  Future<void> save(PluginState value) async => state = value;
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
