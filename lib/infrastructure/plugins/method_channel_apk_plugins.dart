import 'package:flutter/services.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';

final class MethodChannelApkPlugins implements ApkPluginGateway {
  static const _channel = MethodChannel('hikari/plugins');
  static int _sequence = 0;
  String _requestId() =>
      'dart-${DateTime.now().microsecondsSinceEpoch}-${_sequence++}';
  @override
  Future<List<ApkPluginIdentity>> inventory() async {
    final raw = await _channel.invokeMethod<Object?>('inventory');
    if (raw is! List || raw.length > 5000) {
      throw const FormatException('Invalid APK inventory');
    }
    return List.unmodifiable(raw.map(_identity));
  }

  @override
  Future<Uint8List> fetchMetadata(Uri url) async {
    final bytes = await _channel.invokeMethod<Object?>('metadata', {
      'url': pluginHttpsUrl(url.toString()).toString(),
      'requestId': _requestId(),
    });
    if (bytes is! Uint8List || bytes.length > 8 * 1024 * 1024) {
      throw const FormatException('Invalid native metadata bytes');
    }
    return bytes;
  }

  @override
  Future<StagedApk> stage(ApkStageRequest request, {String? requestId}) async {
    final offer = request.offer;
    final raw = _map(
      await _channel.invokeMethod<Object?>('stage', {
        'url': offer.downloadUrl.toString(),
        'family': offer.id.ecosystem.name,
        'packageName': offer.id.upstreamId,
        'versionCode': (offer.version as ApkPluginVersion).code,
        'expectedSigner': request.expectedSigner,
        'requestId': requestId ?? _requestId(),
      }),
    );
    return StagedApk(
      token: _string(raw['token']),
      identity: _identity(raw['identity']),
    );
  }

  @override
  Future<ApkPluginIdentity> approveStaged(String token) async => _identity(
    await _channel.invokeMethod<Object?>('approve', {'token': token}),
  );
  @override
  Future<ApkPluginIdentity> approveInstalled(ApkPluginIdentity identity) async {
    if (identity.currentSigners.length != 1) {
      throw StateError('Unsupported signer set');
    }
    return _identity(
      await _channel.invokeMethod<Object?>('approveInstalled', {
        'family': identity.id.ecosystem.name,
        'packageName': identity.id.upstreamId,
        'versionCode': identity.version.code,
        'expectedSigner': identity.currentSigners.single,
      }),
    );
  }

  @override
  Future<void> discardStaged(String token) =>
      _channel.invokeMethod<void>('discard', {'token': token});

  @override
  Future<bool> canInstall() async {
    final value = await _channel.invokeMethod<Object?>('canInstall');
    if (value is! bool) {
      throw const FormatException('Invalid install permission response');
    }
    return value;
  }

  @override
  Future<void> openInstallSettings() =>
      _channel.invokeMethod<void>('openInstallSettings');

  @override
  Future<ApkInstallOperation> installStaged(
    String token,
    PluginOperation kind,
  ) async {
    if (kind == PluginOperation.uninstall) {
      throw ArgumentError('Uninstall is not supported here');
    }
    return _operation(
      await _channel.invokeMethod<Object?>('install', {
        'token': token,
        'kind': kind.name,
      }),
    );
  }

  @override
  Future<ApkInstallOperation> uninstall(ApkPluginIdentity identity) async {
    if (identity.compatibility != ApkCompatibility.compatible ||
        identity.currentSigners.length != 1) {
      throw StateError('Unsupported uninstall identity');
    }
    return _operation(
      await _channel.invokeMethod<Object?>('uninstall', {
        'family': identity.id.ecosystem.name,
        'packageName': identity.id.upstreamId,
        'versionCode': identity.version.code,
        'expectedSigner': identity.currentSigners.single,
      }),
    );
  }

  @override
  Future<List<ApkInstallOperation>> installOperations() async {
    final value = await _channel.invokeMethod<Object?>('installOperations');
    if (value is! List || value.length > 100) {
      throw const FormatException('Invalid operation history');
    }
    final result = value.map(_operation).toList(growable: false);
    if (result.map((op) => op.id).toSet().length != result.length) {
      throw const FormatException('Duplicate operation ID');
    }
    return List.unmodifiable(result);
  }

