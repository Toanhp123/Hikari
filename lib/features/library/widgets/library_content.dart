import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/features/media/widgets/media_metadata_view.dart';
import 'package:hikari/core/ui/content/hikari_list_row.dart';
import 'package:hikari/core/ui/feedback/hikari_async_state_view.dart';
import 'package:hikari/core/ui/layout/hikari_content_region.dart';
import 'package:hikari/core/ui/foundation/hikari_radius.dart';
import 'package:hikari/core/ui/foundation/hikari_size.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/controls/hikari_button.dart';
import 'package:hikari/core/ui/controls/hikari_filter_chip.dart';
import 'package:hikari/core/ui/controls/hikari_chip_row.dart';
import 'package:hikari/core/ui/layout/hikari_adaptive_grid.dart';
import 'package:hikari/core/ui/media/hikari_artwork.dart';
import 'package:hikari/core/ui/media/hikari_card_metrics.dart';
import 'package:hikari/core/ui/media/hikari_poster_card.dart';
import 'package:hikari/features/media/widgets/media_type_presentation.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/continue_reading_item.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/library/library_view_model.dart';

class LibraryContent extends StatelessWidget {
  const LibraryContent({
    super.key,
    required this.state,
    this.readArtwork,
    required this.openMedia,
    required this.onReload,
    required this.onSelectMediaType,
    required this.onRemove,
    this.onBrowseCatalog,
  });

  final LibraryUiState state;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;
  final void Function(BuildContext, Media) openMedia;
  final VoidCallback onReload;
  final ValueChanged<MediaType?> onSelectMediaType;
  final void Function(LibraryEntry) onRemove;
  final VoidCallback? onBrowseCatalog;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    if (state.status == LibraryStatus.loading) {
      return HikariAsyncStateView(
        status: HikariAsyncStatus.loading,
        child: const SizedBox.shrink(),
      );
    }
    if (state.status == LibraryStatus.error) {
      return HikariAsyncStateView(
        status: HikariAsyncStatus.fatalError,
        message: 'Could not load your library. Please try loading your saved titles again.',
        onRetry: onReload,
        child: const SizedBox.shrink(),
      );
    }
    if (state.entries.isEmpty) {
      return HikariAsyncStateView(
        status: HikariAsyncStatus.empty,
        emptyTitle: 'Your library is empty.',
        emptyMessage: 'Save titles from their details to find them here.',
        emptyAction: onBrowseCatalog == null
            ? null
            : HikariButton(
                label: 'Explore titles',
                icon: const Icon(Icons.explore_outlined),
                onPressed: onBrowseCatalog,
              ),
        child: const SizedBox.shrink(),
      );
    }

    final visibleEntries = state.visibleEntries;
    final readingBySource = <SourceMediaRef, ContinueReadingItem>{
      for (final item in state.continueItems) item.media.source: item,
    };
    final savedCountLabel = visibleEntries.length == 1
        ? '1 saved title'
        : '${visibleEntries.length} saved titles';
    return HikariContentRegion(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _LibraryFilters(
            selectedType: state.mediaType,
            onSelect: onSelectMediaType,
          ),
          Padding(
            padding: const EdgeInsets.only(
              top: HikariSpacing.xs,
              bottom: HikariSpacing.sm,
            ),
            child: Text(
              state.mediaType == null
                  ? savedCountLabel
                  : '${visibleEntries.length} of '
                        '${state.entries.length} titles',
              style: Theme.of(context).textTheme.labelMedium
                  ?.copyWith(color: colors.onSurfaceVariant),
            ),
          ),
          if (state.progressError != null)
            Padding(
              padding: const EdgeInsets.only(bottom: HikariSpacing.sm),
              child: Text(
                'Some reading progress is unavailable.',
                style: Theme.of(context).textTheme.bodySmall
                    ?.copyWith(color: colors.onSurfaceVariant),
              ),
            ),
          Expanded(
            child: visibleEntries.isEmpty
                ? HikariAsyncStateView(
                    status: HikariAsyncStatus.empty,
                    emptyTitle: 'No matching titles',
                    emptyMessage: 'Try another media type or show all titles.',
                    emptyAction: TextButton(
                      onPressed: () => onSelectMediaType(null),
                      child: const Text('Show all types'),
                    ),
                    child: const SizedBox.shrink(),
                  )
                : state.viewMode == LibraryViewMode.grid
                ? _LibraryGrid(
                    entries: visibleEntries,
                    readingBySource: readingBySource,
                    readArtwork: readArtwork,
                    openMedia: openMedia,
                    onRemove: onRemove,
                  )
                : _LibraryList(
                    entries: visibleEntries,
                    readingBySource: readingBySource,
                    readArtwork: readArtwork,
                    openMedia: openMedia,
                    onRemove: onRemove,
                  ),
          ),
        ],
      ),
    );
  }
}

