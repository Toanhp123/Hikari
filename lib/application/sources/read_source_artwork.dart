import 'dart:convert';
import 'dart:typed_data';

import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';

/// Resolves source-owned artwork without exposing source implementations to UI.
///
/// Any registered source can participate by implementing [ArtworkSource]. This
/// keeps search and picker UI agnostic to the extension runtime (Mihon,
/// LNReader, or future anime providers).
final class ReadSourceArtwork {
  ReadSourceArtwork(this._sources, {this._cache});

  final SourceRegistry _sources;
  final ByteCache? _cache;
  final Map<SourceMediaRef, Future<Uint8List?>> _inFlight = {};

  Future<Uint8List?> execute(SourceMediaRef artwork) {
    final source = _sources.find(artwork.sourceId);
    if (source is! ArtworkSource) return Future.value(null);

    final pending = _inFlight[artwork];
    if (pending != null) return pending;
    final load = _load(source, artwork);
    final shared = load.whenComplete(() {
      _inFlight.remove(artwork);
    });
    _inFlight[artwork] = shared;
    return shared;
  }

  Future<Uint8List?> _load(ArtworkSource source, SourceMediaRef artwork) async {
    final cache = _cache;
    if (cache != null) {
      try {
        final cached = await cache.read('source-artwork-v1', cacheKey(artwork));
        if (cached != null && cached.isNotEmpty) return cached;
      } catch (_) {
        // Cache failures never prevent source reads.
      }
    }

    final bytes = await source.readArtwork(artwork);
    if (bytes.isEmpty || cache == null) return bytes.isEmpty ? null : bytes;
    try {
      await cache.write('source-artwork-v1', cacheKey(artwork), bytes);
    } catch (_) {
      // Cache failures never hide successfully loaded source bytes.
    }
    return bytes;
  }

  static String cacheKey(SourceMediaRef artwork) =>
      jsonEncode([artwork.sourceId.value, artwork.itemId]);
}
