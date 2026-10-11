import 'package:hikari/domain/media/media.dart';

/// The selected folder can no longer be read with the granted access.
final class LocalMediaAccessException implements Exception {
  const LocalMediaAccessException();
}

/// A completed scan of one SAF tree. Opaque document identifiers stay in
/// [SourceMediaRef]; presentation only receives the human-readable root name.
final class LocalMediaScanResult {
  LocalMediaScanResult({
    required this.rootName,
    required List<Media> media,
    Map<SourceMediaRef, SourceMediaRef> artwork = const {},
  }) : media = List.unmodifiable(media),
       artwork = Map.unmodifiable(artwork);

  final String rootName;
  final List<Media> media;

  /// Optional image-document references, keyed by the scanned media identity.
  /// Archive files are intentionally not materialized just for grid artwork.
  final Map<SourceMediaRef, SourceMediaRef> artwork;
}
