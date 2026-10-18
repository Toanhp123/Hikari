import 'dart:convert';
import 'dart:io';

import 'package:hikari/domain/plugins/plugins.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

/// Durable user consent/inventory, never stored in reconstructible cache.
/// One app-owned instance serializes reads and flushed atomic file replacement.
final class FilePluginStateStore implements PluginStateStore {
  FilePluginStateStore({this._directory});
  final Directory? _directory;
  Future<void> _tail = Future.value();
  static const _maxBytes = 32 * 1024 * 1024;

  Future<T> _serial<T>(Future<T> Function() action) {
    final next = _tail.then((_) => action());
    _tail = next.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return next;
  }

  Future<File> _file() async => File(
    path.join(
      (_directory ?? await getApplicationSupportDirectory()).path,
      'plugins.json',
    ),
  );

  @override
  Future<PluginState> load() => _serial(() async {
    final file = await _file();
    if (!await file.exists()) return PluginState();
    if (await file.length() > _maxBytes) {
      throw const FormatException('Plugin state too large');
    }
    return _decode(jsonDecode(await file.readAsString()));
  });

  @override
  Future<void> save(PluginState state) => _serial(() async {
    final bytes = utf8.encode(jsonEncode(_encode(state)));
    if (bytes.length > _maxBytes) {
      throw const FormatException('Plugin state too large');
    }
    final file = await _file();
    await file.parent.create(recursive: true);
    final temporary = File('${file.path}.tmp');
    try {
      await temporary.writeAsBytes(bytes, flush: true);
      // Same-directory rename replaces the old file without delete-then-write.
      // Failure leaves old state intact; orphan .tmp is never read as committed.
      await temporary.rename(file.path);
    } finally {
      if (await temporary.exists()) await temporary.delete();
    }
  });
}

Map<String, dynamic> _id(PluginId id) => {
  'ecosystem': id.ecosystem.name,
  'id': id.upstreamId,
};
Map<String, dynamic> _version(PluginVersion version) => switch (version) {
  ApkPluginVersion() => {'code': version.code, 'name': version.name},
  NovelPluginVersion() => {'value': version.value},
};
Map<String, dynamic> _offer(PluginOffer offer) => {
  'id': _id(offer.id),
  'name': offer.name,
  'version': _version(offer.version),
  'downloadUrl': offer.downloadUrl.toString(),
  'iconUrl': offer.iconUrl.toString(),
  'extensionLib': offer.extensionLib,
  'isTorrent': offer.isTorrent,
  'siteUrl': offer.siteUrl?.toString(),
  'siteLabel': offer.siteLabel,
  'customCssUrl': offer.customCssUrl?.toString(),
  'customJsUrl': offer.customJsUrl?.toString(),
  'compatibility': offer.compatibility.name,
  'sources': offer.sources
      .map((s) => {'id': s.upstreamId, 'name': s.name, 'language': s.language})
      .toList(),
};
Map<String, dynamic> _encode(PluginState state) => {
  'schema': 1,
  'repositories': state.repositories
      .map(
        (r) => {
          'url': r.id.toString(),
          'ecosystem': r.ecosystem.name,
          'error': r.refreshError,
          'snapshot': r.snapshot == null
              ? null
              : {
                  'name': r.snapshot!.name,
                  'signer': r.snapshot!.signerFingerprint,
                  'offers': r.snapshot!.offers.map(_offer).toList(),
                },
        },
      )
      .toList(),
  'installed': state.installed
      .map(
        (i) => {
          'id': _id(i.id),
          'version': _version(i.version),
          'digest': i.digest,
          'compatibility': i.compatibility.name,
        },
      )
      .toList(),
  'owners': state.owners.entries
      .map((e) => {'id': _id(e.key), 'url': e.value.toString()})
      .toList(),
  'approvals': state.approvals
      .map(
        (a) => {
          'id': _id(a.id),
          'url': a.repositoryId.toString(),
          'kind': a.kind.name,
          'digest': a.digest,
          'origins': a.origins.toList(),
        },
      )
      .toList(),
  'operations': state.operations
      .map(
        (o) => {
          'id': _id(o.id),
          'operation': o.operation.name,
          'status': o.status.name,
        },
      )
      .toList(),
};
Map<String, dynamic> _map(Object? value) {
  if (value is! Map<String, dynamic> || value.length > 32) {
    throw const FormatException('Invalid plugin state object');
  }
  return value;
}

List<dynamic> _list(Object? value, {int limit = 5000}) {
  if (value is! List || value.length > limit) {
    throw const FormatException('Invalid plugin state list');
  }
  return value;
}

