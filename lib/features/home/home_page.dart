import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/home/home_view_model.dart';
import 'package:hikari/features/home/widgets/continue_shelf.dart';
import 'package:hikari/features/home/widgets/hero_carousel.dart';

enum HomeFilterType {
  all('All'),
  anime('Anime'),
  manga('Manga'),
  novel('Light Novels');

  const HomeFilterType(this.label);
  final String label;
}

/// Home composes only real application data.
///
/// Explicit discover items take precedence when a future browse capability
/// supplies them. Otherwise the grid is backed by the user's live Library.
class HomePage extends StatefulWidget {
  const HomePage({
    super.key,
    required this.openMedia,
    this.library,
    this.featuredItems = const [],
    this.continueItems = const [],
    this.trendingItems = const [],
    this.onNavigateToSearch,
    this.onNavigateToLibrary,
    this.showLocalMediaPrompt = false,
    this.onChooseFolder,
    this.openRemoteManga,
    this.openRemoteNovels,
  });

  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;
  final List<FeaturedHeroItem> featuredItems;
  final List<ContinueReadingItem> continueItems;
  final List<Media> trendingItems;
  final VoidCallback? onNavigateToSearch;
  final VoidCallback? onNavigateToLibrary;
  final bool showLocalMediaPrompt;
  final VoidCallback? onChooseFolder;
  final VoidCallback? openRemoteManga;
  final VoidCallback? openRemoteNovels;

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  late final HomeViewModel _model = HomeViewModel(widget.library);
  HomeFilterType _selectedFilter = HomeFilterType.all;

  @override
  void dispose() {
    _model.dispose();
    super.dispose();
  }

