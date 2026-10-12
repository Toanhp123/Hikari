import 'dart:convert';
import 'dart:typed_data';

import 'package:hikari/core/cache/byte_cache.dart';

/// Versioned, reconstructible AniList query snapshots. No user-owned state.
/// The caller validates the decoded GraphQL shape before using or writing it.
final class AniListMetadataCache {
  const AniListMetadataCache(this._cache, this._clock);

  static const namespace = 'anilist-metadata';
  static const _version = 1;

  final ByteCache _cache;
  final DateTime Function() _clock;

  Future<AniListCachedResponse?> read(String key, Duration lifetime) async {
    try {
      final bytes = await _cache.read(namespace, key);
      if (bytes == null) return null;
      final object = jsonDecode(utf8.decode(bytes));
      if (object is! Map<String, dynamic> || object['version'] != _version) {
        return null;
      }
      final timestamp = object['storedAt'];
      final data = object['response'];
      if (timestamp is! int || data is! Map<String, dynamic>) return null;
      final age = _clock().toUtc().difference(
        DateTime.fromMillisecondsSinceEpoch(timestamp, isUtc: true),
      );
      // A clock rollback must not make stale data fresh indefinitely.
      return AniListCachedResponse(
        data,
        isFresh: !age.isNegative && age < lifetime,
      );
    } catch (_) {
      // A missing, damaged or unavailable cache is always a cache miss.
      return null;
    }
  }

  Future<void> write(String key, Map<String, dynamic> response) async {
    try {
      final bytes = Uint8List.fromList(
        utf8.encode(
          jsonEncode({
            'version': _version,
            'storedAt': _clock().toUtc().millisecondsSinceEpoch,
            'response': response,
          }),
        ),
      );
      await _cache.write(namespace, key, bytes);
    } catch (_) {
      // Metadata persistence must not turn a valid network result into failure.
    }
  }
}

final class AniListCachedResponse {
  const AniListCachedResponse(this.response, {required this.isFresh});

  final Map<String, dynamic> response;
  final bool isFresh;
}
