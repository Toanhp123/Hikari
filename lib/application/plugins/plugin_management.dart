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
  NovelPublicationFailure? novelPublicationFailure;
  bool get novelMutationsLocked => novelPublicationFailure != null;
  String? novelActionError;

  /// Publish native evidence before any fallible Dart reconciliation. Never replay a mutation.
  Future<void> mutateNovel(
    Future<List<NovelArtifactInventory>> Function(ManageNovelPlugins) action,
  ) async {
    if (novelMutationsLocked || novelActions == null) {
      throw StateError('Novel mutations unavailable');
    }
    novelActionError = null;
    try {
      novels = List.unmodifiable(await action(novelActions!));
    } on NovelReconciliationFailure catch (error) {
      novels = List.unmodifiable(error.inventory);
      novelActionError = 'Native Novel state committed. Dart reconciliation failed; refresh evidence, never repeat mutation.';
    } on NovelPublicationFailure catch (error) {
      novelPublicationFailure ??= error;
      if (error.inventory != null) novels = List.unmodifiable(error.inventory!);
      rethrow;
    }
  }

  Future<void> refreshNovelEvidence() async {
    if (!supported || novelGateway == null) return;
    // A publication failure cannot be cleared by route recreation or inventory refresh.
    if (!novelMutationsLocked) {
      novels = List.unmodifiable(await novelGateway!.inventory());
    }
    novelRuntime = await novelGateway!.runtimeSnapshot();
    if (!novelMutationsLocked) {
      try {
        await manager?.reconcileInstalled();
        novelActionError = null;
      } catch (_) {
        novelActionError = 'Native inventory retained. Dart reconciliation failed; no code was evaluated again.';
      }
    }
  }

  final ApkPluginGateway? apkGateway;
  ManageApkPlugins? get apkActions =>
      supported && manager != null && apkGateway != null
      ? ManageApkPlugins(gateway: apkGateway!, manager: manager!)
      : null;
  List<ApkInstallOperation> apkOperations = const [];
  String? operationError;

  void recordApkOperation(ApkInstallOperation operation) {
    apkOperations = List.unmodifiable([
      operation,
      ...apkOperations.where((row) => row.id != operation.id),
    ]);
  }

  Future<void> refreshApkOperations() async {
    final actions = apkActions;
    if (actions == null) return;
    try {
      apkOperations = List.unmodifiable(await actions.installOperations());
      operationError = null;
    } on ApkInstallReconciliationFailure catch (error) {
      apkOperations = List.unmodifiable(error.operations);
      operationError = 'Native operation evidence retained. Inventory reconciliation failed; refresh to retry, not resubmit.';
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
  PluginManager? manager;
  List<ApkPluginIdentity> apks = const [];
  List<NovelArtifactInventory> novels = const [];
  ApkRuntimeEvidence? apkRuntime;
  NovelRuntimeSnapshot? novelRuntime;
  String? error;
  Future<void>? _loading;

  /// Failure is visible and retryable; bootstrap may start this without awaiting it.
  Future<void> refresh() {
    if (!supported) return Future.value();
    return _loading ??= _refresh().whenComplete(() => _loading = null);
  }

  Future<void> _refresh() async {
    try {
      final apkRows = await readApks();
      final novelRows = await readNovels();
      final apkEvidence = await readApkRuntime();
      final novelEvidence = await readNovelRuntime();
      apks = List.unmodifiable(apkRows);
      if (!novelMutationsLocked) novels = List.unmodifiable(novelRows);
      apkRuntime = apkEvidence;
      novelRuntime = novelEvidence;
      if (manager == null) {
        manager = await PluginManager.open(
          store: store,
          reader: reader,
          inventory: inventory,
        );
      } else {
        await manager!.reconcileInstalled();
      }
      error = null;
    } catch (_) {
      // Never replace malformed persisted consent or expose transport exception URLs.
      error = 'Could not load plugin management. Retry without changing your sources.';
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
