import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/chapter_control_bar.dart';
import 'package:hikari/core/ui/patterns/primary_reading_cta.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/remote_manga/manga_series_view_model.dart';

class MangaSeriesContent extends StatefulWidget {
  const MangaSeriesContent({
    super.key,
    required this.viewModel,
    required this.sourceName,
    required this.openingChapter,
    required this.onRefresh,
    required this.onOpenChapter,
    this.readArtwork,
    this.artworkOwner,
  });

  final Object? artworkOwner;
  final MangaSeriesViewModel viewModel;
  final String sourceName;
  final bool openingChapter;
  final Future<void> Function() onRefresh;
  final ValueChanged<MangaChapter> onOpenChapter;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;

  @override
  State<MangaSeriesContent> createState() => _MangaSeriesContentState();
}

class _MangaSeriesContentState extends State<MangaSeriesContent> {
  bool _isSearchExpanded = false;
  String get _searchQuery => widget.viewModel.searchQuery;

  @override
  Widget build(BuildContext context) {
    if (widget.viewModel.state.initialLoading) {
      return const Center(child: CircularProgressIndicator.adaptive());
    }
    if (widget.viewModel.state.failed) {
      return Center(
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Could not load chapters. Check source access or rate limits.',
              ),
              TextButton(
                onPressed: () => unawaited(widget.onRefresh()),
                child: const Text('Try again'),
              ),
            ],
          ),
        ),
      );
    }

    final details = widget.viewModel.state.details!;
    final allChapters = details.chapters;
    final primaryChapter = widget.viewModel.primaryChapter;

    final query = _searchQuery.trim().toLowerCase();
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
                if (widget.viewModel.state.refreshFailed)
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
                  artworkOwner: widget.artworkOwner,
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
                    filteredChapters: query.isNotEmpty
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
                    searchQuery: _searchQuery,
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
                        Text('No chapters matching "$_searchQuery"'),
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
          final subtitle = <String>[
            chapter.scanlator ?? widget.sourceName,
            if (chapter.chapterNumber != null)
              'Chapter ${chapter.chapterNumber}',
            if (chapter.uploadedAt != null)
              chapter.uploadedAt!.toIso8601String().split('T').first,
            if (!chapter.canReadPages) 'Not readable in Hikari',
          ].join(' · ');

          final isInteractive = !widget.openingChapter && chapter.canReadPages;

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