  List<Media> _filteredItems(List<Media> items) {
    if (_selectedFilter == HomeFilterType.all) return items;
    return items
        .where((item) {
          return switch (_selectedFilter) {
            HomeFilterType.all => true,
            HomeFilterType.anime => item.type == MediaType.anime,
            HomeFilterType.manga => item.type == MediaType.manga,
            HomeFilterType.novel => item.type == MediaType.lightNovel,
          };
        })
        .toList(growable: false);
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _model,
      builder: (context, _) {
        final colors = context.hikariColors;
        final state = _model.state;
        final usesBrowseFeed = widget.trendingItems.isNotEmpty;
        final feed = usesBrowseFeed ? widget.trendingItems : state.libraryItems;
        final filteredFeed = _filteredItems(feed);

        return HikariScaffold(
          useSafeArea: true,
          body: CustomScrollView(
            physics: const BouncingScrollPhysics(),
            slivers: [
              SliverToBoxAdapter(child: _buildHeader(context, colors)),
              if (widget.showLocalMediaPrompt)
                SliverToBoxAdapter(child: _buildLocalMediaCard(colors)),
              if (widget.featuredItems.isNotEmpty)
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: HikariSpacing.lg),
                    child: HeroCarousel(
                      items: widget.featuredItems,
                      onOpenMedia: widget.openMedia,
                    ),
                  ),
                ),
              if (widget.continueItems.isNotEmpty)
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: HikariSpacing.xl),
                    child: ContinueShelf(
                      items: widget.continueItems,
                      onOpenMedia: widget.openMedia,
                      onSeeAll: widget.onNavigateToLibrary,
                    ),
                  ),
                ),
              if (feed.isNotEmpty) ...[
                SliverToBoxAdapter(
                  child: _buildFeedHeader(
                    colors,
                    usesBrowseFeed ? 'Discover' : 'Recently added',
                  ),
                ),
                const SliverToBoxAdapter(
                  child: SizedBox(height: HikariSpacing.lg),
                ),
                _buildFeedGrid(colors, filteredFeed),
              ] else if (state.loading)
                const SliverToBoxAdapter(
                  child: Padding(
                    padding: EdgeInsets.all(HikariSpacing.xl),
                    child: Center(child: CircularProgressIndicator()),
                  ),
                )
              else
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.all(HikariSpacing.xl),
                    child: Center(
                      child: Text(
                        state.error == null
                            ? 'Add media to your Library to populate Home.'
                            : 'Could not load recent Library items.',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: colors.textMuted, fontSize: 13),
                      ),
                    ),
                  ),
                ),
              const SliverToBoxAdapter(
                child: SizedBox(height: HikariSpacing.xxl),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildHeader(BuildContext context, HikariColors colors) {
    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: HikariSpacing.lg,
        vertical: HikariSpacing.sm,
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: [
              Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [colors.primary, colors.secondary],
                  ),
                  borderRadius: HikariRadius.borderSm,
                ),
                child: const Icon(
                  Icons.auto_awesome,
                  color: Colors.white,
                  size: 18,
                ),
              ),
              const SizedBox(width: HikariSpacing.sm),
              Text(
                'Hikari',
                style: HikariTypography.titleLarge.copyWith(
                  color: colors.textPrimary,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.5,
                ),
              ),
            ],
          ),
          Row(
            children: [
              IconButton(
                tooltip: 'Search',
                onPressed: widget.onNavigateToSearch,
                icon: Icon(Icons.search_rounded, color: colors.textPrimary),
              ),
              if (widget.openRemoteManga != null)
                IconButton(
                  tooltip: 'Search manga',
                  onPressed: widget.openRemoteManga,
                  icon: Icon(
                    Icons.auto_stories_outlined,
                    color: colors.textSecondary,
                  ),
                ),
              if (widget.openRemoteNovels != null)
                IconButton(
                  tooltip: 'Search novels',
                  onPressed: widget.openRemoteNovels,
                  icon: Icon(
                    Icons.chrome_reader_mode_outlined,
                    color: colors.textSecondary,
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildLocalMediaCard(HikariColors colors) {
    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: HikariSpacing.lg,
        vertical: HikariSpacing.xs,
      ),
      child: Material(
        color: colors.surfaceContainer,
        shape: RoundedRectangleBorder(
          borderRadius: HikariRadius.borderMd,
          side: BorderSide(color: colors.border),
        ),
        clipBehavior: Clip.antiAlias,
        child: Padding(
          padding: const EdgeInsets.all(HikariSpacing.md),
          child: Row(
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: colors.primary.withValues(alpha: 0.15),
                  borderRadius: HikariRadius.borderSm,
                ),
                child: Icon(
                  Icons.folder_open_rounded,
                  color: colors.primaryGlow,
                  size: 24,
                ),
              ),
              const SizedBox(width: HikariSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Local media',
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 14,
                        color: colors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Choose the persisted local-media root used by Hikari.',
                      style: TextStyle(
                        fontSize: 12,
                        color: colors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: HikariSpacing.sm),
              SizedBox(
                width: 125,
                child: HikariButton(
                  label: 'Choose folder',
                  size: HikariButtonSize.small,
                  variant: HikariButtonVariant.secondary,
                  onPressed: widget.onChooseFolder,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildFeedHeader(HikariColors colors, String title) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: HikariTypography.titleMedium.copyWith(
              color: colors.textPrimary,
            ),
          ),
          const SizedBox(height: HikariSpacing.sm),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: HomeFilterType.values.map((filter) {
                return Padding(
                  padding: const EdgeInsets.only(right: HikariSpacing.sm),
                  child: HikariChip(
                    label: filter.label,
                    isSelected: _selectedFilter == filter,
                    onTap: () => setState(() => _selectedFilter = filter),
                  ),
                );
              }).toList(),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFeedGrid(HikariColors colors, List<Media> items) {
    if (items.isEmpty) {
      return SliverToBoxAdapter(
        child: Padding(
          padding: const EdgeInsets.all(HikariSpacing.xl),
          child: Center(
            child: Text(
              'No items in this category.',
              style: TextStyle(color: colors.textMuted, fontSize: 13),
            ),
          ),
        ),
      );
    }

    return SliverPadding(
      padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
      sliver: SliverGrid(
        gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
          maxCrossAxisExtent: HikariBreakpoints.posterGridMaxExtent,
          crossAxisSpacing: HikariSpacing.md,
          mainAxisSpacing: HikariSpacing.md,
          childAspectRatio: 2 / 3,
        ),
        delegate: SliverChildBuilderDelegate((context, index) {
          final item = items[index];
          final badgeColor = switch (item.type) {
            MediaType.anime => colors.badgeVideo,
            MediaType.manga => colors.badgeManga,
            MediaType.lightNovel => colors.badgeNovel,
          };
          final badgeText = switch (item.type) {
            MediaType.anime => 'ANIME',
            MediaType.manga => 'MANGA',
            MediaType.lightNovel => 'NOVEL',
          };

          return MediaPoster(
            title: item.title,
            badgeText: badgeText,
            badgeColor: badgeColor,
            onTap: () => widget.openMedia(context, item),
          );
        }, childCount: items.length),
      ),
    );
  }
}
