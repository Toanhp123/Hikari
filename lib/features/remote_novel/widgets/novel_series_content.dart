import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/chapter_list_patterns.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/features/remote_novel/novel_series_view_model.dart';

class NovelSeriesContent extends StatefulWidget {
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
  State<NovelSeriesContent> createState() => _NovelSeriesContentState();
}

class _NovelSeriesContentState extends State<NovelSeriesContent> {
  bool _isReversed = false;
  bool _isSearching = false;
  String _searchQuery = '';

  @override
  Widget build(BuildContext context) {
    if (widget.state.initialLoading) {
      return const Center(child: CircularProgressIndicator.adaptive());
    }
    if (widget.state.failed) {
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

    final details = widget.state.details!;
    final allChapters = details.chapters;
    final firstReadable = details.chaptersInReadingOrder.firstOrNull;

    final query = _searchQuery.trim().toLowerCase();
    final filteredChapters = query.isEmpty
        ? allChapters
        : allChapters.where((c) {
            if (c.title.toLowerCase().contains(query)) return true;
            if (c.chapterNumber != null) {
              final num = c.chapterNumber!;
              final numStr =
                  (num % 1 == 0) ? num.toInt().toString() : num.toString();
              if (numStr.contains(query)) return true;
              if (num.toString().contains(query)) return true;
            }
            return false;
          }).toList();

    final displayChapters = _isReversed
        ? filteredChapters.reversed.toList()
        : filteredChapters;

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
                if (widget.state.refreshFailed)
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
                if (widget.openingChapter) const LinearProgressIndicator(),
                if (firstReadable != null)
                  PrimaryReadingCta(
                    enabled: !widget.openingChapter,
                    onPressed: () => widget.onOpenChapter(firstReadable),
                  ),
                if (allChapters.isNotEmpty)
                  ChapterControlBar(
                    totalChapters: allChapters.length,
                    filteredChapters:
                        query.isNotEmpty ? filteredChapters.length : null,
                    isReversed: _isReversed,
                    onToggleSort: () => setState(() => _isReversed = !_isReversed),
                    isSearching: _isSearching,
                    onToggleSearch: () => setState(() {
                      _isSearching = !_isSearching;
                      if (!_isSearching) _searchQuery = '';
                    }),
                    searchQuery: _searchQuery,
                    onSearchChanged: (val) => setState(() => _searchQuery = val),
                    onClearSearch: () => setState(() => _searchQuery = ''),
                  ),
                if (allChapters.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(HikariSpacing.lg),
                    child: Text('No readable chapters found.'),
                  )
                else if (filteredChapters.isEmpty)
                  Padding(
                    padding: const EdgeInsets.all(HikariSpacing.lg),
                    child: Column(
                      children: [
                        Text('No chapters matching "$_searchQuery"'),
                        TextButton(
                          onPressed: () => setState(() => _searchQuery = ''),
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
              onTap: () => widget.onOpenChapter(chapter),
            ),
          );
        },
      ),
    );
  }
}
