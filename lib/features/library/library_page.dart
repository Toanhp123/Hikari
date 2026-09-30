import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_button.dart';

enum LibraryViewMode { grid, list }

enum LibrarySortOption {
  recent('Recently Added'),
  title('Title (A-Z)');

  const LibrarySortOption(this.label);
  final String label;
}

enum LibraryCategoryTab {
  all('All'),
  inProgress('In Progress'),
  completed('Completed'),
  favorites('Favorites');

  const LibraryCategoryTab(this.label);
  final String label;
}

/// Upgraded Library screen supporting category tabs, Grid/List view toggle,
/// media type filters, and sorting.
class LibraryPage extends StatefulWidget {
  const LibraryPage({
    super.key,
    required this.repository,
    required this.openMedia,
    this.onOpenDetails,
  });

  final LibraryRepository repository;
  final void Function(BuildContext, Media) openMedia;
  final void Function(BuildContext, Media)? onOpenDetails;

  @override
  State<LibraryPage> createState() => _LibraryPageState();
}

class _LibraryPageState extends State<LibraryPage> {
  late Future<List<LibraryEntry>> _entriesFuture;
  LibraryViewMode _viewMode = LibraryViewMode.grid;
  LibraryCategoryTab _selectedTab = LibraryCategoryTab.all;
  MediaType? _selectedMediaType;
  final LibrarySortOption _sortOption = LibrarySortOption.recent;

  @override
  void initState() {
    super.initState();
    _reloadLibrary();
  }

  void _reloadLibrary() => setState(() {
    _entriesFuture = widget.repository.loadAll();
  });

  List<LibraryEntry> _filterAndSortEntries(List<LibraryEntry> entries) {
    var filtered = entries.where((e) {
      if (_selectedMediaType != null && e.media.type != _selectedMediaType) {
        return false;
      }
      return true;
    }).toList();

    if (_sortOption == LibrarySortOption.title) {
      filtered.sort((a, b) => a.media.title.compareTo(b.media.title));
    } else {
      filtered.sort((a, b) => b.addedAt.compareTo(a.addedAt));
    }

    return filtered;
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;

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
              _viewMode == LibraryViewMode.grid
                  ? Icons.view_list_rounded
                  : Icons.grid_view_rounded,
            ),
            tooltip: _viewMode == LibraryViewMode.grid
                ? 'Switch to list view'
                : 'Switch to grid view',
            onPressed: () => setState(() {
              _viewMode = _viewMode == LibraryViewMode.grid
                  ? LibraryViewMode.list
                  : LibraryViewMode.grid;
            }),
          ),
          const SizedBox(width: HikariSpacing.xs),
        ],
      ),
      body: FutureBuilder<List<LibraryEntry>>(
        future: _entriesFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('Could not load your library.'),
                  const SizedBox(height: HikariSpacing.sm),
                  TextButton(
                    onPressed: _reloadLibrary,
                    child: const Text('Try again'),
                  ),
                ],
              ),
            );
          }

          final allEntries = snapshot.data ?? [];
          if (allEntries.isEmpty) {
            return const Center(
              child: Text(
                'Your library is empty.',
                style: TextStyle(fontSize: 14),
              ),
            );
          }

          final displayEntries = _filterAndSortEntries(allEntries);

          return Column(
            children: [
              // Filter and Category Bar
              Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: HikariSpacing.lg,
                  vertical: HikariSpacing.xs,
                ),
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: [
                      // Category Tabs
                      ...LibraryCategoryTab.values.map((tab) {
                        return Padding(
                          padding: const EdgeInsets.only(
                            right: HikariSpacing.xs,
                          ),
                          child: HikariChip(
                            label: tab.label,
                            isSelected: _selectedTab == tab,
                            onTap: () => setState(() => _selectedTab = tab),
                          ),
                        );
                      }),
                      const SizedBox(width: HikariSpacing.sm),
                      // Media Type Filter Chips
                      HikariChip(
                        label: 'All Types',
                        isSelected: _selectedMediaType == null,
                        onTap: () => setState(() => _selectedMediaType = null),
                      ),
                      const SizedBox(width: HikariSpacing.xs),
                      HikariChip(
                        label: 'Anime',
                        customBadgeColor: colors.badgeVideo,
                        isSelected: _selectedMediaType == MediaType.anime,
                        onTap: () => setState(
                          () => _selectedMediaType =
                              _selectedMediaType == MediaType.anime
                              ? null
                              : MediaType.anime,
                        ),
                      ),
                      const SizedBox(width: HikariSpacing.xs),
                      HikariChip(
                        label: 'Manga',
                        customBadgeColor: colors.badgeManga,
                        isSelected: _selectedMediaType == MediaType.manga,
                        onTap: () => setState(
                          () => _selectedMediaType =
                              _selectedMediaType == MediaType.manga
                              ? null
                              : MediaType.manga,
                        ),
                      ),
                      const SizedBox(width: HikariSpacing.xs),
                      HikariChip(
                        label: 'Novel',
                        customBadgeColor: colors.badgeNovel,
                        isSelected: _selectedMediaType == MediaType.lightNovel,
                        onTap: () => setState(
                          () => _selectedMediaType =
                              _selectedMediaType == MediaType.lightNovel
                              ? null
                              : MediaType.lightNovel,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: HikariSpacing.sm),

              // Content View (Grid or List)
              Expanded(
                child: displayEntries.isEmpty
                    ? Center(
                        child: Text(
                          'No items match selected filter.',
                          style: TextStyle(color: colors.textMuted),
                        ),
                      )
                    : _viewMode == LibraryViewMode.grid
                    ? _buildGridView(context, displayEntries)
                    : _buildListView(context, displayEntries),
              ),
            ],
          );
        },
      ),
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
        final badgeColor = media.type == MediaType.anime
            ? colors.badgeVideo
            : media.type == MediaType.manga
            ? colors.badgeManga
            : colors.badgeNovel;
        final badgeText = media.type == MediaType.anime
            ? 'ANIME'
            : media.type == MediaType.manga
            ? 'MANGA'
            : 'NOVEL';

        return Stack(
          children: [
            MediaPoster(
              title: media.title,
              badgeText: badgeText,
              badgeColor: badgeColor,
              onTap: () {
                if (widget.onOpenDetails != null) {
                  widget.onOpenDetails!(context, media);
                } else {
                  widget.openMedia(context, media);
                }
              },
            ),
            Positioned(
              top: 4,
              right: 4,
              child: LibraryButton(
                repository: widget.repository,
                media: media,
                onChanged: _reloadLibrary,
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
              onChanged: _reloadLibrary,
            ),
          ),
        );
      },
    );
  }
}