class _LibraryFilters extends StatelessWidget {
  const _LibraryFilters({required this.selectedType, required this.onSelect});

  final MediaType? selectedType;
  final ValueChanged<MediaType?> onSelect;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: HikariSpacing.xs),
      child: HikariChipRow(
        children: [
          HikariFilterChip(
            label: 'All',
            selected: selectedType == null,
            onSelected: (_) => onSelect(null),
          ),
          for (final type in MediaType.values)
            HikariFilterChip(
              label: mediaTypeLabel(type),
              selected: selectedType == type,
              onSelected: (_) => onSelect(type),
            ),
        ],
      ),
    );
  }
}

class _LibraryGrid extends StatelessWidget {
  const _LibraryGrid({
    required this.entries,
    required this.readingBySource,
    this.readArtwork,
    required this.openMedia,
    required this.onRemove,
  });

  final List<LibraryEntry> entries;
  final Map<SourceMediaRef, ContinueReadingItem> readingBySource;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;
  final void Function(BuildContext, Media) openMedia;
  final void Function(LibraryEntry) onRemove;

  @override
  Widget build(BuildContext context) {
    return HikariAdaptiveGrid.builder(
      padding: const EdgeInsets.symmetric(vertical: HikariSpacing.lg),
      physics: const BouncingScrollPhysics(),
      itemExtentBuilder: (context, width) =>
          hikariCardExtent(context, width: width),
      itemKeyBuilder: (index) => ValueKey(entries[index].media.source),
      itemCount: entries.length,
      itemBuilder: (context, index) {
        final entry = entries[index];
        final media = entry.media;
        final progress = readingBySource[media.source];
        final badge = mediaTypeBadgeColors(context, media.type);
        return Stack(
          children: [
            Positioned.fill(
              child: Builder(
                builder: (context) {
                  Widget poster(Uint8List? bytes) => Semantics(
                    value: progress == null ? null : _readingLabel(progress),
                    child: HikariPosterCard(
                      title: media.title,
                      artwork: HikariArtwork(
                        image: bytes == null ? null : MemoryImage(bytes),
                      ),
                      progress: progress?.completed == true
                          ? null
                          : progress?.progress,
                      badge: Badge(
                        backgroundColor: badge.background,
                        textColor: badge.foreground,
                        label: Text(mediaTypeBadgeLabel(media.type)),
                      ),
                      onTap: () => openMedia(context, media),
                    ),
                  );
                  final artwork = entry.artwork;
                  return artwork != null && readArtwork != null
                      ? SourceArtwork(
                          key: ValueKey(media.source),
                          resource: artwork,
                          read: readArtwork!,
                          builder: (_, bytes, _) => poster(bytes),
                        )
                      : poster(null);
                },
              ),
            ),
            Positioned(
              top: 4,
              right: 4,
              child: _LibraryRemoveButton(onPressed: () => onRemove(entry)),
            ),
          ],
        );
      },
    );
  }
}

