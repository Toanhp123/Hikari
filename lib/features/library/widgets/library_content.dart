import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_view_model.dart';
import 'package:hikari/features/library/widgets/library_button.dart';

Widget _emptyContent(BuildContext context) => const SizedBox.shrink();

class LibraryContent extends StatelessWidget {
  const LibraryContent({
    super.key,
    required this.state,
    required this.repository,
    required this.openMedia,
    required this.onReload,
    required this.onSelectMediaType,
    required this.onRepositoryChanged,
    this.onBrowseCatalog,
  });

  final LibraryUiState state;
  final LibraryRepository repository;
  final void Function(BuildContext, Media) openMedia;
  final VoidCallback onReload;
  final ValueChanged<MediaType?> onSelectMediaType;
  final VoidCallback onRepositoryChanged;
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
                      repository: repository,
                      openMedia: openMedia,
                      onRepositoryChanged: onRepositoryChanged,
                    )
                  : _LibraryList(
                      entries: visibleEntries,
                      repository: repository,
                      openMedia: openMedia,
                      onRepositoryChanged: onRepositoryChanged,
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
        spacing: HikariSpacing.xs,
        children: [
          HikariChip(
            label: 'All Types',
            isSelected: selectedType == null,
            onTap: () => onSelect(null),
          ),
          for (final type in MediaType.values)
            HikariChip(
              label: mediaTypeLabel(type),
              customBadgeColor: mediaTypeBadgeColors(context, type).foreground,
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
    required this.repository,
    required this.openMedia,
    required this.onRepositoryChanged,
  });

  final List<LibraryEntry> entries;
  final LibraryRepository repository;
  final void Function(BuildContext, Media) openMedia;
  final VoidCallback onRepositoryChanged;

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      padding: const EdgeInsets.all(HikariSpacing.lg),
      physics: const BouncingScrollPhysics(),
      gridDelegate: mediaPosterGridDelegate,
      itemCount: entries.length,
      itemBuilder: (context, index) {
        final media = entries[index].media;
        final badge = mediaTypeBadgeColors(context, media.type);
        return Stack(
          children: [
            MediaPoster(
              title: media.title,
              badgeText: mediaTypeBadgeLabel(media.type),
              badgeColor: badge.background,
              badgeForegroundColor: badge.foreground,
              onTap: () => openMedia(context, media),
            ),
            Positioned(
              top: 4,
              right: 4,
              child: LibraryButton(
                repository: repository,
                media: media,
                onChanged: onRepositoryChanged,
              ),
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
    required this.repository,
    required this.openMedia,
    required this.onRepositoryChanged,
  });

  final List<LibraryEntry> entries;
  final LibraryRepository repository;
  final void Function(BuildContext, Media) openMedia;
  final VoidCallback onRepositoryChanged;

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
        final media = entries[index].media;
        return Material(
          color: colors.surfaceContainer,
          shape: RoundedRectangleBorder(
            borderRadius: HikariRadius.borderSm,
            side: BorderSide(color: colors.outline),
          ),
          clipBehavior: Clip.antiAlias,
          child: ListTile(
            leading: DecoratedBox(
              decoration: BoxDecoration(
                color: mediaTypeBadgeColors(context, media.type).background,
                borderRadius: HikariRadius.borderSm,
              ),
              child: SizedBox.square(
                dimension: HikariSize.touchTarget,
                child: Icon(switch (media.type) {
                  MediaType.anime => Icons.movie_outlined,
                  MediaType.manga => Icons.menu_book_rounded,
                  MediaType.lightNovel => Icons.auto_stories_rounded,
                }, color: mediaTypeBadgeColors(context, media.type).foreground),
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
              mediaTypeLabel(media.type),
              style: Theme.of(context).textTheme.bodySmall
                  ?.copyWith(color: colors.onSurfaceVariant),
            ),
            onTap: () => openMedia(context, media),
            trailing: LibraryButton(
              repository: repository,
              media: media,
              onChanged: onRepositoryChanged,
            ),
          ),
        );
      },
    );
  }
}
