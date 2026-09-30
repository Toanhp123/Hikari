import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/home/widgets/continue_shelf.dart';
import 'package:hikari/features/home/widgets/hero_carousel.dart';

import 'package:hikari/features/shell/app_navigation_shell.dart';

enum HomeFilterType {
  all('All'),
  anime('Anime'),
  manga('Manga'),
  novel('Light Novels'),
  movies('Movies');

  const HomeFilterType(this.label);
  final String label;
}

/// The Home Dashboard screen with Hero banner, Continue Reading shelf,
/// category filters, and trending grid.
class HomePage extends StatefulWidget {
  const HomePage({
    super.key,
    required this.openMedia,
    this.library,
    this.featuredItems = const [],
    this.continueItems = const [],
    this.trendingItems = const [],
    this.onOpenDetails,
    this.onNavigateToSearch,
    this.onNavigateToLibrary,
    this.showLocalMediaPrompt = false,
    this.scanLocalMedia,
    this.onChooseFolder,
    this.openRemoteManga,
    this.openRemoteNovels,
  });

  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;
  final List<FeaturedHeroItem> featuredItems;
  final List<ContinueReadingItem> continueItems;
  final List<Media> trendingItems;
  final void Function(BuildContext, Media)? onOpenDetails;
  final VoidCallback? onNavigateToSearch;
  final VoidCallback? onNavigateToLibrary;
  final bool showLocalMediaPrompt;
  final Future<List<Media>?> Function()? scanLocalMedia;
  final VoidCallback? onChooseFolder;
  final VoidCallback? openRemoteManga;
  final VoidCallback? openRemoteNovels;

  static const _defaultFeaturedItems = [
    FeaturedHeroItem(
      media: Media(
        title: 'Aether Bound',
        type: MediaType.anime,
        source: SourceMediaRef(
          sourceId: SourceId('featured'),
          itemId: 'aether_bound',
        ),
      ),
      tagline: 'Immersive dynamic artwork with from a popular anime (e.g.) end watching series.',
      genres: ['Action', 'Fantasy', 'Sci-Fi'],
      primaryActionLabel: 'Play Episode 14',
      secondaryActionLabel: 'My List',
    ),
  ];

  static const _defaultContinueItems = [
    ContinueReadingItem(
      media: Media(
        title: 'Demon Slayer',
        type: MediaType.anime,
        source: SourceMediaRef(sourceId: SourceId('continue'), itemId: 'ds'),
      ),
      progress: 0.65,
      progressLabel: 'S2 Ep 8 · 14m left',
    ),
    ContinueReadingItem(
      media: Media(
        title: 'Berserk Vol 24',
        type: MediaType.manga,
        source: SourceMediaRef(
          sourceId: SourceId('continue'),
          itemId: 'berserk',
        ),
      ),
      progress: 0.40,
      progressLabel: 'Ch. 198 · 15m left',
    ),
    ContinueReadingItem(
      media: Media(
        title: 'Jujutsu Kaisen',
        type: MediaType.anime,
        source: SourceMediaRef(sourceId: SourceId('continue'), itemId: 'jjk'),
      ),
      progress: 0.82,
      progressLabel: 'S1 Ep 19 · 14m left',
    ),
    ContinueReadingItem(
      media: Media(
        title: 'Chainsaw Man',
        type: MediaType.anime,
        source: SourceMediaRef(sourceId: SourceId('continue'), itemId: 'csm'),
      ),
      progress: 0.25,
      progressLabel: 'S1 Ep 13 · 12m left',
    ),
  ];

  static const _defaultTrendingItems = [
    Media(
      title: 'Attack on Titan',
      type: MediaType.anime,
      source: SourceMediaRef(sourceId: SourceId('trending'), itemId: 'aot'),
    ),
    Media(
      title: 'Vagabond',
      type: MediaType.manga,
      source: SourceMediaRef(
        sourceId: SourceId('trending'),
        itemId: 'vagabond',
      ),
    ),
    Media(
      title: 'Perfect Blue',
      type: MediaType.anime,
      source: SourceMediaRef(
        sourceId: SourceId('trending'),
        itemId: 'perfect_blue',
      ),
    ),
    Media(
      title: 'Solo Leveling',
      type: MediaType.lightNovel,
      source: SourceMediaRef(
        sourceId: SourceId('trending'),
        itemId: 'solo_leveling',
      ),
    ),
    Media(
      title: 'Cyberpunk Edgerunners',
      type: MediaType.anime,
      source: SourceMediaRef(
        sourceId: SourceId('trending'),
        itemId: 'cyberpunk',
      ),
    ),
    Media(
      title: 'Chainsaw Man',
      type: MediaType.manga,
      source: SourceMediaRef(
        sourceId: SourceId('trending'),
        itemId: 'csm_manga',
      ),
    ),
  ];

  static const _showcaseMetadata =
      <String, ({String rating, String status, String subtitle})>{
        'Attack on Titan': (
          rating: '9.2',
          status: 'Watching',
          subtitle: '9.2 ★ | Anime',
        ),
        'Vagabond': (
          rating: '9.5',
          status: 'Watching',
          subtitle: '9.5 ★ | Manga',
        ),
        'Perfect Blue': (
          rating: '8.8',
          status: 'Plan to Watch',
          subtitle: '8.8 ★ | Movie',
        ),
        'Solo Leveling': (
          rating: '9.1',
          status: 'Watching',
          subtitle: '9.1 ★ | Light Novel',
        ),
        'Cyberpunk Edgerunners': (
          rating: '8.9',
          status: 'Plan to Watch',
          subtitle: '8.9 ★ | Anime',
        ),
        'Chainsaw Man': (
          rating: '9.0',
          status: 'Ongoing',
          subtitle: '9.0 ★ | Manga',
        ),
      };

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  HomeFilterType _selectedFilter = HomeFilterType.all;

