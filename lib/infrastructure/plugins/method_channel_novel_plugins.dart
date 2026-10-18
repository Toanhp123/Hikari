import 'package:flutter/services.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';

final class MethodChannelNovelPlugins implements NovelPluginGateway {
  const MethodChannelNovelPlugins();
  static const _channel = MethodChannel('hikari/plugins');
  static int _sequence = 0;

  Future<Object?> _invoke(
    String method, [
    Map<String, Object?>? arguments,
  ]) async {
    try {
      return await _channel.invokeMethod<Object?>(method, arguments);
    } on PlatformException catch (error) {
      if (error.code == 'novel_admission_rejected') {
        const reasons = {
          'execution-rejected',
          'unsupported-module',
          'missing-required-method',
          'host-operation-denied',
          'export-metadata-mismatch',
        };
        final details = error.details;
        final reason = details is Map ? details['reason'] : null;
        throw NovelAdmissionRejected(
          reasons.contains(reason) ? reason as String : 'execution-rejected',
        );
      }
      if (error.code == 'novel_publication_unknown' ||
          error.code == 'novel_pointer_sync_failed') {
        final kind = error.code == 'novel_publication_unknown'
            ? NovelPublicationFailureKind.publicationUnknown
            : NovelPublicationFailureKind.pointerSyncFailed;
        List<NovelArtifactInventory>? inventory;
        List<NovelAttemptedPointer>? attempted;
        if (kind == NovelPublicationFailureKind.pointerSyncFailed) {
          try {
            inventory = _inventory(error.details);
          } on FormatException {
            /* Mutation still locked. */
          }
        } else {
          Map<Object?, Object?>? details;
          try {
            details = _map(error.details, {
              'publication',
              'lastConfirmedInventory',
              'attemptedPointers',
            });
          } on FormatException {
            /* Unvalidated evidence omitted. */
          }
          if (details != null && details['publication'] == 'unknown') {
            try {
              inventory = _inventory(details['lastConfirmedInventory']);
            } on FormatException {
              /* Keep separately valid attempted pointers. */
            }
            try {
              attempted = _attempted(details['attemptedPointers']);
            } on FormatException {
              /* Keep separately valid confirmed inventory. */
            }
          }
        }
        throw NovelPublicationFailure(
          kind: kind,
          inventory: inventory,
          attempted: attempted,
        );
      }
      rethrow;
    }
  }

  @override
  Future<StagedNovelArtifact> stage(
    PluginOffer offer,
    Iterable<String> origins, {
    String? requestId,
  }) async {
    if (offer.version is! NovelPluginVersion ||
        offer.siteUrl == null ||
        offer.customJsUrl != null ||
        offer.customCssUrl != null) {
      throw const FormatException('Unsupported Novel offer');
    }
    final metadata = NovelArtifactMetadata(
      id: offer.id,
      name: offer.name,
      version: offer.version as NovelPluginVersion,
      repositoryId: offer.repositoryId,
      site: offer.siteUrl.toString(),
      origins: origins,
    );
    final row = _map(
      await _invoke('novelStage', {
        'requestId':
            requestId ??
            'novel-${DateTime.now().microsecondsSinceEpoch}-${_sequence++}',
        'url': offer.downloadUrl.toString(),
        'metadata': _metadata(metadata),
      }),
      {'token', 'artifact'},
    );
    final artifact = _artifact(row['artifact']);
    final captured = artifact.metadata;
    Uri rootPath(Uri uri) => uri.path.isEmpty ? uri.replace(path: '/') : uri;
    if (captured.id != metadata.id ||
        captured.name != metadata.name ||
        captured.version.value != metadata.version.value ||
        rootPath(captured.repositoryId.url) !=
            rootPath(metadata.repositoryId.url) ||
        rootPath(captured.site) != rootPath(metadata.site) ||
        captured.origins.length != metadata.origins.length ||
        !captured.origins.every(metadata.origins.contains)) {
      throw const FormatException('Native Novel stage metadata changed');
    }
    return StagedNovelArtifact(
      token: _string(row['token']),
      artifact: NovelArtifact(metadata: metadata, digest: artifact.digest),
    );
  }

