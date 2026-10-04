import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/remote_novel/novel_series_view_model.dart';

class NovelSeriesContent extends StatelessWidget {
  const NovelSeriesContent({
    super.key,
    required this.state,
    required this.sourceName,
    required this.openingChapter,
    required this.onRefresh,
    required this.onOpenChapter,
    this.readArtwork,
  });

  final NovelSeriesUiState state;
  final String sourceName;
  final bool openingChapter;
  final Future<void> Function() onRefresh;
  final ValueChanged<NovelChapter> onOpenChapter;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;

  @override
  Widget build(BuildContext context) {
    if (state.initialLoading) {
      return const Center(child: CircularProgressIndicator.adaptive());
    }
    if (state.failed) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Could not load novel details.'),
            TextButton(
              onPressed: () => unawaited(onRefresh()),
              child: const Text('Try again'),
            ),
          ],
        ),
      );
    }

    final details = state.details!;
    return RefreshIndicator.adaptive(
      onRefresh: onRefresh,
      child: ListView.builder(
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: details.chapters.length + 1,
        itemBuilder: (context, index) {
          if (index == 0) {
            return Column(
              children: [
                if (state.refreshFailed)
                  MaterialBanner(
                    content: const Text(
                      'Could not refresh chapters. Showing the last loaded list.',
                    ),
                    actions: [
                      TextButton(
                        onPressed: () => unawaited(onRefresh()),
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                MediaMetadataView(
                  metadata: details.metadata,
                  sourceName: sourceName,
                  readArtwork: readArtwork,
                ),
                if (openingChapter) const LinearProgressIndicator(),
                if (details.chapters.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(HikariSpacing.lg),
                    child: Text('No readable chapters found.'),
                  ),
              ],
            );
          }

          final chapter = details.chapters[index - 1];
          return ListTile(
            key: ValueKey(chapter.source),
            title: Text(chapter.title),
            enabled: !openingChapter,
            subtitle: Text(
              [
                if (chapter.chapterNumber != null)
                  'Chapter ${chapter.chapterNumber}',
                ...chapter.scanlators,
                if (chapter.releaseLabel != null)
                  chapter.releaseLabel!
                else if (chapter.releaseDate != null)
                  chapter.releaseDate!.toIso8601String().split('T').first,
              ].join(' · '),
            ),
            onTap: () => onOpenChapter(chapter),
          );
        },
      ),
    );
  }
}