  @override
  void initState() {
    super.initState();
    if (widget.showLocalMediaPrompt && widget.scanLocalMedia != null) {
      unawaited(widget.scanLocalMedia!());
    }
  }

  List<FeaturedHeroItem> get _effectiveFeatured =>
      widget.featuredItems.isNotEmpty
      ? widget.featuredItems
      : HomePage._defaultFeaturedItems;

  List<ContinueReadingItem> get _effectiveContinue =>
      widget.continueItems.isNotEmpty
      ? widget.continueItems
      : HomePage._defaultContinueItems;

  List<Media> get _filteredTrending {
    final base = widget.trendingItems.isNotEmpty
        ? widget.trendingItems
        : HomePage._defaultTrendingItems;
    if (_selectedFilter == HomeFilterType.all) return base;
    return base.where((item) {
      return switch (_selectedFilter) {
        HomeFilterType.all => true,
        HomeFilterType.anime => item.type == MediaType.anime,
        HomeFilterType.manga => item.type == MediaType.manga,
        HomeFilterType.novel => item.type == MediaType.lightNovel,
        HomeFilterType.movies =>
          item.type == MediaType.anime &&
              (item.title.contains('Movie') || item.title == 'Perfect Blue'),
      };
    }).toList();
  }

  void _navigateToSearch(BuildContext context) {
    if (widget.onNavigateToSearch != null) {
      widget.onNavigateToSearch!();
    } else {
      AppNavigationScope.of(context)?.selectTab(AppTab.search.index);
    }
  }

  void _navigateToLibrary(BuildContext context) {
    if (widget.onNavigateToLibrary != null) {
      widget.onNavigateToLibrary!();
    } else {
      AppNavigationScope.of(context)?.selectTab(AppTab.library.index);
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;

    return HikariScaffold(
      useSafeArea: true,
      body: CustomScrollView(
        physics: const BouncingScrollPhysics(),
        slivers: [
          // Top App Bar Header
          SliverToBoxAdapter(
            child: Padding(
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
                      // Primary Unified Search button
                      IconButton(
                        tooltip: 'Search',
                        onPressed: () => _navigateToSearch(context),
                        icon: Icon(
                          Icons.search_rounded,
                          color: colors.textPrimary,
                        ),
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
            ),
          ),

          // Local Media Quick Access Card
          if (widget.showLocalMediaPrompt)
            SliverToBoxAdapter(
              child: Padding(
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
                                'Import and browse anime, manga, and light novels from storage.',
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
              ),
            ),

          // 16:9 Hero Carousel
          if (_effectiveFeatured.isNotEmpty)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.only(bottom: HikariSpacing.lg),
                child: HeroCarousel(
                  items: _effectiveFeatured,
                  onOpenMedia: widget.openMedia,
                  onOpenDetails: widget.onOpenDetails,
                ),
              ),
            ),

          // Continue Watching & Reading Shelf
          if (_effectiveContinue.isNotEmpty) ...[
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.only(bottom: HikariSpacing.xl),
                child: ContinueShelf(
                  items: _effectiveContinue,
                  onOpenMedia: widget.openMedia,
                  onSeeAll: () => _navigateToLibrary(context),
                ),
              ),
            ),
          ],

          // Filter Pill Chips
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Discover',
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
                          padding: const EdgeInsets.only(
                            right: HikariSpacing.sm,
                          ),
                          child: HikariChip(
                            label: filter.label,
                            isSelected: _selectedFilter == filter,
                            onTap: () =>
                                setState(() => _selectedFilter = filter),
                          ),
                        );
                      }).toList(),
                    ),
                  ),
                ],
              ),
            ),
          ),

          const SliverToBoxAdapter(child: SizedBox(height: HikariSpacing.lg)),

          // Trending / Library Media Grid
          if (_filteredTrending.isNotEmpty)
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
              sliver: SliverGrid(
                gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                  crossAxisCount: context.responsiveGridColumns,
                  crossAxisSpacing: HikariSpacing.md,
                  mainAxisSpacing: HikariSpacing.md,
                  childAspectRatio: 2 / 3,
                ),
                delegate: SliverChildBuilderDelegate((context, index) {
                  final item = _filteredTrending[index];
                  final meta = HomePage._showcaseMetadata[item.title];
                  final badgeColor = item.type == MediaType.anime
                      ? colors.badgeVideo
                      : item.type == MediaType.manga
                      ? colors.badgeManga
                      : colors.badgeNovel;
                  final badgeText = item.type == MediaType.anime
                      ? 'ANIME'
                      : item.type == MediaType.manga
                      ? 'MANGA'
                      : 'NOVEL';

                  return MediaPoster(
                    title: item.title,
                    rating: meta?.rating,
                    statusText: meta?.status,
                    subtitle: meta?.subtitle,
                    badgeText: meta == null ? badgeText : null,
                    badgeColor: badgeColor,
                    onTap: () {
                      if (widget.onOpenDetails != null) {
                        widget.onOpenDetails!(context, item);
                      } else {
                        widget.openMedia(context, item);
                      }
                    },
                  );
                }, childCount: _filteredTrending.length),
              ),
            )
          else
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.all(HikariSpacing.xl),
                child: Center(
                  child: Text(
                    'No items in this category.',
                    style: TextStyle(color: colors.textMuted, fontSize: 13),
                  ),
                ),
              ),
            ),

          const SliverToBoxAdapter(child: SizedBox(height: HikariSpacing.xxl)),
        ],
      ),
    );
  }
}