  @override
  Future<ApkInstallOperation> resumeInstallConfirmation(
    String operationId,
  ) async => _operation(
    await _channel.invokeMethod<Object?>('resumeInstallConfirmation', {
      'operationId': operationId,
    }),
  );

  @override
  Future<ApkInstallOperation> cancelInstall(String operationId) async =>
      _operation(
        await _channel.invokeMethod<Object?>('cancelInstall', {
          'operationId': operationId,
        }),
      );

  @override
  Future<ApkInstallOperation> retireInstall(
    String operationId, {
    bool abandonOwnedSession = false,
    bool acknowledgeUnrevokedRemoval = false,
  }) async => _operation(
    await _channel.invokeMethod<Object?>('retireInstall', {
      'operationId': operationId,
      'abandonOwnedSession': abandonOwnedSession,
      'acknowledgeUnrevokedRemoval': acknowledgeUnrevokedRemoval,
    }),
  );

  @override
  Future<void> cancel(String requestId) =>
      _channel.invokeMethod<void>('cancel', {'requestId': requestId});
}

ApkInstallOperation _operation(Object? value) {
  final row = _map(value);
  final version = row['versionCode'];
  final session = row['sessionId'];
  final transferred = row['stageTransferred'];
  final reason = row['reason'];
  if (version is! int ||
      (session != null && session is! int) ||
      transferred is! bool ||
      (reason != null && reason is! String)) {
    throw const FormatException('Invalid operation types');
  }
  final status = switch (row['status']) {
    'preparing' => ApkInstallStatus.preparing,
    'awaiting_result' => ApkInstallStatus.awaitingResult,
    'pending_user_action' => ApkInstallStatus.pendingUserAction,
    'interrupted' => ApkInstallStatus.interrupted,
    'succeeded' => ApkInstallStatus.succeeded,
    'failed' => ApkInstallStatus.failed,
    'cancelled' => ApkInstallStatus.cancelled,
    'externally_installed' => ApkInstallStatus.externallyInstalled,
    'retired_unknown' => ApkInstallStatus.retiredUnknown,
    'absent_before_request' => ApkInstallStatus.absentBeforeRequest,
    'externally_removed' => ApkInstallStatus.externallyRemoved,
    _ => throw const FormatException('Unknown install status'),
  };
  return ApkInstallOperation(
    id: _string(row['id']),
    pluginId: PluginId(
      _enum(PluginEcosystem.values, row['family']),
      _string(row['packageName']),
    ),
    versionCode: version,
    signer: _string(row['signer']),
    kind: _enum(PluginOperation.values, row['kind']),
    status: status,
    stageTransferred: transferred,
    sessionId: session as int?,
    reason: reason as String?,
  );
}

Map<Object?, Object?> _map(Object? value) {
  if (value is! Map || value.length > 32) {
    throw const FormatException('Invalid APK response');
  }
  return value;
}

String _string(Object? value) {
  if (value is! String || value.length > 4096) {
    throw const FormatException('Invalid APK string');
  }
  return value;
}

T _enum<T extends Enum>(List<T> values, Object? value) {
  for (final entry in values) {
    if (entry.name == value) return entry;
  }
  throw const FormatException('Unknown APK enum');
}

List<String> _strings(Object? value) {
  if (value is! List || value.length > 32) {
    throw const FormatException('Invalid APK signer list');
  }
  return value.map(_string).toList();
}

ApkPluginIdentity _identity(Object? value) {
  final row = _map(value);
  final code = row['versionCode'];
  final trusted = row['trusted'];
  if (code is! int || trusted is! bool) {
    throw const FormatException('Invalid APK identity types');
  }
  return ApkPluginIdentity(
    id: PluginId(
      _enum(PluginEcosystem.values, row['family']),
      _string(row['packageName']),
    ),
    version: ApkPluginVersion(code, _string(row['versionName'])),
    library: _string(row['library']),
    entrypoint: _string(row['entrypoint']),
    currentSigners: _strings(row['signers']),
    signerHistory: _strings(row['history']),
    compatibility: _enum(ApkCompatibility.values, row['compatibility']),
    trust: trusted ? ApkTrust.approved : ApkTrust.unapproved,
  );
}
