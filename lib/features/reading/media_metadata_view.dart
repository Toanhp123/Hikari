import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/reading.dart';

class MediaMetadataView extends StatelessWidget {
  const MediaMetadataView({
    super.key,
    required this.metadata,
    required this.sourceName,
    this.readArtwork,
  });
  final MediaMetadata metadata;
  final String sourceName;
  final Future<Uint8List> Function(SourceMediaRef)? readArtwork;
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
          Text('Status: ${metadata.rawStatus ?? metadata.status.name}'),
        if (metadata.rating != null)
          Text(
            'Rating: ${metadata.rating}${metadata.ratingMax == null ? '' : ' / ${metadata.ratingMax}'}',
          ),
      ],
    ),
  );
}

class SourceArtwork extends StatefulWidget {
  const SourceArtwork({super.key, required this.resource, required this.read});
  final SourceMediaRef resource;
  final Future<Uint8List> Function(SourceMediaRef) read;
  @override
  State<SourceArtwork> createState() => _SourceArtworkState();
}

class _SourceArtworkState extends State<SourceArtwork> {
  late Future<Uint8List> _bytes = widget.read(widget.resource);
  @override
  void didUpdateWidget(SourceArtwork oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.resource != widget.resource) {
      _bytes = widget.read(widget.resource);
    }
  }

  @override
  Widget build(BuildContext context) => SizedBox(
    width: 120,
    height: 160,
    child: FutureBuilder<Uint8List>(
      future: _bytes,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return const Center(
            child: Icon(Icons.broken_image, semanticLabel: 'Cover unavailable'),
          );
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        return Image.memory(
          snapshot.data!,
          fit: BoxFit.contain,
          semanticLabel: 'Cover',
          errorBuilder: (_, _, _) => const Icon(
            Icons.broken_image,
            semanticLabel: 'Cover unavailable',
          ),
        );
      },
    ),
  );
}
