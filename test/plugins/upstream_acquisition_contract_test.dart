import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/infrastructure/plugins/plugin_manifest_reader.dart';

// Exact records from LNReader/lnreader-plugins .dist/plugins.min.json at
// 0cf10f698b6a75d4560e9a1f20e0980c2bc76b1e (offline, inert metadata).
const _upstream = [
  {
    'id': 'lightnovel.vn',
    'name': 'Light Novel VN',
    'site': 'https://lightnovel.vn',
    'lang': 'Tiếng Việt',
    'version': '1.0.0',
    'url': 'https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/vietnamese/lightnovelvn.js',
    'iconUrl': 'https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/public/static/src/vi/lightnovelvn/icon.png',
  },
  {
    'id': 'ln.hako',
    'name': 'Hako',
    'site': 'https://ln.hako.vn',
    'lang': 'Tiếng Việt',
    'version': '1.2.0',
    'url': 'https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/vietnamese/LNHako.js',
    'iconUrl': 'https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/public/static/src/vi/hakolightnovel/icon.png',
  },
  {
    'id': 'genesistudio',
    'name': 'Genesis',
    'site': 'https://genesistudio.com',
    'lang': 'English',
    'version': '2.0.1',
    'url': 'https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/.js/src/plugins/english/genesis.js',
    'iconUrl': 'https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/public/static/src/en/genesis/icon.png',
    'customCSS': 'https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/public/static/src/en/genesis/customCSS.css',
  },
];

void main() {
  test(
    'real LNReader offers preserve dotted IDs through Novel staging metadata',
    () async {
      final repository = PluginRepository(
        RepositoryId(
          'https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json',
        ),
        PluginEcosystem.novel,
      );
      final reader = PluginManifestReader(
        (_) async => utf8.encode(jsonEncode(_upstream)),
      );
      final offers = (await reader.read(repository)).offers;
      expect(offers, hasLength(3));
      for (final offer in offers.where((o) => o.customCssUrl == null)) {
        final metadata = NovelArtifactMetadata(
          id: offer.id,
          name: offer.name,
          version: offer.version as NovelPluginVersion,
          repositoryId: offer.repositoryId,
          site: offer.siteUrl!.toString(),
          origins: [offer.siteUrl!.origin],
          language: offer.sources.single.language,
        );
        expect(metadata.id.upstreamId, contains('.'));
        expect(metadata.language, 'Tiếng Việt');
        expect(metadata.site.scheme, 'https');
      }
      final unsupported = offers.singleWhere(
        (o) => o.id.upstreamId == 'genesistudio',
      );
      expect(unsupported.compatibility, PluginCompatibility.unsupportedModule);
      expect(unsupported.customCssUrl, isNotNull);
    },
  );
}
