/// Package ecosystem, not media type or source identity.
enum PluginEcosystem { manga, anime, novel }

enum PluginOperation { install, update, uninstall }

enum PluginOperationStatus { pending, interrupted }

enum PluginCompatibility {
  compatible,
  unsupportedPlatform,
  unsupportedAbi,
  unsupportedModule,
  unsupportedOrigin,
  unknown,
}

enum PluginApprovalKind { signer, codeDigest }

/// Syntactic URL policy only. Transport must also validate DNS and every redirect.
Uri pluginHttpsUrl(String value) {
  if (value.isEmpty ||
      value.length > 4096 ||
      value.trim() != value ||
      RegExp(r'[\x00-\x20\x7f\\]').hasMatch(value)) {
    throw const FormatException('Invalid plugin URL');
  }
  // Validate only the raw URL *path* before Uri.parse normalizes dot segments.
  // Signed CDN query parameters legitimately contain %2F, %25 and nested
  // escaping; they must be preserved, not interpreted as path traversal.
  final authorityStart = value.indexOf('://');
  if (authorityStart < 0) throw const FormatException('Invalid plugin URL');
  final pathStart = value.indexOf('/', authorityStart + 3);
  final queryStart = value.indexOf('?');
  final fragmentStart = value.indexOf('#');
  final stops = [queryStart, fragmentStart].where((index) => index >= 0);
  final pathEnd = stops.isEmpty
      ? value.length
      : stops.reduce((a, b) => a < b ? a : b);
  var path = pathStart >= 0 && pathStart < pathEnd
      ? value.substring(pathStart, pathEnd)
      : '';
  for (var i = 0; i < 4; i++) {
    if (RegExp(r'(^|/)\.{1,2}(/|$)').hasMatch(path) ||
        RegExp(r'%2f|%5c', caseSensitive: false).hasMatch(path)) {
      throw const FormatException('Unsafe plugin URL path');
    }
    final next = path.replaceAllMapped(RegExp(r'%[0-9a-fA-F]{2}'), (match) {
      return String.fromCharCode(int.parse(match[0]!.substring(1), radix: 16));
    });
    if (next == path) break;
    path = next;
    if (i == 3) throw const FormatException('Nested URL path encoding');
  }
  if (RegExp(r'%(?![0-9a-fA-F]{2})').hasMatch(path)) {
    throw const FormatException('Invalid URL path encoding');
  }
  final uri = Uri.parse(value);
  var host = uri.host.toLowerCase();
  if (host.endsWith('.')) host = host.substring(0, host.length - 1);
  if (uri.scheme.toLowerCase() != 'https' ||
      host.isEmpty ||
      uri.userInfo.isNotEmpty ||
      uri.hasFragment ||
      uri.port != 443 ||
      host == 'localhost' ||
      host.endsWith('.localhost') ||
      host.endsWith('.local') ||
      !host.contains('.') ||
      host.contains(':')) {
    throw const FormatException(
      'Plugin URL requires public credential-free HTTPS',
    );
  }
  final parts = host.split('.');
  if (parts.every((part) => RegExp(r'^\d+$').hasMatch(part))) {
    final ip = parts.map(int.parse).toList();
    if (ip.length != 4 ||
        ip.any((v) => v > 255) ||
        parts.any((v) => v.length > 1 && v.startsWith('0')) ||
        ip[0] == 0 ||
        ip[0] == 10 ||
        ip[0] == 127 ||
        ip[0] >= 224 ||
        (ip[0] == 100 && ip[1] >= 64 && ip[1] <= 127) ||
        (ip[0] == 169 && ip[1] == 254) ||
        (ip[0] == 172 && ip[1] >= 16 && ip[1] <= 31) ||
        (ip[0] == 192 && (ip[1] == 168 || ip[1] == 0)) ||
        (ip[0] == 198 && (ip[1] == 18 || ip[1] == 19))) {
      throw const FormatException('Non-public literal IP');
    }
  } else if (parts.any(
        (p) => !RegExp(r'^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$').hasMatch(p),
      ) ||
      RegExp(r'^(0x[0-9a-f]+|[0-9]+)$').hasMatch(parts.last)) {
    throw const FormatException('Invalid public hostname');
  }
  String normalizeEscapes(String input) =>
      input.replaceAllMapped(RegExp(r'%[0-9a-fA-F]{2}'), (m) {
        final c = String.fromCharCode(int.parse(m[0]!.substring(1), radix: 16));
        return RegExp(r'[a-zA-Z0-9._~-]').hasMatch(c) ? c : m[0]!.toUpperCase();
      });
  final normalized = uri
      .replace(scheme: 'https', host: host, port: 443)
      .toString();
  return Uri.parse(normalizeEscapes(normalized));
}

String pluginText(String value, {int maxLength = 4096}) {
  if (value.trim().isEmpty ||
      value.length > maxLength ||
      value.contains('\u0000')) {
    throw const FormatException('Invalid plugin field');
  }
  return value;
}

