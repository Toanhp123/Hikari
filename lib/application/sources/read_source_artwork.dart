import 'dart:async';
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
  static const cacheNamespace = 'source-artwork-v1';

  ReadSourceArtwork(this._sources, {this._cache});

  final SourceRegistry _sources;
  final ByteCache? _cache;
  final Map<SourceMediaRef, _ArtworkFlight> _inFlight = {};

  Future<Uint8List?> execute(SourceMediaRef artwork) {
    final current = _inFlight[artwork];
    if (current != null) return current.future;
    return _start(artwork, reload: false);
  }

  /// Bypasses cached artwork without discarding it if the source fails.
  /// A reload waits for an older read so the old request cannot overwrite it.
  Future<Uint8List?> reload(SourceMediaRef artwork) {
    final current = _inFlight[artwork];
    if (current?.reload == true) return current!.future;
    return _start(artwork, reload: true, predecessor: current?.future);
  }

  Future<Uint8List?> _start(
    SourceMediaRef artwork, {
    required bool reload,
    Future<Uint8List?>? predecessor,
  }) {
    final flight = _ArtworkFlight(reload: reload);
    _inFlight[artwork] = flight;
    flight.future = _load(artwork, reload: reload, predecessor: predecessor)
        .whenComplete(() {
          if (identical(_inFlight[artwork], flight)) _inFlight.remove(artwork);
        });
    return flight.future;
  }

  Future<Uint8List?> _load(
    SourceMediaRef artwork, {
    required bool reload,
    Future<Uint8List?>? predecessor,
  }) async {
    if (predecessor != null) {
      try {
        await predecessor;
      } catch (_) {
        // Explicit reload proceeds even if the earlier request failed.
      }
    }
    final cache = _cache;
    if (!reload && cache != null) {
      try {
        final cached = await cache.read(cacheNamespace, cacheKey(artwork));
        if (cached != null && cached.isNotEmpty) return cached;
      } catch (_) {
        // Cache failures never prevent source reads.
      }
    }

    // An absent/disabled extension can still use cached artwork on normal reads.
    final source = _sources.find(artwork.sourceId);
    if (source is! ArtworkSource) return null;
    final bytes = await source.readArtwork(artwork);
    if (bytes.isEmpty || cache == null) return bytes.isEmpty ? null : bytes;
    try {
      await cache.write(cacheNamespace, cacheKey(artwork), bytes);
    } catch (_) {
      // Cache failures never hide successfully loaded source bytes.
    }
    return bytes;
  }

  static String cacheKey(SourceMediaRef artwork) =>
      jsonEncode([artwork.sourceId.value, artwork.itemId]);
}

final class _ArtworkFlight {
  _ArtworkFlight({required this.reload});

  final bool reload;
  late final Future<Uint8List?> future;
}