String _string(Object? value) {
  if (value is! String) {
    throw const FormatException('Invalid plugin state string');
  }
  return pluginText(value);
}

String? _nullable(Object? value) => value == null ? null : _string(value);
T _enum<T extends Enum>(List<T> values, Object? value) {
  for (final entry in values) {
    if (entry.name == value) return entry;
  }
  throw const FormatException('Unknown plugin state enum');
}

PluginId _readId(Object? value) {
  final m = _map(value);
  return PluginId(
    _enum(PluginEcosystem.values, m['ecosystem']),
    _string(m['id']),
  );
}

PluginVersion _readVersion(Object? value, PluginEcosystem ecosystem) {
  final m = _map(value);
  if (ecosystem == PluginEcosystem.novel) {
    if (m.keys.toSet().difference({'value'}).isNotEmpty) {
      throw const FormatException('Unexpected novel version fields');
    }
    return NovelPluginVersion(_string(m['value']));
  }
  final code = m['code'];
  if (code is! int || m.keys.toSet().difference({'code', 'name'}).isNotEmpty) {
    throw const FormatException('Invalid APK state version');
  }
  return ApkPluginVersion(code, _string(m['name']));
}

PluginOffer _readOffer(Object? value, RepositoryId repository) {
  final m = _map(value);
  final id = _readId(m['id']);
  if (m['isTorrent'] is! bool) {
    throw const FormatException('Invalid torrent state');
  }
  return PluginOffer(
    repositoryId: repository,
    id: id,
    name: _string(m['name']),
    version: _readVersion(m['version'], id.ecosystem),
    downloadUrl: _string(m['downloadUrl']),
    iconUrl: _string(m['iconUrl']),
    extensionLib: _nullable(m['extensionLib']),
    isTorrent: m['isTorrent'] as bool,
    siteUrl: _nullable(m['siteUrl']),
    siteLabel: _nullable(m['siteLabel']),
    customCssUrl: _nullable(m['customCssUrl']),
    customJsUrl: _nullable(m['customJsUrl']),
    compatibility: _enum(PluginCompatibility.values, m['compatibility']),
    sources: _list(m['sources'], limit: 256).map((value) {
      final s = _map(value);
      return PluginSourceDescriptor(
        upstreamId: _string(s['id']),
        name: _string(s['name']),
        language: _string(s['language']),
      );
    }),
  );
}

PluginState _decode(Object? value) {
  final root = _map(value);
  if (root['schema'] != 1) {
    throw const FormatException('Unknown plugin state schema');
  }
  final owners = <PluginId, RepositoryId>{};
  for (final value in _list(root['owners'])) {
    final m = _map(value);
    final id = _readId(m['id']);
    if (owners.containsKey(id)) {
      throw const FormatException('Duplicate update owner');
    }
    owners[id] = RepositoryId(_string(m['url']));
  }
  return PluginState(
    repositories: _list(root['repositories']).map((value) {
      final m = _map(value);
      final id = RepositoryId(_string(m['url']));
      final snapshot = m['snapshot'] == null ? null : _map(m['snapshot']);
      return PluginRepository(
        id,
        _enum(PluginEcosystem.values, m['ecosystem']),
        refreshError: _nullable(m['error']),
        snapshot: snapshot == null
            ? null
            : PluginRepositorySnapshot(
                name: _string(snapshot['name']),
                signerFingerprint: _nullable(snapshot['signer']),
                offers: _list(snapshot['offers']).map((v) => _readOffer(v, id)),
              ),
      );
    }),
    installed: _list(root['installed']).map((value) {
      final m = _map(value);
      final id = _readId(m['id']);
      return InstalledPlugin(
        id: id,
        version: _readVersion(m['version'], id.ecosystem),
        digest: _string(m['digest']),
        compatibility: _enum(PluginCompatibility.values, m['compatibility']),
      );
    }),
    owners: owners,
    approvals: _list(root['approvals']).map((value) {
      final m = _map(value);
      return PluginApproval(
        id: _readId(m['id']),
        repositoryId: RepositoryId(_string(m['url'])),
        kind: _enum(PluginApprovalKind.values, m['kind']),
        digest: _string(m['digest']),
        origins: _list(m['origins'], limit: 32).map(_string),
      );
    }),
    operations: _list(root['operations']).map((value) {
      final m = _map(value);
      return PendingPluginOperation(
        _readId(m['id']),
        _enum(PluginOperation.values, m['operation']),
        _enum(PluginOperationStatus.values, m['status']),
      );
    }),
  );
}
