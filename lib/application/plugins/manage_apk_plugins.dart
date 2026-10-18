import 'package:hikari/application/plugins/plugin_manager.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';

/// Native approval already committed; retry inventory reconciliation, not consent.
final class ApkApprovalReconciliationFailure implements Exception {
  const ApkApprovalReconciliationFailure(this.cause);
  final Object cause;
}

/// Native operation state remains authoritative if Dart inventory persistence fails.
final class ApkInstallReconciliationFailure implements Exception {
  const ApkInstallReconciliationFailure(this.operations, this.cause);
  final List<ApkInstallOperation> operations;
  final Object cause;
}

final class ManageApkPlugins {
  const ManageApkPlugins({required this.gateway, required this.manager});
  final ApkPluginGateway gateway;
  final PluginManager manager;

  Future<StagedApk> stage(PluginId id, {String? requestId}) async {
    final owner = manager.state.owners[id];
    for (final offer in manager.offersFor(id)) {
      if (offer.repositoryId != owner) continue;
      final repository = manager.state.repositories.firstWhere(
        (r) => r.id == owner,
      );
      final signer = repository.snapshot?.signerFingerprint;
      if (signer == null) {
        throw StateError('Selected repository declares no signer expectation');
      }
      return gateway.stage(
        ApkStageRequest(offer: offer, expectedSigner: signer),
        requestId: requestId,
      );
    }
    throw StateError('Selected update owner has no offer');
  }

  /// Returns durable session submission, not installation or source activation.
  Future<ApkInstallOperation> installStaged(
    String token,
    PluginOperation kind,
  ) => gateway.installStaged(token, kind);

  /// Opens normal system removal UI; user content and immutable registry stay untouched.
  Future<ApkInstallOperation> uninstall(ApkPluginIdentity identity) =>
      gateway.uninstall(identity);

  /// User-directed unknown-outcome resolution, never an inferred success or cancellation.
  Future<ApkInstallOperation> retireInstall(
    String operationId, {
    bool abandonOwnedSession = false,
    bool acknowledgeUnrevokedRemoval = false,
  }) => gateway.retireInstall(
    operationId,
    abandonOwnedSession: abandonOwnedSession,
    acknowledgeUnrevokedRemoval: acknowledgeUnrevokedRemoval,
  );

  Future<List<ApkInstallOperation>> installOperations() async {
    final operations = await gateway.installOperations();
    try {
      await manager.reconcileInstalled();
    } catch (error) {
      throw ApkInstallReconciliationFailure(operations, error);
    }
    return operations;
  }

  Future<ApkPluginIdentity> approveStaged(String token) async {
    final inspected = await gateway.approveStaged(token);
    await _reconcileApproval();
    return inspected;
  }

  Future<ApkPluginIdentity> approveInstalled(ApkPluginIdentity identity) async {
    final inspected = await gateway.approveInstalled(identity);
    await _reconcileApproval();
    return inspected;
  }

  Future<void> _reconcileApproval() async {
    try {
      await manager.reconcileInstalled();
    } catch (error) {
      throw ApkApprovalReconciliationFailure(error);
    }
  }
}

/// Inventory is not activation and does not copy native trust into Dart approvals.
final class NativeApkInventory implements PluginInventory {
  const NativeApkInventory(this.gateway);
  final ApkPluginGateway gateway;
  @override
  Future<List<InstalledPlugin>> readInstalled() async =>
      (await gateway.inventory())
          .where((identity) => identity.currentSigners.length == 1)
          .map(
            (identity) => InstalledPlugin(
              id: identity.id,
              version: identity.version,
              digest: identity.currentSigners.single,
              compatibility: switch (identity.compatibility) {
                ApkCompatibility.compatible => PluginCompatibility.compatible,
                ApkCompatibility.unsupportedAbi =>
                  PluginCompatibility.unsupportedAbi,
                ApkCompatibility.unsupportedSigner ||
                ApkCompatibility.invalidMetadata => PluginCompatibility.unknown,
              },
            ),
          )
          .toList(growable: false);
}
