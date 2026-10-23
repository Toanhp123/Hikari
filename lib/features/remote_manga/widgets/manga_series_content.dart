import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/features/reading/widgets/chapter_list_controls.dart';
import 'package:hikari/core/ui/content/hikari_chapter_row.dart';
import 'package:hikari/core/ui/feedback/hikari_notice_banner.dart';
import 'package:hikari/core/ui/feedback/hikari_async_state_view.dart';
import 'package:hikari/core/ui/controls/hikari_button.dart';
import 'package:hikari/features/media/widgets/media_metadata_view.dart';
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
    final metadata = widget.viewModel.effectiveMetadata;
    if (widget.viewModel.state.initialLoading && metadata == null) {
      return const HikariAsyncStateView(
        status: HikariAsyncStatus.loading,
        child: SizedBox.shrink(),
      );
    }
    if (widget.viewModel.state.failed && metadata == null) {
      return HikariAsyncStateView(
        status: HikariAsyncStatus.fatalError,
        message: 'Could not load chapters. Check source access or rate limits.',
        onRetry: () => unawaited(widget.onRefresh()),
        child: const SizedBox.shrink(),
      );
    }

    final details = widget.viewModel.state.details;
    final allChapters = details?.chapters ?? const [];
    final primaryChapter = widget.viewModel.primaryChapter;

    final query = _searchQuery.trim().toLowerCase();
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
                if (widget.viewModel.state.refreshFailed)
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
                  Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: HikariSpacing.md,
                      vertical: HikariSpacing.sm,
                    ),
                    child: HikariButton(
                      label: widget.viewModel.isContinuation
                          ? 'Continue reading'
                          : 'Start reading',
                      icon: const Icon(Icons.play_arrow_rounded),
                      size: HikariButtonSize.large,
                      isFullWidth: true,
                      onPressed: widget.openingChapter || primaryChapter == null
                          ? null
                          : () => widget.onOpenChapter(primaryChapter),
                    ),
                  ),
                if (allChapters.isNotEmpty)
                  ChapterListControls(
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
          return HikariChapterRow(
            key: ValueKey(chapter.source),
            title: chapter.title,
            subtitle: [
              chapter.scanlator ?? widget.sourceName,
              if (chapter.chapterNumber != null)
                'Chapter ${chapter.chapterNumber}',
              if (chapter.uploadedAt != null)
                chapter.uploadedAt!.toIso8601String().split('T').first,
              if (!chapter.canReadPages) 'Not readable in Hikari',
            ].join(' · '),
            enabled: !widget.openingChapter && chapter.canReadPages,
            onTap: () => widget.onOpenChapter(chapter),
          );
        },
      ),
    );
  }
}