String pluginDigest(String value) {
  if (!RegExp(r'^[a-fA-F0-9]{64}$').hasMatch(value)) {
    throw const FormatException('Expected SHA-256 digest');
  }
  return value.toLowerCase();
}

final class RepositoryId {
  RepositoryId(String url) : url = pluginHttpsUrl(url);
  final Uri url;
  @override
  bool operator ==(Object other) => other is RepositoryId && url == other.url;
  @override
  int get hashCode => url.hashCode;
  @override
  String toString() => url.toString();
}

final class PluginId {
  PluginId(this.ecosystem, String upstreamId)
    : upstreamId = pluginText(upstreamId, maxLength: 256);
  final PluginEcosystem ecosystem;
  final String upstreamId;
  @override
  bool operator ==(Object other) =>
      other is PluginId &&
      ecosystem == other.ecosystem &&
      upstreamId == other.upstreamId;
  @override
  int get hashCode => Object.hash(ecosystem, upstreamId);
}

sealed class PluginVersion implements Comparable<PluginVersion> {}

final class ApkPluginVersion extends PluginVersion {
  ApkPluginVersion(this.code, String name)
    : name = pluginText(name, maxLength: 128) {
    if (code < 0) throw const FormatException('Invalid APK version code');
  }
  final int code;
  final String name;
  @override
  int compareTo(PluginVersion other) {
    if (other is! ApkPluginVersion) throw ArgumentError('Different ecosystems');
    return code.compareTo(other.code);
  }
}

final class NovelPluginVersion extends PluginVersion {
  NovelPluginVersion(String value) : value = pluginText(value, maxLength: 128) {
    if (!RegExp(
      r'^\d+(?:\.\d+)*(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$',
    ).hasMatch(value)) {
      throw const FormatException('Invalid numeric novel version');
    }
  }
  final String value;
  @override
  int compareTo(PluginVersion other) {
    if (other is! NovelPluginVersion) {
      throw ArgumentError('Different ecosystems');
    }
    final a = value.split('+').first.split('-');
    final b = other.value.split('+').first.split('-');
    final ac = a.first.split('.').map(BigInt.parse).toList();
    final bc = b.first.split('.').map(BigInt.parse).toList();
    for (var i = 0; i < ac.length || i < bc.length; i++) {
      final result = (i < ac.length ? ac[i] : BigInt.zero).compareTo(
        i < bc.length ? bc[i] : BigInt.zero,
      );
      if (result != 0) return result;
    }
    if (a.length == 1 || b.length == 1) {
      return (a.length == 1 ? 1 : 0).compareTo(b.length == 1 ? 1 : 0);
    }
    final ap = a.skip(1).join('-').split('.');
    final bp = b.skip(1).join('-').split('.');
    for (var i = 0; i < ap.length && i < bp.length; i++) {
      final ai = BigInt.tryParse(ap[i]);
      final bi = BigInt.tryParse(bp[i]);
      final result = ai != null && bi != null
          ? ai.compareTo(bi)
          : ai != null
          ? -1
          : bi != null
          ? 1
          : ap[i].compareTo(bp[i]);
      if (result != 0) return result.sign;
    }
    return ap.length.compareTo(bp.length);
  }
}

final class PluginSourceDescriptor {
  PluginSourceDescriptor({
    required String upstreamId,
    required String name,
    required String language,
  }) : upstreamId = pluginText(upstreamId, maxLength: 256),
       name = pluginText(name),
       language = pluginText(language, maxLength: 128);
  final String upstreamId;
  final String name;
  final String language;
}

final class PluginOffer {
  PluginOffer({
    required this.repositoryId,
    required this.id,
    required String name,
    required this.version,
    required String downloadUrl,
    required String iconUrl,
    required Iterable<PluginSourceDescriptor> sources,
    this.extensionLib,
    this.isTorrent = false,
    String? siteUrl,
    this.siteLabel,
    String? customCssUrl,
    String? customJsUrl,
    this.compatibility = PluginCompatibility.unknown,
  }) : name = pluginText(name),
       downloadUrl = pluginHttpsUrl(downloadUrl),
       iconUrl = pluginHttpsUrl(iconUrl),
       siteUrl = siteUrl == null ? null : pluginHttpsUrl(siteUrl),
       customCssUrl = customCssUrl == null
           ? null
           : pluginHttpsUrl(customCssUrl),
       customJsUrl = customJsUrl == null ? null : pluginHttpsUrl(customJsUrl),
       sources = List.unmodifiable(sources) {
    if ((id.ecosystem == PluginEcosystem.novel) !=
            (version is NovelPluginVersion) ||
        this.sources.length > 256 ||
        this.sources.map((s) => s.upstreamId).toSet().length !=
            this.sources.length) {
      throw const FormatException('Invalid plugin offer');
    }
    if (extensionLib != null) pluginText(extensionLib!, maxLength: 128);
  }
  final RepositoryId repositoryId;
  final PluginId id;
  final String name;
  final PluginVersion version;
  final Uri downloadUrl;
  final Uri iconUrl;
  final List<PluginSourceDescriptor> sources;
  final String? extensionLib;
  final bool isTorrent;
  final Uri? siteUrl;
  final String? siteLabel;
  final Uri? customCssUrl;
  final Uri? customJsUrl;
  final PluginCompatibility compatibility;
}

