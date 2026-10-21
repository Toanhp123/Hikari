import 'package:hikari/application/plugins/plugin_manager.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';

/// Native pointers already committed. Retry reconciliation, never infer rollback.
final class NovelReconciliationFailure implements Exception {
  const NovelReconciliationFailure(this.inventory, this.cause);
  final List<NovelArtifactInventory> inventory;
  final Object cause;
}

final class ManageNovelPlugins {
  const ManageNovelPlugins({required this.gateway, required this.manager});
  final NovelPluginGateway gateway;
  final PluginManager manager;
  Future<StagedNovelArtifact> stage(
    PluginId id, {
    required Iterable<String> origins,
    String? requestId,
  }) {
    final owner = manager.state.owners[id];
    final offers = manager.offersFor(id).where((o) => o.repositoryId == owner);
    if (owner == null || offers.length != 1) {
      throw StateError('Selected Novel owner has no offer');
    }
    return gateway.stage(offers.single, origins, requestId: requestId);
  }

  Future<List<NovelArtifactInventory>> install(
    StagedNovelArtifact stage,
  ) async {
    if (manager.state.owners[stage.artifact.metadata.id] !=
        stage.artifact.metadata.repositoryId) {
      throw StateError('Novel owner changed since staging');
    }
    final inventory = await gateway.install(stage);
    return _reconcile(inventory, stage.artifact);
  }

  Future<List<NovelArtifactInventory>> admit(NovelArtifact displayed) async {
    if (manager.state.owners[displayed.metadata.id] !=
        displayed.metadata.repositoryId) {
      throw StateError('Novel owner changed before admission');
    }
    return _reconcile(await gateway.admit(displayed), displayed);
  }

  Future<List<NovelArtifactInventory>> rollback(
    NovelArtifactInventory displayed,
  ) async {
    final inventory = await gateway.rollback(displayed);
    return _reconcile(inventory, displayed.previous);
  }

  Future<List<NovelArtifactInventory>> uninstall(
    NovelArtifact displayed,
  ) async => _reconcile(await gateway.uninstall(displayed), null);
  Future<List<NovelArtifactInventory>> _reconcile(
    List<NovelArtifactInventory> inventory,
    NovelArtifact? consent,
  ) async {
    try {
      if (consent != null) await manager.approve(consent.approval);
      await manager.reconcileInstalled();
    } catch (error) {
      throw NovelReconciliationFailure(inventory, error);
    }
    return inventory;
  }
}

/// Admission is bounded export inspection, never provider compatibility or activation.
final class NativeNovelInventory implements PluginInventory {
  const NativeNovelInventory(this.gateway);
  final NovelPluginGateway gateway;
  @override
  Future<List<InstalledPlugin>> readInstalled() async =>
      (await gateway.inventory())
          .map(
            (row) => InstalledPlugin(
              id: row.current.metadata.id,
              version: row.current.metadata.version,
              digest: row.current.digest,
              compatibility: PluginCompatibility.unknown,
            ),
          )
          .toList(growable: false);
}
