import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/chapter_control_bar.dart';
import 'package:hikari/core/ui/patterns/primary_reading_cta.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/remote_novel/novel_series_view_model.dart';

class NovelSeriesContent extends StatefulWidget {
  const NovelSeriesContent({
    super.key,
    required this.viewModel,
    required this.sourceName,
    required this.openingChapter,
    required this.onRefresh,
    required this.onOpenChapter,
    this.readArtwork,
  });

  final NovelSeriesViewModel viewModel;
  final String sourceName;
  final bool openingChapter;
  final Future<void> Function() onRefresh;
  final ValueChanged<NovelChapter> onOpenChapter;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;

  @override
  State<NovelSeriesContent> createState() => _NovelSeriesContentState();
}

class _NovelSeriesContentState extends State<NovelSeriesContent> {
  bool _isSearchExpanded = false;

  @override
  Widget build(BuildContext context) {
    final state = widget.viewModel.state;
    if (state.initialLoading) {
      return const Center(child: CircularProgressIndicator.adaptive());
    }
    if (state.failed) {
      return Center(
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Could not load novel details.'),
              TextButton(
                onPressed: () => unawaited(widget.onRefresh()),
                child: const Text('Try again'),
              ),
            ],
          ),
        ),
      );
    }

    final details = state.details!;
    final allChapters = details.chapters;
    final primaryChapter = widget.viewModel.primaryChapter;
    final searchQuery = widget.viewModel.searchQuery;
    final displayChapters = widget.viewModel.displayChapters;
    final colorScheme = Theme.of(context).colorScheme;

    return RefreshIndicator.adaptive(
      onRefresh: widget.onRefresh,
      child: ListView.builder(
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: displayChapters.length + 1,
        itemBuilder: (context, index) {
          if (index == 0) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (state.refreshFailed)
                  MaterialBanner(
                    content: const Text(
                      'Could not refresh chapters. Showing the last loaded list.',
                    ),
                    actions: [
                      TextButton(
                        onPressed: () => unawaited(widget.onRefresh()),
                        child: const Text('Retry'),
                      ),
                    ],
                  ),
                MediaMetadataView(
                  metadata: details.metadata,
                  sourceName: widget.sourceName,
                  readArtwork: widget.readArtwork,
                ),
                PrimaryReadingCta(
                  label: widget.viewModel.isContinuation
                      ? 'Continue reading'
                      : 'Start reading',
                  enabled: !widget.openingChapter,
                  onPressed: primaryChapter == null
                      ? null
                      : () => widget.onOpenChapter(primaryChapter),
                ),
                if (allChapters.isNotEmpty)
                  ChapterControlBar(
                    totalChapters: allChapters.length,
                    filteredChapters: searchQuery.trim().isNotEmpty
                        ? displayChapters.length
                        : null,
                    reverseSourceOrder: widget.viewModel.reverseSourceOrder,
                    onToggleSourceOrder: widget.viewModel.toggleSourceOrder,
                    isSearchExpanded: _isSearchExpanded,
                    onToggleSearchExpanded: () => setState(() {
                      _isSearchExpanded = !_isSearchExpanded;
                      if (!_isSearchExpanded) {
                        widget.viewModel.setSearchQuery('');
                      }
                    }),
                    searchQuery: searchQuery,
                    onSearchChanged: widget.viewModel.setSearchQuery,
                    onClearSearch: () => widget.viewModel.setSearchQuery(''),
                  ),
                if (allChapters.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(HikariSpacing.lg),
                    child: Text('No readable chapters found.'),
                  )
                else if (displayChapters.isEmpty)
                  Padding(
                    padding: const EdgeInsets.all(HikariSpacing.lg),
                    child: Column(
                      children: [
                        Text('No chapters matching "$searchQuery"'),
                        TextButton(
                          onPressed: () => widget.viewModel.setSearchQuery(''),
                          child: const Text('Clear filter'),
                        ),
                      ],
                    ),
                  ),
              ],
            );
          }
          final chapter = displayChapters[index - 1];
          final subtitle = [
            if (chapter.chapterNumber != null)
              'Chapter ${chapter.chapterNumber}',
            ...chapter.scanlators,
            if (chapter.releaseLabel != null)
              chapter.releaseLabel!
            else if (chapter.releaseDate != null)
              chapter.releaseDate!.toIso8601String().split('T').first,
          ].join(' · ');
          final isInteractive = !widget.openingChapter;
          return Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: HikariSpacing.md,
              vertical: HikariSpacing.xs,
            ),
            child: ListTile(
              key: ValueKey(chapter.source),
              shape: const RoundedRectangleBorder(
                borderRadius: HikariRadius.borderMd,
              ),
              tileColor: colorScheme.surfaceContainerLow,
              title: Text(chapter.title),
              subtitle: Text(subtitle),
              trailing: isInteractive
                  ? const Icon(Icons.chevron_right_rounded)
                  : null,
              enabled: isInteractive,
              onTap: isInteractive ? () => widget.onOpenChapter(chapter) : null,
            ),
          );
        },
      ),
    );
  }
}
