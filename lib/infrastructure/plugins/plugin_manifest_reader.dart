import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:hikari/domain/plugins/plugins.dart';

/// Caller supplies bounded transport, including DNS and per-hop redirect checks.
/// This adapter only parses inert metadata; it never fetches executable assets.
final class PluginManifestReader implements PluginRepositoryReader {
  PluginManifestReader(this._fetch);
  final Future<List<int>> Function(Uri url) _fetch;
  static const maxBytes = 8 * 1024 * 1024;
  static const maxEntries = 5000;

  @override
  Future<PluginRepositorySnapshot> read(PluginRepository repository) async {
    final visited = <Uri>{};
    String? name;
    String? signer;
    Future<PluginRepositorySnapshot> visit(
      Uri url, {
      bool extensionList = false,
    }) async {
      if (visited.length >= 5 || !visited.add(url)) {
        throw const FormatException('Repository index cycle or depth exceeded');
      }
      var bytes = await _fetch(url);
      if (bytes.isEmpty || bytes.length > maxBytes) {
        throw const FormatException('Invalid manifest size');
      }
      if (bytes.length > 1 && bytes[0] == 0x1f && bytes[1] == 0x8b) {
        final sink = _BoundedBytes(maxBytes);
        final decoder = gzip.decoder.startChunkedConversion(sink);
        decoder.add(bytes);
        decoder.close();
        bytes = sink.bytes;
      }
      final first = bytes.firstWhere(
        (b) => ![9, 10, 13, 32].contains(b),
        orElse: () => 0,
      );
      Object? root;
      if (first == 0x7b || first == 0x5b) {
        try {
          root = jsonDecode(utf8.decode(bytes));
        } on FormatException {
          if (repository.ecosystem == PluginEcosystem.novel) rethrow;
          // Protobuf field 1's 0x0a tag looks like JSON whitespace; its length
          // can then look like '[' or '{'. Decode the complete bounded payload
          // before choosing JSON. Malformed fallback must still pass wire/schema
          // validation below; JSON shape errors never fall back to protobuf.
        }
      }
      if (root == null) {
        if (repository.ecosystem == PluginEcosystem.novel) {
          throw const FormatException('Novel manifest must be JSON');
        }
        root = extensionList
            ? _protoList(Uint8List.fromList(bytes), repository.ecosystem)
            : _protoRoot(Uint8List.fromList(bytes), repository.ecosystem);
      }
      if (repository.ecosystem == PluginEcosystem.novel) {
        final offers = _list(root)
            .map((v) => _novel(repository.id, _map(v)))
            .toList();
        return PluginRepositorySnapshot(
          name: repository.id.toString(),
          offers: offers,
        );
      }
      if (extensionList) {
        final list = _map(root);
        return PluginRepositorySnapshot(
          name: name!,
          signerFingerprint: signer,
          offers: _list(list['extensions'] ?? const [])
              .map((v) => _apk(repository, _map(v))),
        );
      }
      if (root is List) {
        // Legacy APK indexes have a defined sibling metadata convention.
        final metadataUrl = pluginHttpsUrl(url.resolve('repo.json').toString());
        if (!visited.contains(metadataUrl)) {
          final metadataBytes = await _fetch(metadataUrl);
          if (metadataBytes.length > maxBytes) {
            throw const FormatException('Invalid metadata size');
          }
          final metadata = _map(jsonDecode(utf8.decode(metadataBytes)));
          final meta = _map(metadata['meta']);
          name = _string(meta['name']);
          signer = _optionalString(meta['signingKeyFingerprint']);
          if (metadata['index_v2'] != null) {
            return visit(pluginHttpsUrl(_string(metadata['index_v2'])));
          }
        }
        return PluginRepositorySnapshot(
          name: name ?? repository.id.toString(),
          signerFingerprint: signer,
          offers: _list(root).map((v) => _legacy(repository, url, _map(v))),
        );
      }
      final map = _map(root);
      if (map.containsKey('meta')) {
        final meta = _map(map['meta']);
        name = _string(meta['name']);
        signer = _optionalString(meta['signingKeyFingerprint']);
        return visit(
          map['index_v2'] != null
              ? pluginHttpsUrl(_string(map['index_v2']))
              : pluginHttpsUrl(url.resolve('index.min.json').toString()),
        );
      }
      name = _string(map['name']);
      signer = _optionalString(map['signingKey']);
      if (map['extensionList'] != null && map['extensionListUrl'] != null) {
        throw const FormatException('Ambiguous extension list');
      }
      if (map['extensionListUrl'] != null) {
        return visit(
          pluginHttpsUrl(_string(map['extensionListUrl'])),
          extensionList: true,
        );
      }
      final extensions = _map(map['extensionList'])['extensions'];
      return PluginRepositorySnapshot(
        name: name!,
        signerFingerprint: signer,
        offers: _list(extensions ?? const [])
            .map((v) => _apk(repository, _map(v))),
      );
    }

    return visit(repository.id.url);
  }

