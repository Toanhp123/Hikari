import 'dart:typed_data';

import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';

/// Resolves source-owned artwork without exposing source implementations to UI.
///
/// Any registered source can participate by implementing [ArtworkSource]. This
/// keeps search and picker UI agnostic to the extension runtime (Mihon,
/// LNReader, or future anime providers).
final class ReadSourceArtwork {
  const ReadSourceArtwork(this._sources);

  final SourceRegistry _sources;

  Future<Uint8List?> execute(SourceMediaRef artwork) async {
    final source = _sources.find(artwork.sourceId);
    if (source is! ArtworkSource) return null;

    final bytes = await source.readArtwork(artwork);
    return bytes.isEmpty ? null : bytes;
  }
}
