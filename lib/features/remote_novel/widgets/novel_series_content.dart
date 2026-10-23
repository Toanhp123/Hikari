import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/patterns/chapter_control_bar.dart';
import 'package:hikari/core/ui/content/hikari_chapter_row.dart';
import 'package:hikari/core/ui/feedback/hikari_notice_banner.dart';
import 'package:hikari/core/ui/feedback/hikari_async_state_view.dart';
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
    this.artworkOwner,
  });

  final Object? artworkOwner;
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
    final metadata = widget.viewModel.effectiveMetadata;
    if (state.initialLoading && metadata == null) {
      return const HikariAsyncStateView(
        status: HikariAsyncStatus.loading,
        child: SizedBox.shrink(),
      );
    }
    if (state.failed && metadata == null) {
      return HikariAsyncStateView(
        status: HikariAsyncStatus.fatalError,
        message: 'Could not load novel details.',
        onRetry: () => unawaited(widget.onRefresh()),
        child: const SizedBox.shrink(),
      );
    }

    final details = state.details;
    final allChapters = details?.chapters ?? const [];
    final primaryChapter = widget.viewModel.primaryChapter;
    final searchQuery = widget.viewModel.searchQuery;
    final displayChapters = widget.viewModel.displayChapters;

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
                  HikariNoticeBanner(
                    tone: HikariNoticeTone.warning,
                    message: 'Could not refresh chapters. Showing the last loaded list.',
                    action: TextButton(
                      onPressed: () => unawaited(widget.onRefresh()),
                      child: const Text('Retry'),
                    ),
                  ),
                if (metadata != null)
                  MediaMetadataView(
                    key: const ValueKey('series-metadata'),
                    metadata: metadata,
                    sourceName: widget.sourceName,
                    readArtwork: widget.readArtwork,
                    artworkOwner: widget.artworkOwner,
                  ),
                if (widget.viewModel.state.initialLoading)
                  const Padding(
                    padding: EdgeInsets.all(HikariSpacing.lg),
                    child: Center(
                      child: CircularProgressIndicator.adaptive(
                        semanticsLabel: 'Loading chapters',
                      ),
                    ),
                  ),
                if (widget.viewModel.state.failed)
                  Semantics(
                    liveRegion: true,
                    child: HikariNoticeBanner(
                      tone: HikariNoticeTone.warning,
                      message: 'Could not load chapters. Check source access and try again.',
                      action: TextButton(
                        onPressed: () => unawaited(widget.onRefresh()),
                        child: const Text('Retry'),
                      ),
                    ),
                  ),
                if (details != null)
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
                if (details != null && allChapters.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(HikariSpacing.lg),
                    child: Text('No readable chapters found.'),
                  )
                else if (details != null && displayChapters.isEmpty)
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
          return HikariChapterRow(
            key: ValueKey(chapter.source),
            title: chapter.title,
            subtitle: [
              if (chapter.chapterNumber != null)
                'Chapter ${chapter.chapterNumber}',
              ...chapter.scanlators,
              if (chapter.releaseLabel != null)
                chapter.releaseLabel!
              else if (chapter.releaseDate != null)
                chapter.releaseDate!.toIso8601String().split('T').first,
            ].join(' · '),
            enabled: !widget.openingChapter,
            onTap: () => widget.onOpenChapter(chapter),
          );
        },
      ),
    );
  }
}