  PluginOffer _novel(RepositoryId repository, Map<String, dynamic> map) {
    final id = _string(map['id']);
    final name = _string(map['name']);
    final site = _string(map['site']);
    Uri? safeSite;
    try {
      safeSite = pluginHttpsUrl(site);
    } on FormatException {
      // Upstream has HTTP, IDN and user-configured placeholder sites. Keep
      // display metadata, but do not turn these into executable origin consent.
    }
    final css = map['customCSS'] == null ? null : _string(map['customCSS']);
    final js = map['customJS'] == null ? null : _string(map['customJS']);
    return PluginOffer(
      repositoryId: repository,
      id: PluginId(PluginEcosystem.novel, id),
      name: name,
      version: NovelPluginVersion(_string(map['version'])),
      downloadUrl: _string(map['url']),
      iconUrl: _string(map['iconUrl']),
      siteUrl: safeSite?.toString(),
      siteLabel: site,
      customCssUrl: css,
      customJsUrl: js,
      compatibility: js != null || css != null
          ? PluginCompatibility.unsupportedModule
          : safeSite == null
          ? PluginCompatibility.unsupportedOrigin
          : PluginCompatibility.unknown,
      sources: [
        PluginSourceDescriptor(
          upstreamId: id,
          name: name,
          language: _string(map['lang']),
        ),
      ],
    );
  }

  PluginOffer _apk(PluginRepository repository, Map<String, dynamic> map) {
    final resources = _map(map['resources']);
    if (map['isTorrent'] != null && map['isTorrent'] is! bool) {
      throw const FormatException('Invalid torrent flag');
    }
    return PluginOffer(
      repositoryId: repository.id,
      id: PluginId(repository.ecosystem, _string(map['packageName'])),
      name: _string(map['name']),
      version: ApkPluginVersion(
        _integer(map['versionCode']),
        _string(map['versionName']),
      ),
      downloadUrl: _string(resources['apkUrl']),
      iconUrl: _string(resources['iconUrl']),
      extensionLib: _string(map['extensionLib']),
      isTorrent: map['isTorrent'] == true,
      sources: _list(map['sources'] ?? const [], limit: 256).map((v) {
        final source = _map(v);
        return PluginSourceDescriptor(
          upstreamId: _integer(source['id']).toString(),
          name: _string(source['name']),
          language: _string(source['language']),
        );
      }),
    );
  }

  PluginOffer _legacy(
    PluginRepository repository,
    Uri index,
    Map<String, dynamic> map,
  ) {
    final package = _string(map['pkg']);
    final apk = _string(map['apk']);
    if (!RegExp(r'^[A-Za-z0-9_.-]+\.apk$').hasMatch(apk) ||
        apk.contains('..') ||
        !RegExp(r'^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$').hasMatch(package)) {
      throw const FormatException('Unsafe legacy filename');
    }
    final urls = map['apkUrls'];
    final download = urls == null
        ? index.resolve('apk/$apk').toString()
        : _string(_list(urls, limit: 32).first);
    return PluginOffer(
      repositoryId: repository.id,
      id: PluginId(repository.ecosystem, package),
      name: _string(map['name']),
      version: ApkPluginVersion(_integer(map['code']), _string(map['version'])),
      downloadUrl: download,
      iconUrl: index.resolve('icon/$package.png').toString(),
      sources: _list(map['sources'] ?? const [], limit: 256).map((v) {
        final source = _map(v);
        return PluginSourceDescriptor(
          upstreamId: _integer(source['id']).toString(),
          name: _string(source['name']),
          language: _string(source['lang']),
        );
      }),
    );
  }
}

Map<String, dynamic> _map(Object? value) {
  if (value is! Map<String, dynamic> || value.length > 64) {
    throw const FormatException('Expected bounded object');
  }
  return value;
}

List<dynamic> _list(
  Object? value, {
  int limit = PluginManifestReader.maxEntries,
}) {
  if (value is! List || value.length > limit) {
    throw const FormatException('Expected bounded list');
  }
  return value;
}

String _string(Object? value) {
  if (value is! String) throw const FormatException('Expected string');
  return pluginText(value);
}

String? _optionalString(Object? value) =>
    value == null || value == '' ? null : _string(value);
int _integer(Object? value) {
  if (value is int) return value;
  if (value is String && RegExp(r'^-?\d{1,19}$').hasMatch(value)) {
    final parsed = int.tryParse(value);
    if (parsed != null) return parsed;
  }
  throw const FormatException('Expected lossless int64');
}

