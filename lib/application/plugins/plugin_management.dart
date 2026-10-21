import 'package:hikari/application/plugins/manage_apk_plugins.dart';
import 'package:hikari/application/plugins/manage_novel_plugins.dart';
import 'package:hikari/application/plugins/plugin_manager.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';

/// Running APK evidence, not an inventory or trust decision.
typedef RunningApkRevision = ({PluginId id, int versionCode, String signer});

final class ApkRuntimeEvidence {
  ApkRuntimeEvidence({
    required this.initialized,
    required Iterable<RunningApkRevision> loaded,
    required Map<String, String> errors,
  }) : loaded = List.unmodifiable(loaded),
       errors = Map.unmodifiable(errors);
  final bool initialized;
  final List<RunningApkRevision> loaded;
  final Map<String, String> errors;
}

/// One app-owned management session. Never mutates the content source registry.
final class PluginManagement {
  PluginManagement({
    required this.supported,
    required this.store,
    required this.reader,
    required this.inventory,
    required this.readApks,
    required this.readNovels,
    required this.readApkRuntime,
    required this.readNovelRuntime,
    this.apkGateway,
    this.novelGateway,
  });
  final NovelPluginGateway? novelGateway;
  ManageNovelPlugins? get novelActions =>
      supported && manager != null && novelGateway != null
      ? ManageNovelPlugins(gateway: novelGateway!, manager: manager!)
      : null;
  NovelPublicationFailure? _novelPublicationFailure;
  NovelPublicationFailure? get novelPublicationFailure =>
      _novelPublicationFailure;
  bool get novelMutationsLocked => _novelPublicationFailure != null;
  String? _novelActionError;
  String? get novelActionError => _novelActionError;

  /// Publish native evidence before any fallible Dart reconciliation. Never replay a mutation.
  Future<void> mutateNovel(
    Future<List<NovelArtifactInventory>> Function(ManageNovelPlugins) action,
  ) async {
    if (novelMutationsLocked || novelActions == null) {
      throw StateError('Novel mutations unavailable');
    }
    _novelActionError = null;
    try {
      replaceNovelInventory(await action(novelActions!));
    } on NovelReconciliationFailure catch (error) {
      _retainNovelFailureInventory(error.inventory);
      _novelActionError = 'Native Novel state committed. Dart reconciliation failed; refresh evidence, never repeat mutation.';
    } on NovelPublicationFailure catch (error) {
      _novelPublicationFailure ??= error;
      if (error.inventory != null) {
        _retainNovelFailureInventory(error.inventory!);
      }
      rethrow;
    }
  }

  Future<void> refreshNovelEvidence() async {
    if (!supported || novelGateway == null) return;
    // A publication failure cannot be cleared by route recreation or inventory refresh.
    if (!novelMutationsLocked) {
      replaceNovelInventory(await novelGateway!.inventory());
    }
    _novelRuntime = await novelGateway!.runtimeSnapshot();
    if (!novelMutationsLocked) {
      try {
        await manager?.reconcileInstalled();
        _novelActionError = null;
      } catch (_) {
        _novelActionError = 'Native inventory retained. Dart reconciliation failed; no code was evaluated again.';
      }
    }
  }

  final ApkPluginGateway? apkGateway;
  ManageApkPlugins? get apkActions =>
      supported && manager != null && apkGateway != null
      ? ManageApkPlugins(gateway: apkGateway!, manager: manager!)
      : null;
  String? _operationError;
  String? get operationError => _operationError;

  void recordApkOperation(ApkInstallOperation operation) {
    _apkOperations = List.unmodifiable([
      operation,
      ...apkOperations.where((row) => row.id != operation.id),
    ]);
  }

  Future<void> refreshApkOperations() async {
    final actions = apkActions;
    if (actions == null) return;
    try {
      _apkOperations = List.unmodifiable(await actions.installOperations());
      _operationError = null;
    } on ApkInstallReconciliationFailure catch (error) {
      _apkOperations = List.unmodifiable(error.operations);
      _operationError = 'Native operation evidence retained. Inventory reconciliation failed; refresh to retry, not resubmit.';
    }
  }

