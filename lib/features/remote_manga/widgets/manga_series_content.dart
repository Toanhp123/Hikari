import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/remote_manga/manga_series_view_model.dart';

class MangaSeriesContent extends StatelessWidget {
  const MangaSeriesContent({
    super.key,
    required this.state,
    required this.sourceName,
    required this.openingChapter,
    required this.onRetry,
    required this.onOpenChapter,
    this.readArtwork,
  });

  final MangaSeriesUiState state;
  final String sourceName;
  final bool openingChapter;
  final VoidCallback onRetry;
  final ValueChanged<MangaChapter> onOpenChapter;
  final Future<Uint8List> Function(SourceMediaRef)? readArtwork;

  @override
  Widget build(BuildContext context) {
    if (state is MangaSeriesLoading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (state is MangaSeriesFailure) {
      return Center(
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Could not load chapters. Check source access or rate limits.',
              ),
              TextButton(onPressed: onRetry, child: const Text('Try again')),
            ],
          ),
        ),
      );
    }

    final details = (state as MangaSeriesReady).details;
    final chapters = details.chapters;
    return ListView.builder(
      itemCount: chapters.length + 1,
      itemBuilder: (context, index) {
        if (index == 0) {
          return Column(
            children: [
              MediaMetadataView(
                metadata: details.metadata,
                sourceName: sourceName,
                readArtwork: readArtwork,
              ),
              if (chapters.isEmpty) const Text('No readable chapters found.'),
            ],
          );
        }

        final chapter = chapters[index - 1];
        final subtitle = <String>[
          chapter.scanlator ?? sourceName,
          if (chapter.chapterNumber != null) 'Chapter ${chapter.chapterNumber}',
          if (chapter.uploadedAt != null)
            chapter.uploadedAt!.toIso8601String().split('T').first,
          if (!chapter.canReadPages) 'Not readable in Hikari',
        ].join(' · ');
        return ListTile(
          key: ValueKey(chapter.source),
          title: Text(chapter.title),
          subtitle: Text(subtitle),
          enabled: !openingChapter && chapter.canReadPages,
          onTap: chapter.canReadPages ? () => onOpenChapter(chapter) : null,
        );
      },
    );
  }
}