final class _BoundedBytes implements Sink<List<int>> {
  _BoundedBytes(this.limit);
  final int limit;
  final List<int> bytes = [];
  @override
  void add(List<int> data) {
    if (bytes.length + data.length > limit) {
      throw const FormatException('Expanded manifest too large');
    }
    bytes.addAll(data);
  }

  @override
  void close() {}
}

// Minimal wire reader for the verified v2 schema. Unknown fields are skipped;
// no protobuf runtime or executable package content enters this adapter.
Map<int, List<Object>> _wire(Uint8List bytes) {
  var offset = 0;
  var fields = 0;
  final result = <int, List<Object>>{};
  int varint() {
    var value = BigInt.zero;
    for (var i = 0; i < 10; i++) {
      if (offset >= bytes.length) {
        throw const FormatException('Truncated protobuf');
      }
      final byte = bytes[offset++];
      value |= BigInt.from(byte & 127) << (i * 7);
      if (byte < 128) {
        if (i == 9 && byte > 1) {
          throw const FormatException('Oversized protobuf integer');
        }
        return value.toSigned(64).toInt();
      }
    }
    throw const FormatException('Invalid protobuf varint');
  }

  while (offset < bytes.length) {
    if (++fields > 50000) {
      throw const FormatException('Excessive protobuf fields');
    }
    final tag = varint();
    final field = tag >> 3;
    final type = tag & 7;
    if (field <= 0) throw const FormatException('Invalid protobuf tag');
    Object? value;
    switch (type) {
      case 0:
        value = varint();
      case 1:
        offset += 8;
      case 2:
        final length = varint();
        if (length < 0 || offset + length > bytes.length) {
          throw const FormatException('Invalid protobuf length');
        }
        value = Uint8List.sublistView(bytes, offset, offset + length);
        offset += length;
      case 5:
        offset += 4;
      default:
        throw const FormatException('Unsupported protobuf wire type');
    }
    if (offset > bytes.length) {
      throw const FormatException('Truncated protobuf field');
    }
    if (value != null) (result[field] ??= []).add(value);
  }
  return result;
}

Object? _one(Map<int, List<Object>> wire, int field) {
  final values = wire[field];
  if (values == null) return null;
  if (values.length != 1) {
    throw const FormatException('Duplicate protobuf singular field');
  }
  return values.single;
}

String _ps(Map<int, List<Object>> wire, int field, {bool optional = false}) {
  final value = _one(wire, field);
  if (optional && value == null) return '';
  if (value is! Uint8List || value.length > 4096) {
    throw const FormatException('Invalid protobuf string');
  }
  return utf8.decode(value);
}

Map<int, List<Object>> _pm(Object? value) {
  if (value is! Uint8List) {
    throw const FormatException('Invalid protobuf message');
  }
  return _wire(value);
}

Map<String, dynamic> _protoRoot(Uint8List bytes, PluginEcosystem ecosystem) {
  final root = _wire(bytes);
  final inline = _one(root, 101);
  final remote = _one(root, 102);
  if (inline != null && remote != null) {
    throw const FormatException('Ambiguous protobuf list');
  }
  return {
    'name': _ps(root, 1),
    'signingKey': _ps(root, 3, optional: true),
    if (remote != null) 'extensionListUrl': _ps(root, 102),
    if (inline != null)
      'extensionList': _protoList(inline as Uint8List, ecosystem),
  };
}

Map<String, dynamic> _protoList(Uint8List bytes, PluginEcosystem ecosystem) {
  final entries = _wire(bytes)[1] ?? const [];
  if (entries.length > PluginManifestReader.maxEntries) {
    throw const FormatException('Excessive extensions');
  }
  return {
    'extensions': entries.map((value) {
      final e = _pm(value);
      final resources = _pm(_one(e, 3));
      final sourceField = ecosystem == PluginEcosystem.manga ? 8 : 9;
      final sources = e[sourceField] ?? const [];
      if (sources.length > 256) {
        throw const FormatException('Excessive sources');
      }
      return {
        'name': _ps(e, 1),
        'packageName': _ps(e, 2),
        'resources': {
          'apkUrl': _ps(resources, 1),
          'iconUrl': _ps(resources, 2),
        },
        'extensionLib': _ps(e, 4),
        'versionCode': _one(e, 5) ?? 0,
        'versionName': _ps(e, 6),
        if (ecosystem == PluginEcosystem.anime) 'isTorrent': _one(e, 8) == 1,
        'sources': sources.map((value) {
          final s = _pm(value);
          return {
            'id': _one(s, 1) ?? 0,
            'name': _ps(s, 2),
            'language': _ps(s, 3),
          };
        }).toList(),
      };
    }).toList(),
  };
}
