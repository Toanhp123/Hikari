import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';

/// Presentation-only labels. Never persist or use these as protocol values.
/// Current wording is retained to avoid unexpected UI changes during cleanup.
String pluginEcosystemLabel(PluginEcosystem ecosystem) => switch (ecosystem) {
  PluginEcosystem.manga => 'manga',
  PluginEcosystem.anime => 'anime',
  PluginEcosystem.novel => 'novel',
};

String pluginCompatibilityLabel(PluginCompatibility compatibility) =>
    switch (compatibility) {
      PluginCompatibility.compatible => 'compatible',
      PluginCompatibility.unsupportedPlatform => 'unsupportedPlatform',
      PluginCompatibility.unsupportedAbi => 'unsupportedAbi',
      PluginCompatibility.unsupportedModule => 'unsupportedModule',
      PluginCompatibility.unsupportedOrigin => 'unsupportedOrigin',
      PluginCompatibility.unknown => 'unknown',
    };

String apkCompatibilityLabel(ApkCompatibility compatibility) =>
    switch (compatibility) {
      ApkCompatibility.compatible => 'compatible',
      ApkCompatibility.unsupportedSigner => 'unsupportedSigner',
      ApkCompatibility.unsupportedAbi => 'unsupportedAbi',
      ApkCompatibility.invalidMetadata => 'invalidMetadata',
    };

String apkTrustLabel(ApkTrust trust) => switch (trust) {
  ApkTrust.unapproved => 'unapproved',
  ApkTrust.approved => 'approved',
};

String pluginOperationLabel(PluginOperation operation) => switch (operation) {
  PluginOperation.install => 'install',
  PluginOperation.update => 'update',
  PluginOperation.uninstall => 'uninstall',
};

String apkInstallStatusLabel(ApkInstallStatus status) => switch (status) {
  ApkInstallStatus.preparing => 'preparing',
  ApkInstallStatus.awaitingResult => 'awaitingResult',
  ApkInstallStatus.pendingUserAction => 'pendingUserAction',
  ApkInstallStatus.interrupted => 'interrupted',
  ApkInstallStatus.succeeded => 'succeeded',
  ApkInstallStatus.failed => 'failed',
  ApkInstallStatus.cancelled => 'cancelled',
  ApkInstallStatus.externallyInstalled => 'externallyInstalled',
  ApkInstallStatus.retiredUnknown => 'retiredUnknown',
  ApkInstallStatus.absentBeforeRequest => 'absentBeforeRequest',
  ApkInstallStatus.externallyRemoved => 'externallyRemoved',
};

String novelStatusLabel(NovelArtifactStatus status) => switch (status) {
  NovelArtifactStatus.ready => 'ready',
  NovelArtifactStatus.missing => 'missing',
  NovelArtifactStatus.corrupt => 'corrupt',
};

String novelAdmissionLabel(NovelAdmission admission) => switch (admission) {
  NovelAdmission.unknown => 'unknown',
  NovelAdmission.admitted => 'admitted',
};

String novelRuntimeInitializationLabel(
  NovelRuntimeInitialization initialization,
) => switch (initialization) {
  NovelRuntimeInitialization.notInitialized => 'notInitialized',
  NovelRuntimeInitialization.ready => 'ready',
  NovelRuntimeInitialization.failed => 'failed',
};