  final bool supported;
  final PluginStateStore store;
  final PluginRepositoryReader reader;
  final PluginInventory inventory;
  final Future<List<ApkPluginIdentity>> Function() readApks;
  final Future<List<NovelArtifactInventory>> Function() readNovels;
  final Future<ApkRuntimeEvidence> Function() readApkRuntime;
  final Future<NovelRuntimeSnapshot> Function() readNovelRuntime;
  // Shared app session owns inventory snapshots; routes cannot assign mutable lists.
  PluginManager? manager;
  List<ApkPluginIdentity> _apks = const [];
  List<ApkPluginIdentity> get apks => _apks;
  void replaceApkInventory(Iterable<ApkPluginIdentity> rows) {
    _apks = List.unmodifiable(rows);
  }

  List<NovelArtifactInventory> _novels = const [];
  List<NovelArtifactInventory> get novels => _novels;
  void replaceNovelInventory(Iterable<NovelArtifactInventory> rows) {
    if (novelMutationsLocked) return;
    _novels = List.unmodifiable(rows);
  }

  // Publication-failure evidence is deliberately allowed to update the locked
  // inventory, but only from the failure returned by the native authority.
  void _retainNovelFailureInventory(Iterable<NovelArtifactInventory> rows) {
    _novels = List.unmodifiable(rows);
  }

  List<ApkInstallOperation> _apkOperations = const [];
  List<ApkInstallOperation> get apkOperations => _apkOperations;
  ApkRuntimeEvidence? _apkRuntime;
  ApkRuntimeEvidence? get apkRuntime => _apkRuntime;
  NovelRuntimeSnapshot? _novelRuntime;
  NovelRuntimeSnapshot? get novelRuntime => _novelRuntime;
  String? _error;
  String? get error => _error;
  String? _apkEvidenceError;
  String? get apkEvidenceError => _apkEvidenceError;
  String? _novelEvidenceError;
  String? get novelEvidenceError => _novelEvidenceError;
  Future<void>? _loading;

  /// Reads native evidence independently. A failed family does not poison the
  /// other snapshot. Persisted combined inventory is still atomic: partial
  /// failures must never be interpreted as successful uninstallation.
  Future<void> refresh() {
    if (!supported) return Future.value();
    return _loading ??= _refresh().whenComplete(() => _loading = null);
  }

  Future<void> _refresh() async {
    try {
      replaceApkInventory(await readApks());
      _apkEvidenceError = null;
    } catch (_) {
      _apkEvidenceError =
          'Could not refresh APK inventory; previous evidence retained.';
    }
    try {
      _apkRuntime = await readApkRuntime();
    } catch (_) {
      _apkEvidenceError =
          'Could not refresh all APK evidence; previous evidence retained.';
    }
    try {
      if (!novelMutationsLocked) {
        replaceNovelInventory(await readNovels());
      }
      _novelEvidenceError = null;
    } catch (_) {
      _novelEvidenceError =
          'Could not refresh Novel inventory; previous evidence retained.';
    }
    try {
      _novelRuntime = await readNovelRuntime();
    } catch (_) {
      _novelEvidenceError =
          'Could not refresh all Novel evidence; previous evidence retained.';
    }
    try {
      if (manager == null) {
        manager = await PluginManager.open(
          store: store,
          reader: reader,
          inventory: inventory,
          allowUnavailableInventory: true,
        );
      } else {
        await manager!.reconcileInstalled();
      }
      _error = manager!.inventoryFresh ? null : 'Plugin inventory is unavailable. Native evidence retained; updates are disabled until reconciliation.';
    } catch (_) {
      // Never save a partial aggregate inventory or expose exception URLs.
      // The existing manager remains usable with its previous persisted state.
      _error = 'Could not reconcile plugin inventory. Previous state retained; retry.';
    }
  }
}

final class CombinedPluginInventory implements PluginInventory {
  const CombinedPluginInventory(this.inventories);
  final List<PluginInventory> inventories;
  @override
  Future<List<InstalledPlugin>> readInstalled() async {
    final rows = <InstalledPlugin>[];
    for (final inventory in inventories) {
      rows.addAll(await inventory.readInstalled());
    }
    return rows;
  }
}
