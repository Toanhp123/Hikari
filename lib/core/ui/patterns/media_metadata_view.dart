import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';

class MediaMetadataView extends StatelessWidget {
  const MediaMetadataView({
    super.key,
    required this.metadata,
    required this.sourceName,
    this.readArtwork,
  });
  final MediaMetadata metadata;
  final String sourceName;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.all(16),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (metadata.cover != null && readArtwork != null)
          SourceArtwork(
            key: ValueKey(metadata.cover),
            resource: metadata.cover!,
            read: readArtwork!,
          ),
        Text(sourceName, style: Theme.of(context).textTheme.labelLarge),
        if (metadata.authors.isNotEmpty)
          Text('Authors: ${metadata.authors.join(', ')}'),
        if (metadata.artists.isNotEmpty)
          Text('Artists: ${metadata.artists.join(', ')}'),
        if (metadata.summary != null) Text(metadata.summary!),
        if (metadata.genres.isNotEmpty)
          Text('Genres: ${metadata.genres.join(', ')}'),
        if (metadata.tags.isNotEmpty) Text('Tags: ${metadata.tags.join(', ')}'),
        if (metadata.language != null) Text('Language: ${metadata.language}'),
        if (metadata.publisher != null)
          Text('Publisher: ${metadata.publisher}'),
        if (metadata.rawStatus != null ||
            metadata.status != PublicationStatus.unknown)
          Text(
            'Status: ${metadata.rawStatus ?? _publicationStatusLabel(metadata.status)}',
          ),
        if (metadata.rating != null)
          Text(
            'Rating: ${metadata.rating}${metadata.ratingMax == null ? '' : ' / ${metadata.ratingMax}'}',
          ),
      ],
    ),
  );
}

/// Loads optional source-owned artwork without exposing provider details to UI.
///
/// The future is retained while [resource] is unchanged. Missing, empty, or
/// failed artwork resolves to null so presentation can fall back gracefully.
class SourceArtwork extends StatefulWidget {
  const SourceArtwork({
    super.key,
    required this.resource,
    required this.read,
    this.builder,
  });

  final SourceMediaRef resource;
  final Future<Uint8List?> Function(SourceMediaRef) read;
  final Widget Function(BuildContext, Uint8List?, bool)? builder;

  @override
  State<SourceArtwork> createState() => _SourceArtworkState();
}

class _SourceArtworkState extends State<SourceArtwork> {
  late Future<Uint8List?> _bytes = _read();

  Future<Uint8List?> _read() async {
    try {
      final bytes = await widget.read(widget.resource);
      return bytes == null || bytes.isEmpty ? null : bytes;
    } catch (_) {
      return null;
    }
  }

  @override
  void didUpdateWidget(SourceArtwork oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.resource != widget.resource) {
      _bytes = _read();
    }
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<Uint8List?>(
    future: _bytes,
    builder: (context, snapshot) {
      final loading = snapshot.connectionState != ConnectionState.done;
      final builder = widget.builder;
      if (builder != null) return builder(context, snapshot.data, loading);

      return SizedBox(
        width: 120,
        height: 160,
        child: _defaultArtwork(snapshot.data, loading),
      );
    },
  );

  Widget _defaultArtwork(Uint8List? bytes, bool loading) {
    if (loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (bytes == null) {
      return const Center(
        child: Icon(Icons.broken_image, semanticLabel: 'Cover unavailable'),
      );
    }
    return Image.memory(
      bytes,
      fit: BoxFit.contain,
      semanticLabel: 'Cover',
      errorBuilder: (_, _, _) =>
          const Icon(Icons.broken_image, semanticLabel: 'Cover unavailable'),
    );
  }
}

String _publicationStatusLabel(PublicationStatus status) => switch (status) {
  PublicationStatus.unknown => 'Unknown',
  PublicationStatus.ongoing => 'Ongoing',
  PublicationStatus.completed => 'Completed',
  PublicationStatus.licensed => 'Licensed',
  PublicationStatus.publishingFinished => 'Publishing finished',
  PublicationStatus.cancelled => 'Cancelled',
  PublicationStatus.onHiatus => 'On hiatus',
};