  @override
  Future<NovelRuntimeSnapshot> runtimeSnapshot() async =>
      _runtimeSnapshot(await _invoke('novelRuntimeSnapshot'));
  @override
  Future<List<NovelArtifactInventory>> inventory() async =>
      _inventory(await _invoke('novelInventory'));
  @override
  Future<List<NovelArtifactInventory>> admit(NovelArtifact displayed) async =>
      _inventory(await _invoke('novelAdmit', {'artifact': _encode(displayed)}));
  @override
  Future<List<NovelArtifactInventory>> install(
    StagedNovelArtifact stage,
  ) async => _inventory(
    await _invoke('novelInstall', {
      'token': stage.token,
      'artifact': _encode(stage.artifact),
    }),
  );
  @override
  Future<List<NovelArtifactInventory>> rollback(
    NovelArtifactInventory displayed,
  ) async {
    final previous = displayed.previous;
    if (previous == null) throw StateError('No previous Novel artifact');
    return _inventory(
      await _invoke('novelRollback', {
        'artifact': _encode(displayed.current),
        'previous': _encode(previous),
      }),
    );
  }

  @override
  Future<List<NovelArtifactInventory>> uninstall(
    NovelArtifact displayed,
  ) async => _inventory(
    await _invoke('novelUninstall', {'artifact': _encode(displayed)}),
  );
  @override
  Future<void> discard(String token) =>
      _channel.invokeMethod<void>('novelDiscard', {'token': token});
  @override
  Future<void> cancel(String requestId) =>
      _channel.invokeMethod<void>('cancel', {'requestId': requestId});
}

List<NovelAttemptedPointer> _attempted(Object? value) {
  if (value is! List || value.length > 500) {
    throw const FormatException('Invalid attempted Novel pointers');
  }
  final ids = <PluginId>{};
  return List.unmodifiable(
    value.map((item) {
      final row = _map(item, {'current', 'previous'});
      final current = _artifact(row['current']);
      final previous = row['previous'] == null
          ? null
          : _artifact(row['previous']);
      if (!ids.add(current.metadata.id) ||
          previous != null && previous.metadata.id != current.metadata.id) {
        throw const FormatException('Invalid attempted Novel identity');
      }
      return (current: current, previous: previous);
    }),
  );
}

Map<String, Object?> _metadata(NovelArtifactMetadata m) => {
  'id': m.id.upstreamId,
  'name': m.name,
  'version': m.version.value,
  'repository': m.repositoryId.toString(),
  'site': m.site.toString(),
  'origins': m.origins,
};
Map<String, Object?> _encode(NovelArtifact a) => {
  ..._metadata(a.metadata),
  'digest': a.digest,
};
Map<Object?, Object?> _map(Object? value, Set<String> keys) {
  if (value is! Map ||
      value.length != keys.length ||
      !value.keys.toSet().containsAll(keys)) {
    throw const FormatException('Invalid Novel native fields');
  }
  return value;
}

String _string(Object? value) {
  if (value is! String) {
    throw const FormatException('Invalid Novel native text');
  }
  return value;
}

NovelArtifact _artifact(Object? value) {
  final row = _map(value, {
    'id',
    'name',
    'version',
    'repository',
    'site',
    'origins',
    'digest',
  });
  final origins = row['origins'];
  if (origins is! List ||
      origins.length > 32 ||
      origins.any((v) => v is! String)) {
    throw const FormatException('Invalid Novel native origins');
  }
  return NovelArtifact(
    metadata: NovelArtifactMetadata(
      id: PluginId(PluginEcosystem.novel, _string(row['id'])),
      name: _string(row['name']),
      version: NovelPluginVersion(_string(row['version'])),
      repositoryId: RepositoryId(_string(row['repository'])),
      site: _string(row['site']),
      origins: origins.cast<String>(),
    ),
    digest: _string(row['digest']),
  );
}