class _LibraryList extends StatelessWidget {
  const _LibraryList({
    required this.entries,
    required this.readingBySource,
    this.readArtwork,
    required this.openMedia,
    required this.onRemove,
  });

  final List<LibraryEntry> entries;
  final Map<SourceMediaRef, ContinueReadingItem> readingBySource;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;
  final void Function(BuildContext, Media) openMedia;
  final void Function(LibraryEntry) onRemove;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return ListView.separated(
      padding: EdgeInsets.zero,
      physics: const BouncingScrollPhysics(),
      itemCount: entries.length,
      separatorBuilder: (context, index) =>
          const SizedBox(height: HikariSpacing.sm),
      itemBuilder: (context, index) {
        final entry = entries[index];
        final media = entry.media;
        final progress = readingBySource[media.source];
        return Material(
          color: colors.surfaceContainer,
          shape: RoundedRectangleBorder(
            borderRadius: HikariRadius.borderSm,
            side: BorderSide(color: colors.outline),
          ),
          clipBehavior: Clip.antiAlias,
          key: ValueKey(media.source),
          child: HikariListRow(
            leading: SizedBox.square(
              dimension: HikariSize.touchTarget,
              child: Builder(
                builder: (context) {
                  final badge = mediaTypeBadgeColors(context, media.type);
                  Widget fallback() => ColoredBox(
                    color: badge.background,
                    child: Icon(switch (media.type) {
                      MediaType.anime => Icons.movie_outlined,
                      MediaType.manga => Icons.menu_book_rounded,
                      MediaType.lightNovel => Icons.auto_stories_rounded,
                    }, color: badge.foreground),
                  );
                  final artwork = entry.artwork;
                  return ClipRRect(
                    borderRadius: HikariRadius.borderSm,
                    child: artwork != null && readArtwork != null
                        ? SourceArtwork(
                            key: ValueKey(media.source),
                            resource: artwork,
                            read: readArtwork!,
                            builder: (_, bytes, _) => HikariArtwork(
                              image: bytes == null ? null : MemoryImage(bytes),
                              placeholder: fallback(),
                            ),
                          )
                        : fallback(),
                  );
                },
              ),
            ),
            title: Text(
              media.title,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w600,
                color: colors.onSurface,
              ),
            ),
            subtitle: Text(
              progress == null
                  ? mediaTypeLabel(media.type)
                  : '${mediaTypeLabel(media.type)} · ${_readingLabel(progress)}',
              style: Theme.of(context).textTheme.bodySmall
                  ?.copyWith(color: colors.onSurfaceVariant),
            ),
            onTap: () => openMedia(context, media),
            trailing: _LibraryRemoveButton(onPressed: () => onRemove(entry)),
          ),
        );
      },
    );
  }
}

/// A saved item is already known to be in Library; do not create a separate
/// membership subscription for every visible row or poster.
class _LibraryRemoveButton extends StatelessWidget {
  const _LibraryRemoveButton({required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => IconButton(
    tooltip: 'Remove from library',
    onPressed: onPressed,
    icon: const Icon(Icons.bookmark_rounded),
  );
}

String _readingLabel(ContinueReadingItem item) {
  final chapter = item.chapter != null;
  if (chapter && item.completed) return 'Last chapter completed';
  return switch (item.position) {
    PagePosition(:final pageCount) when pageCount == 0 =>
      chapter ? 'Resume chapter' : 'Resume reading',
    PagePosition(:final pageIndex, :final pageCount) when chapter =>
      'Chapter page ${pageIndex + 1} of $pageCount',
    PagePosition(:final pageIndex, :final pageCount) =>
      'Page ${pageIndex + 1} of $pageCount',
    TextPosition(:final progression) when chapter =>
      'Chapter ${(progression * 100).round()}% read',
    TextPosition(:final progression) => '${(progression * 100).round()}% read',
    VideoPosition() => 'Resume video',
    DocumentPosition() => 'Resume reading',
    _ => 'Continue reading',
  };
}
