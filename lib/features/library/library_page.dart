import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_button.dart';
import 'package:hikari/features/library/library_view_model.dart';

class LibraryPage extends StatefulWidget {
  const LibraryPage({
    super.key,
    required this.repository,
    required this.openMedia,
  });

  final LibraryRepository repository;
  final void Function(BuildContext, Media) openMedia;

  @override
  State<LibraryPage> createState() => _LibraryPageState();
}

class _LibraryPageState extends State<LibraryPage> {
  late final LibraryViewModel _model = LibraryViewModel(widget.repository);

  @override
  void dispose() {
    _model.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;

    return ListenableBuilder(
      listenable: _model,
      builder: (context, _) {
        final state = _model.state;
        return HikariScaffold(
          useSafeArea: true,
          appBar: AppBar(
            title: Text(
              'Library',
              style: HikariTypography.titleLarge.copyWith(
                color: colors.textPrimary,
                fontWeight: FontWeight.w700,
              ),
            ),
            actions: [
              HikariIconButton(
                icon: Icon(
                  state.viewMode == LibraryViewMode.grid
                      ? Icons.view_list_rounded
                      : Icons.grid_view_rounded,
                ),
                tooltip: state.viewMode == LibraryViewMode.grid
                    ? 'Switch to list view'
                    : 'Switch to grid view',
                onPressed: _model.toggleViewMode,
              ),
              const SizedBox(width: HikariSpacing.xs),
            ],
          ),
          body: _buildBody(context, state, colors),
        );
      },
    );
  }

  Widget _buildBody(
    BuildContext context,
    LibraryUiState state,
    HikariColors colors,
  ) {
    if (state.loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (state.error != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Could not load your library.'),
            const SizedBox(height: HikariSpacing.sm),
            TextButton(
              onPressed: _model.reload,
              child: const Text('Try again'),
            ),
          ],
        ),
      );
    }
    if (state.entries.isEmpty) {
      return const Center(
        child: Text('Your library is empty.', style: TextStyle(fontSize: 14)),
      );
    }

    final entries = state.visibleEntries;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: HikariSpacing.lg,
            vertical: HikariSpacing.xs,
          ),
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                HikariChip(
                  label: 'All Types',
                  isSelected: state.mediaType == null,
                  onTap: () => _model.selectMediaType(null),
                ),
                const SizedBox(width: HikariSpacing.xs),
                HikariChip(
                  label: 'Anime',
                  customBadgeColor: colors.badgeVideo,
                  isSelected: state.mediaType == MediaType.anime,
                  onTap: () => _model.selectMediaType(MediaType.anime),
                ),
                const SizedBox(width: HikariSpacing.xs),
                HikariChip(
                  label: 'Manga',
                  customBadgeColor: colors.badgeManga,
                  isSelected: state.mediaType == MediaType.manga,
                  onTap: () => _model.selectMediaType(MediaType.manga),
                ),
                const SizedBox(width: HikariSpacing.xs),
                HikariChip(
                  label: 'Novel',
                  customBadgeColor: colors.badgeNovel,
                  isSelected: state.mediaType == MediaType.lightNovel,
                  onTap: () => _model.selectMediaType(MediaType.lightNovel),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: HikariSpacing.sm),
        Expanded(
          child: entries.isEmpty
              ? Center(
                  child: Text(
                    'No items match the selected media type.',
                    style: TextStyle(color: colors.textMuted),
                  ),
                )
              : state.viewMode == LibraryViewMode.grid
              ? _buildGridView(context, entries)
              : _buildListView(context, entries),
        ),
      ],
    );
  }

  Widget _buildGridView(BuildContext context, List<LibraryEntry> entries) {
    final colors = context.hikariColors;

    return GridView.builder(
      padding: const EdgeInsets.all(HikariSpacing.lg),
      physics: const BouncingScrollPhysics(),
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: context.responsiveGridColumns,
        crossAxisSpacing: HikariSpacing.md,
        mainAxisSpacing: HikariSpacing.md,
        childAspectRatio: 2 / 3,
      ),
      itemCount: entries.length,
      itemBuilder: (context, index) {
        final media = entries[index].media;
        final badgeColor = switch (media.type) {
          MediaType.anime => colors.badgeVideo,
          MediaType.manga => colors.badgeManga,
          MediaType.lightNovel => colors.badgeNovel,
        };
        final badgeText = switch (media.type) {
          MediaType.anime => 'ANIME',
          MediaType.manga => 'MANGA',
          MediaType.lightNovel => 'NOVEL',
        };

        return Stack(
          children: [
            MediaPoster(
              title: media.title,
              badgeText: badgeText,
              badgeColor: badgeColor,
              onTap: () => widget.openMedia(context, media),
            ),
            Positioned(
              top: 4,
              right: 4,
              child: LibraryButton(
                repository: widget.repository,
                media: media,
                onChanged: _model.repositoryChanged,
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _buildListView(BuildContext context, List<LibraryEntry> entries) {
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
            subtitle: Text(switch (media.type) {
              MediaType.anime => 'Anime',
              MediaType.manga => 'Manga',
              MediaType.lightNovel => 'Light Novel',
            }, style: TextStyle(color: colors.textSecondary, fontSize: 12)),
            onTap: () => widget.openMedia(context, media),
            trailing: LibraryButton(
              repository: widget.repository,
              media: media,
              onChanged: _model.repositoryChanged,
            ),
          ),
        );
      },
    );
  }
}