final class PluginRepositorySnapshot {
  PluginRepositorySnapshot({
    required String name,
    required Iterable<PluginOffer> offers,
    String? signerFingerprint,
  }) : name = pluginText(name),
       offers = List.unmodifiable(offers),
       signerFingerprint = signerFingerprint == null
           ? null
           : pluginDigest(signerFingerprint) {
    if (this.offers.length > 5000 ||
        this.offers.map((o) => o.id).toSet().length != this.offers.length) {
      throw const FormatException('Duplicate or excessive plugin offers');
    }
  }
  final String name;
  final List<PluginOffer> offers;
  final String? signerFingerprint;
}

final class PluginRepository {
  PluginRepository(
    this.id,
    this.ecosystem, {
    this.snapshot,
    this.refreshError,
  }) {
    if (snapshot != null &&
        snapshot!.offers.any(
          (o) => o.repositoryId != id || o.id.ecosystem != ecosystem,
        )) {
      throw const FormatException('Foreign repository offer');
    }
    if (refreshError != null) pluginText(refreshError!);
  }
  final RepositoryId id;
  final PluginEcosystem ecosystem;
  final PluginRepositorySnapshot? snapshot;
  final String? refreshError;
}

/// APK digest represents exactly one verified current signer. Multi-signer APKs
/// must be rejected by inventory/admission; this is not an any-signer allowlist.
/// Novel digest identifies exact immutable executable bundle bytes.
final class InstalledPlugin {
  InstalledPlugin({
    required this.id,
    required this.version,
    required String digest,
    required this.compatibility,
  }) : digest = pluginDigest(digest) {
    if ((id.ecosystem == PluginEcosystem.novel) !=
        (version is NovelPluginVersion)) {
      throw const FormatException('Wrong installed version');
    }
  }
  final PluginId id;
  final PluginVersion version;
  final String digest;
  final PluginCompatibility compatibility;
}

/// Explicit user consent. Repository metadata never creates this record.
final class PluginApproval {
  PluginApproval({
    required this.id,
    required this.repositoryId,
    required this.kind,
    required String digest,
    Iterable<String> origins = const [],
  }) : digest = pluginDigest(digest),
       origins = Set.unmodifiable(
         origins.map((value) {
           final url = pluginHttpsUrl(value);
           if ((url.path.isNotEmpty && url.path != '/') || url.hasQuery) {
             throw const FormatException('Expected HTTPS origin');
           }
           return url.origin;
         }),
       ) {
    if ((id.ecosystem == PluginEcosystem.novel) !=
            (kind == PluginApprovalKind.codeDigest) ||
        (kind == PluginApprovalKind.codeDigest && this.origins.isEmpty) ||
        (kind == PluginApprovalKind.signer && this.origins.isNotEmpty) ||
        this.origins.length > 32) {
      throw const FormatException('Invalid plugin approval');
    }
  }
  final PluginId id;
  final RepositoryId repositoryId;
  final PluginApprovalKind kind;
  final String digest;
  final Set<String> origins;
}

final class PendingPluginOperation {
  const PendingPluginOperation(this.id, this.operation, this.status);
  final PluginId id;
  final PluginOperation operation;
  final PluginOperationStatus status;
}

final class PluginState {
  PluginState({
    Iterable<PluginRepository> repositories = const [],
    Iterable<InstalledPlugin> installed = const [],
    Map<PluginId, RepositoryId> owners = const {},
    Iterable<PluginApproval> approvals = const [],
    Iterable<PendingPluginOperation> operations = const [],
  }) : repositories = List.unmodifiable(repositories),
       installed = List.unmodifiable(installed),
       owners = Map.unmodifiable(owners),
       approvals = List.unmodifiable(approvals),
       operations = List.unmodifiable(operations) {
    for (final ids in [
      this.repositories.map((v) => v.id),
      this.installed.map((v) => v.id),
      this.approvals.map((v) => v.id),
      this.operations.map((v) => v.id),
    ]) {
      if (ids.length > 5000 || ids.toSet().length != ids.length) {
        throw const FormatException('Duplicate or excessive plugin state');
      }
    }
    if (this.owners.length > 5000) {
      throw const FormatException('Excessive owners');
    }
  }
  final List<PluginRepository> repositories;
  final List<InstalledPlugin> installed;
  final Map<PluginId, RepositoryId> owners;
  final List<PluginApproval> approvals;
  final List<PendingPluginOperation> operations;
}

abstract interface class PluginStateStore {
  Future<PluginState> load();
  Future<void> save(PluginState state);
}

abstract interface class PluginRepositoryReader {
  Future<PluginRepositorySnapshot> read(PluginRepository repository);
}

/// Installed inventory is independent of repository availability and source activation.
abstract interface class PluginInventory {
  Future<List<InstalledPlugin>> readInstalled();
}
