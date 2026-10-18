import 'package:hikari/domain/plugins/plugins.dart';

/// Selected display metadata, not proof of JS compatibility or execution consent.
final class NovelArtifactMetadata {
  NovelArtifactMetadata({
    required this.id,
    required String name,
    required this.version,
    required this.repositoryId,
    required String site,
    required Iterable<String> origins,
  }) : name = pluginText(name, maxLength: 256),
       site = pluginHttpsUrl(site),
       origins = List.unmodifiable(origins.map(_origin).toList()..sort()) {
    if (id.ecosystem != PluginEcosystem.novel ||
        !RegExp(r'^[A-Za-z0-9_-]{1,128}$').hasMatch(id.upstreamId) ||
        this.origins.isEmpty ||
        this.origins.length > 32 ||
        this.origins.toSet().length != this.origins.length ||
        !this.origins.contains(this.site.origin)) {
      throw const FormatException('Invalid Novel artifact metadata');
    }
  }
  final PluginId id;
  final String name;
  final NovelPluginVersion version;
  final RepositoryId repositoryId;
  final Uri site;
  final List<String> origins;
}

String _origin(String value) {
  final uri = pluginHttpsUrl(value);
  if ((uri.path.isNotEmpty && uri.path != '/') || uri.hasQuery) {
    throw const FormatException('Expected exact Novel HTTPS origin');
  }
  return uri.origin;
}

/// Native frozen tuple. Generic PluginApproval is only a non-authoritative mirror.
final class NovelArtifact {
  NovelArtifact({required this.metadata, required String digest})
    : digest = pluginDigest(digest);
  final NovelArtifactMetadata metadata;
  final String digest;
  PluginApproval get approval => PluginApproval(
    id: metadata.id,
    repositoryId: metadata.repositoryId,
    kind: PluginApprovalKind.codeDigest,
    digest: digest,
    origins: metadata.origins,
  );
}

final class StagedNovelArtifact {
  StagedNovelArtifact({required String token, required this.artifact})
    : token = pluginText(token, maxLength: 128) {
    if (!RegExp(r'^[A-Za-z0-9_-]{1,128}$').hasMatch(token)) {
      throw const FormatException('Invalid Novel stage token');
    }
  }
  final String token;
  final NovelArtifact artifact;
}

/// Bounded export inspection only; never universal compatibility or activation.
enum NovelAdmission { unknown, admitted }

enum NovelArtifactStatus { ready, missing, corrupt }

final class NovelArtifactInventory {
  const NovelArtifactInventory({
    required this.current,
    required this.previous,
    required this.status,
    required this.previousStatus,
    this.admission = NovelAdmission.unknown,
    this.previousAdmission,
  });
  final NovelArtifact current;
  final NovelArtifact? previous;
  final NovelArtifactStatus status;
  final NovelArtifactStatus? previousStatus;
  bool get restartRequired => true;
  final NovelAdmission admission;
  final NovelAdmission? previousAdmission;
}

enum NovelRuntimeInitialization { notInitialized, ready, failed }

/// Captured runtime evidence, independent from current persisted pointers.
final class LoadedNovelRevision {
  const LoadedNovelRevision({
    required this.id,
    required this.name,
    required this.site,
    required this.artifact,
  });
  final String id;
  final String name;
  final Uri site;
  final NovelArtifact? artifact;
}

final class NovelRuntimeIssue {
  const NovelRuntimeIssue(this.artifact, this.reason);
  final NovelArtifact artifact;
  final String reason;
}

final class NovelRuntimeSnapshot {
  NovelRuntimeSnapshot({
    required this.initialization,
    required this.failure,
    required Iterable<LoadedNovelRevision> loaded,
    required Iterable<NovelRuntimeIssue> issues,
  }) : loaded = List.unmodifiable(loaded),
       issues = List.unmodifiable(issues);
  final NovelRuntimeInitialization initialization;
  final String? failure;
  final List<LoadedNovelRevision> loaded;
  final List<NovelRuntimeIssue> issues;

  List<LoadedNovelRevision> pendingRemoval(
    Iterable<NovelArtifactInventory> inventory,
  ) {
    final ids = inventory.map((row) => row.current.metadata.id).toSet();
    return loaded
        .where(
          (row) =>
              row.artifact != null && !ids.contains(row.artifact!.metadata.id),
        )
        .toList(growable: false);
  }

  bool loadedCurrent(NovelArtifact artifact) =>
      loaded.any((row) => sameNovelArtifact(artifact, row.artifact));
}

/// Sanitized native admission outcome, never raw JavaScript/platform exception text.
final class NovelAdmissionRejected implements Exception {
  const NovelAdmissionRejected(this.reason);
  final String reason;
}

enum NovelPublicationFailureKind { publicationUnknown, pointerSyncFailed }

typedef NovelAttemptedPointer = ({
  NovelArtifact current,
  NovelArtifact? previous,
});

/// Publication attempted: all mutations require native process restart, even if evidence is malformed.
final class NovelPublicationFailure implements Exception {
  NovelPublicationFailure({
    required this.kind,
    Iterable<NovelArtifactInventory>? inventory,
    Iterable<NovelAttemptedPointer>? attempted,
  }) : inventory = inventory == null ? null : List.unmodifiable(inventory),
       attempted = attempted == null ? null : List.unmodifiable(attempted);
  final NovelPublicationFailureKind kind;

  /// Last-confirmed inventory for unknown publication; committed inventory for sync failure.
  final List<NovelArtifactInventory>? inventory;
  final List<NovelAttemptedPointer>? attempted;
}

bool sameNovelArtifact(NovelArtifact? a, NovelArtifact? b) {
  if (a == null || b == null) return a == b;
  Uri root(Uri value) => value.path.isEmpty ? value.replace(path: '/') : value;
  final x = a.metadata, y = b.metadata;
  return a.digest == b.digest &&
      x.id == y.id &&
      x.name == y.name &&
      x.version.value == y.version.value &&
      root(x.site) == root(y.site) &&
      root(x.repositoryId.url) == root(y.repositoryId.url) &&
      x.origins.length == y.origins.length &&
      x.origins.every(y.origins.contains);
}

abstract interface class NovelPluginGateway {
  Future<StagedNovelArtifact> stage(
    PluginOffer offer,
    Iterable<String> origins, {
    String? requestId,
  });
  Future<List<NovelArtifactInventory>> inventory();
  Future<NovelRuntimeSnapshot> runtimeSnapshot();
  Future<List<NovelArtifactInventory>> admit(NovelArtifact displayed);
  Future<List<NovelArtifactInventory>> install(StagedNovelArtifact stage);
  Future<List<NovelArtifactInventory>> rollback(
    NovelArtifactInventory displayed,
  );
  Future<List<NovelArtifactInventory>> uninstall(NovelArtifact displayed);
  Future<void> discard(String token);
  Future<void> cancel(String requestId);
}
