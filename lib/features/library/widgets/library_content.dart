import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/core/ui/patterns/media_artwork_decode.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/continue_reading_item.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/library/library_view_model.dart';

Widget _emptyContent(BuildContext context) => const SizedBox.shrink();

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
      return AsyncStateView(
        status: AsyncViewStatus.loading,
        contentBuilder: (_) => const SizedBox.shrink(),
      );
    }
    if (state.status == LibraryStatus.error) {
      return AsyncStateView(
        status: AsyncViewStatus.error,
        errorTitle: 'Could not load your library.',
        errorMessage: 'Please try loading your saved titles again.',
        onRetry: onReload,
        contentBuilder: (_) => const SizedBox.shrink(),
      );
    }
    if (state.entries.isEmpty) {
      return AsyncStateView(
        status: AsyncViewStatus.empty,
        emptyTitle: 'Your library is empty.',
        emptyMessage: 'Save titles from their details to find them here.',
        emptyIcon: Icons.bookmark_border_rounded,
        emptyAction: onBrowseCatalog == null
            ? null
            : HikariButton(
                label: 'Explore titles',
                icon: const Icon(Icons.explore_outlined),
                onPressed: onBrowseCatalog,
              ),
        contentBuilder: _emptyContent,
      );
    }

    final visibleEntries = state.visibleEntries;
    final readingBySource = <SourceMediaRef, ContinueReadingItem>{
      for (final item in state.continueItems) item.media.source: item,
    };
    final savedCountLabel = visibleEntries.length == 1
        ? '1 saved title'
        : '${visibleEntries.length} saved titles';
    return Column(
      children: [
        Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: HikariBreakpoints.maxContentWidth,
            ),
            child: Column(
              children: [
                _LibraryFilters(
                  selectedType: state.mediaType,
                  onSelect: onSelectMediaType,
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(
                    HikariSpacing.lg,
                    HikariSpacing.xs,
                    HikariSpacing.lg,
                    HikariSpacing.sm,
                  ),
                  child: Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: Text(
                      state.mediaType == null
                          ? savedCountLabel
                          : '${visibleEntries.length} of '
                                '${state.entries.length} titles',
                      style: Theme.of(context).textTheme.labelMedium
                          ?.copyWith(color: colors.onSurfaceVariant),
                    ),
                  ),
                ),
                if (state.progressError != null)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(
                      HikariSpacing.lg,
                      0,
                      HikariSpacing.lg,
                      HikariSpacing.sm,
                    ),
                    child: Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: Text(
                        'Some reading progress is unavailable.',
                        style: Theme.of(context).textTheme.bodySmall
                            ?.copyWith(color: colors.onSurfaceVariant),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
        Expanded(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(
                maxWidth: HikariBreakpoints.maxContentWidth,
              ),
              child: visibleEntries.isEmpty
                  ? AsyncStateView(
                      status: AsyncViewStatus.empty,
                      emptyTitle: 'No matching titles',
                      emptyMessage:
                          'Try another media type or show all titles.',
                      emptyIcon: Icons.filter_list_off_rounded,
                      emptyAction: TextButton(
                        onPressed: () => onSelectMediaType(null),
                        child: const Text('Show all types'),
                      ),
                      contentBuilder: _emptyContent,
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
          ),
        ),
      ],
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
      padding: const EdgeInsets.symmetric(
        horizontal: HikariSpacing.lg,
        vertical: HikariSpacing.xs,
      ),
      child: HikariChipRow(
        children: [
          HikariChip(
            label: 'All',
            isSelected: selectedType == null,
            onTap: () => onSelect(null),
          ),
          for (final type in MediaType.values)
            HikariChip(
              label: mediaTypeLabel(type),
              isSelected: selectedType == type,
              onTap: () => onSelect(type),
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
    return GridView.builder(
      padding: const EdgeInsets.all(HikariSpacing.lg),
      physics: const BouncingScrollPhysics(),
      gridDelegate: mediaPosterGridDelegate,
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
                    child: MediaPoster(
                      title: media.title,
                      imageBytes: bytes,
                      progress: progress?.completed == true
                          ? null
                          : progress?.progress,
                      badgeText: mediaTypeBadgeLabel(media.type),
                      badgeColor: badge.background,
                      badgeForegroundColor: badge.foreground,
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
      padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
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
          child: ListTile(
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
                            builder: (_, bytes, _) => bytes == null
                                ? fallback()
                                : Image.memory(
                                    bytes,
                                    fit: BoxFit.cover,
                                    cacheWidth: mediaArtworkCacheWidth(
                                      context,
                                      HikariSize.touchTarget,
                                    ),
                                    errorBuilder: (_, _, _) => fallback(),
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
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
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