List<NovelArtifactInventory> _inventory(Object? value) {
  if (value is! List || value.length > 500) {
    throw const FormatException('Invalid Novel inventory');
  }
  final ids = <PluginId>{};
  return List.unmodifiable(
    value.map((v) {
      final row = _map(v, {
        'current',
        'previous',
        'status',
        'previousStatus',
        'admission',
        'previousAdmission',
        'restartRequired',
      });
      if (row['restartRequired'] != true) {
        throw const FormatException('Novel artifact is not runtime admission');
      }
      final current = _artifact(row['current']);
      final previous = row['previous'] == null
          ? null
          : _artifact(row['previous']);
      if (!ids.add(current.metadata.id) ||
          (previous != null && previous.metadata.id != current.metadata.id) ||
          (previous == null) != (row['previousStatus'] == null) ||
          (previous == null) != (row['previousAdmission'] == null)) {
        throw const FormatException('Invalid Novel inventory identity');
      }
      NovelArtifactStatus status(Object? raw) => switch (raw) {
        'ready' => NovelArtifactStatus.ready,
        'missing' => NovelArtifactStatus.missing,
        'corrupt' => NovelArtifactStatus.corrupt,
        _ => throw const FormatException('Invalid Novel artifact status'),
      };
      NovelAdmission admission(Object? value) => switch (value) {
        'unknown' => NovelAdmission.unknown,
        'admitted' => NovelAdmission.admitted,
        _ => throw const FormatException('Invalid Novel admission evidence'),
      };
      return NovelArtifactInventory(
        current: current,
        admission: admission(row['admission']),
        previousAdmission: previous == null
            ? null
            : admission(row['previousAdmission']),
        previous: previous,
        status: status(row['status']),
        previousStatus: previous == null ? null : status(row['previousStatus']),
      );
    }),
  );
}

NovelRuntimeSnapshot _runtimeSnapshot(Object? value) {
  final row = _map(value, {'initialization', 'failure', 'loaded', 'issues'});
  final initialization = switch (row['initialization']) {
    'notInitialized' => NovelRuntimeInitialization.notInitialized,
    'ready' => NovelRuntimeInitialization.ready,
    'failed' => NovelRuntimeInitialization.failed,
    _ => throw const FormatException('Invalid Novel runtime initialization'),
  };
  final failure = row['failure'];
  if ((initialization == NovelRuntimeInitialization.failed) !=
          (failure != null) ||
      failure != null && !{'collision', 'snapshot-failed'}.contains(failure)) {
    throw const FormatException('Invalid Novel runtime failure');
  }
  final loaded = row['loaded'], issues = row['issues'];
  if (loaded is! List ||
      loaded.length > 1000 ||
      issues is! List ||
      issues.length > 500 ||
      initialization != NovelRuntimeInitialization.ready && loaded.isNotEmpty ||
      initialization == NovelRuntimeInitialization.notInitialized &&
          issues.isNotEmpty) {
    throw const FormatException('Invalid Novel runtime evidence');
  }
  final ids = <String>{};
  final revisions = loaded.map((value) {
    final item = _map(value, {'id', 'name', 'site', 'artifact'});
    final id = _string(item['id']);
    final name = pluginText(_string(item['name']), maxLength: 256);
    final site = pluginHttpsUrl(_string(item['site']));
    final artifact = item['artifact'] == null
        ? null
        : _artifact(item['artifact']);
    Uri root(Uri value) =>
        value.path.isEmpty ? value.replace(path: '/') : value;
    if (!RegExp(r'^[A-Za-z0-9_-]{1,128}$').hasMatch(id) ||
        !ids.add(id) ||
        artifact != null &&
            (artifact.metadata.id.upstreamId != id ||
                artifact.metadata.name != name ||
                root(artifact.metadata.site) != root(site))) {
      throw const FormatException('Invalid Novel loaded identity');
    }
    return LoadedNovelRevision(
      id: id,
      name: name,
      site: site,
      artifact: artifact,
    );
  }).toList();
  final issueIds = <PluginId>{};
  final errors = issues.map((value) {
    final item = _map(value, {'artifact', 'reason'});
    final artifact = _artifact(item['artifact']);
    final reason = _string(item['reason']);
    if (!{'missing', 'corrupt', 'admission-unknown'}.contains(reason) ||
        !issueIds.add(artifact.metadata.id)) {
      throw const FormatException('Invalid Novel runtime issue');
    }
    return NovelRuntimeIssue(artifact, reason);
  }).toList();
  return NovelRuntimeSnapshot(
    initialization: initialization,
    failure: failure as String?,
    loaded: revisions,
    issues: errors,
  );
}
