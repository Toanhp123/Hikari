import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/infrastructure/plugins/plugin_manifest_reader.dart';

/// Opt-in inert upstream metadata check. Downloaded files stay outside code dirs.
void main() {
  final manga = Platform.environment['HIKARI_MANGA_MANIFEST'];
  final metadata = Platform.environment['HIKARI_MANGA_METADATA'];
  final novel = Platform.environment['HIKARI_NOVEL_MANIFEST'];
  test('pinned upstream repository manifests parse without executable evaluation', () async {
    final reader = PluginManifestReader((url) async {
      if (url.path.endsWith('repo.json')) return File(metadata!).readAsBytes();
      if (url.path.endsWith('plugins.min.json')) {
        return File(novel!).readAsBytes();
      }
      return File(manga!).readAsBytes();
    });
    final mangaSnapshot = await reader.read(
      PluginRepository(
        RepositoryId(
          'https://raw.githubusercontent.com/keiyoushi/extensions/da5cd0f87fc5e4074e8d2ba29e5303eaa134e6ed/index.json',
        ),
        PluginEcosystem.manga,
      ),
    );
    final novelSnapshot = await reader.read(
      PluginRepository(
        RepositoryId(
          'https://raw.githubusercontent.com/lnreader/lnreader-plugins/0cf10f698b6a75d4560e9a1f20e0980c2bc76b1e/.dist/plugins.min.json',
        ),
        PluginEcosystem.novel,
      ),
    );
    expect(mangaSnapshot.offers, isNotEmpty);
    expect(novelSnapshot.offers, hasLength(302));
    // Visible evidence only; no network-dependent default gate.
    // ignore: avoid_print
    print(
      'Pinned manifests: manga=${mangaSnapshot.offers.length}, novel=${novelSnapshot.offers.length}',
    );
  }, skip: manga == null || metadata == null || novel == null);
}
