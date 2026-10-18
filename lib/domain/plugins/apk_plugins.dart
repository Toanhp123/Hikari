import 'dart:typed_data';

import 'package:hikari/domain/plugins/plugins.dart';

enum ApkCompatibility {
  compatible,
  unsupportedSigner,
  unsupportedAbi,
  invalidMetadata,
}

enum ApkTrust { unapproved, approved }

/// Inspected OS/archive identity, not repository claims. No active-source status.
final class ApkPluginIdentity {
  ApkPluginIdentity({
    required this.id,
    required this.version,
    required this.library,
    required this.entrypoint,
    required Iterable<String> currentSigners,
    required Iterable<String> signerHistory,
    required this.compatibility,
    required this.trust,
  }) : currentSigners = List.unmodifiable(currentSigners.map(pluginDigest)),
       signerHistory = List.unmodifiable(signerHistory.map(pluginDigest)) {
    if (id.ecosystem == PluginEcosystem.novel ||
        this.currentSigners.length > 32 ||
        this.signerHistory.length > 32) {
      throw const FormatException('Invalid APK identity');
    }
    if (compatibility == ApkCompatibility.compatible &&
        this.currentSigners.length != 1) {
      throw const FormatException('Compatible APK requires single signer');
    }
  }
  final PluginId id;
  final ApkPluginVersion version;
  final String library;
  final String entrypoint;
  final List<String> currentSigners;
  final List<String> signerHistory;
  final ApkCompatibility compatibility;
  final ApkTrust trust;
}

final class ApkStageRequest {
  ApkStageRequest({required this.offer, required String expectedSigner})
    : expectedSigner = pluginDigest(expectedSigner) {
    if (offer.id.ecosystem == PluginEcosystem.novel ||
        offer.version is! ApkPluginVersion) {
      throw ArgumentError('Expected APK offer');
    }
  }
  final PluginOffer offer;
  final String expectedSigner;
}

final class StagedApk {
  StagedApk({required this.token, required this.identity}) {
    if (!RegExp(r'^[A-Za-z0-9_-]{1,128}$').hasMatch(token)) {
      throw const FormatException('Invalid native stage token');
    }
  }
  final String token;
  final ApkPluginIdentity identity;
}

/// Native owns trust; approval returns only after durable native persistence.
abstract interface class ApkPluginGateway {
  Future<List<ApkPluginIdentity>> inventory();
  Future<Uint8List> fetchMetadata(Uri url);
  Future<StagedApk> stage(ApkStageRequest request, {String? requestId});

  /// Persists signer consent from platform archive inspection, not installation.
  /// Future installation must reconcile OS-owned installed identity before loading.
  Future<ApkPluginIdentity> approveStaged(String token);
  Future<void> discardStaged(String token);
  Future<ApkPluginIdentity> approveInstalled(ApkPluginIdentity identity);
  Future<void> cancel(String requestId);
  Future<bool> canInstall();
  Future<void> openInstallSettings();
  Future<ApkInstallOperation> installStaged(String token, PluginOperation kind);
  Future<ApkInstallOperation> uninstall(ApkPluginIdentity identity);
  Future<List<ApkInstallOperation>> installOperations();
  Future<ApkInstallOperation> resumeInstallConfirmation(String operationId);
  Future<ApkInstallOperation> cancelInstall(String operationId);

  /// Explicitly retires unknown outcome only after native confirms no owned session remains.
  Future<ApkInstallOperation> retireInstall(
    String operationId, {
    bool abandonOwnedSession = false,
    bool acknowledgeUnrevokedRemoval = false,
  });
}

enum ApkInstallStatus {
  preparing,
  awaitingResult,
  pendingUserAction,
  interrupted,
  succeeded,
  failed,
  cancelled,
  externallyInstalled,
  retiredUnknown,
  absentBeforeRequest,
  externallyRemoved;

  bool get isTerminal => switch (this) {
    succeeded ||
    failed ||
    cancelled ||
    retiredUnknown ||
    absentBeforeRequest ||
    externallyRemoved => true,
    _ => false,
  };
}

/// Durable native session state. Neither session submission nor inventory implies success.
final class ApkInstallOperation {
  ApkInstallOperation({
    required this.id,
    required this.pluginId,
    required this.versionCode,
    required String signer,
    required this.kind,
    required this.status,
    required this.stageTransferred,
    this.sessionId,
    this.reason,
  }) : signer = pluginDigest(signer) {
    if (!RegExp(
          r'^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$',
        ).hasMatch(id) ||
        pluginId.ecosystem == PluginEcosystem.novel ||
        versionCode < 0 ||
        (kind == PluginOperation.uninstall &&
            (sessionId != null || stageTransferred)) ||
        (sessionId != null && sessionId! < 0) ||
        (reason != null && reason!.length > 128)) {
      throw const FormatException('Invalid APK install operation');
    }
  }
  final String id;
  final PluginId pluginId;
  final int versionCode;
  final String signer;
  final PluginOperation kind;
  final ApkInstallStatus status;
  final bool stageTransferred;
  final int? sessionId;
  final String? reason;
  bool get isTerminal => status.isTerminal;
}
