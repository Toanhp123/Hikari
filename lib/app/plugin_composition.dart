import 'package:hikari/application/plugins/manage_apk_plugins.dart';
import 'package:hikari/application/plugins/manage_novel_plugins.dart';
import 'package:flutter/foundation.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/infrastructure/extensions/mihon/mihon_extension_gateway.dart';
import 'package:hikari/infrastructure/plugins/file_plugin_state_store.dart';
import 'package:hikari/infrastructure/plugins/method_channel_apk_plugins.dart';
import 'package:hikari/infrastructure/plugins/method_channel_novel_plugins.dart';
import 'package:hikari/infrastructure/plugins/plugin_manifest_reader.dart';
import 'package:hikari/domain/plugins/plugins.dart';

PluginManagement createPluginManagement() {
  final apk = MethodChannelApkPlugins();
  const novel = MethodChannelNovelPlugins();
  const mihon = MethodChannelMihonExtensionGateway();
  return PluginManagement(
    supported: !kIsWeb && defaultTargetPlatform == TargetPlatform.android,
    store: FilePluginStateStore(),
    inventory: CombinedPluginInventory([
      NativeApkInventory(apk),
      const NativeNovelInventory(novel),
    ]),
    reader: PluginManifestReader(apk.fetchMetadata),
    apkGateway: apk,
    novelGateway: novel,
    readApks: apk.inventory,
    readNovels: novel.inventory,
    readNovelRuntime: novel.runtimeSnapshot,
    readApkRuntime: () async {
      final snapshot = await mihon.runtimeSnapshot();
      return ApkRuntimeEvidence(
        initialized: snapshot.initialized,
        loaded: List.unmodifiable(
          snapshot.sources.map(
            (source) => (
              id: PluginId(PluginEcosystem.manga, source.packageName),
              versionCode: source.versionCode,
              signer: source.signer,
            ),
          ),
        ),
        errors: Map.unmodifiable(snapshot.errors),
      );
    },
  );
}
