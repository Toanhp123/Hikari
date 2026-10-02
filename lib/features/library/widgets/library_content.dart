import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_view_model.dart';
import 'package:hikari/features/library/widgets/library_button.dart';

class LibraryContent extends StatelessWidget {
  const LibraryContent({
    super.key,
    required this.state,
    required this.repository,
    required this.openMedia,
    required this.onReload,
    required this.onSelectMediaType,
    required this.onRepositoryChanged,
  });

  final LibraryUiState state;
  final LibraryRepository repository;
  final void Function(BuildContext, Media) openMedia;
  final VoidCallback onReload;
  final ValueChanged<MediaType?> onSelectMediaType;
  final VoidCallback onRepositoryChanged;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    if (state.status == LibraryStatus.loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (state.status == LibraryStatus.error) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Could not load your library.'),
            const SizedBox(height: HikariSpacing.sm),
            TextButton(onPressed: onReload, child: const Text('Try again')),
          ],
        ),
      );
    }
    if (state.entries.isEmpty) {
      return const Center(
        child: Text('Your library is empty.', style: TextStyle(fontSize: 14)),
      );
    }

    return Column(
      children: [
        _LibraryFilters(
          selectedType: state.mediaType,
          onSelect: onSelectMediaType,
        ),
        const SizedBox(height: HikariSpacing.sm),
        Expanded(
          child: state.visibleEntries.isEmpty
              ? Center(
                  child: Text(
                    'No items match the selected media type.',
                    style: TextStyle(color: colors.textMuted),
                  ),
                )
              : state.viewMode == LibraryViewMode.grid
              ? _LibraryGrid(
                  entries: state.visibleEntries,
                  repository: repository,
                  openMedia: openMedia,
                  onRepositoryChanged: onRepositoryChanged,
                )
              : _LibraryList(
                  entries: state.visibleEntries,
                  repository: repository,
                  openMedia: openMedia,
                  onRepositoryChanged: onRepositoryChanged,
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
    final colors = context.hikariColors;
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
              customBadgeColor: mediaTypeBadgeColor(colors, type),
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
    final colors = context.hikariColors;
    return GridView.builder(
      padding: const EdgeInsets.all(HikariSpacing.lg),
      physics: const BouncingScrollPhysics(),
      gridDelegate: mediaPosterGridDelegate,
      itemCount: entries.length,
      itemBuilder: (context, index) {
        final media = entries[index].media;
        return Stack(
          children: [
            MediaPoster(
              title: media.title,
              badgeText: mediaTypeBadgeLabel(media.type),
              badgeColor: mediaTypeBadgeColor(colors, media.type),
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
    final colors = context.hikariColors;
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
            side: BorderSide(color: colors.border),
          ),
          clipBehavior: Clip.antiAlias,
          child: ListTile(
            title: Text(
              media.title,
              style: TextStyle(
                fontWeight: FontWeight.w600,
                color: colors.textPrimary,
              ),
            ),
            subtitle: Text(
              mediaTypeLabel(media.type),
              style: TextStyle(color: colors.textSecondary, fontSize: 12),
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
