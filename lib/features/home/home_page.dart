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

enum HomeFilterType {
  all('All'),
  anime('Anime'),
  manga('Manga'),
  novel('Light Novels');

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

  List<Media> get _filteredTrending {
    if (_selectedFilter == HomeFilterType.all) return widget.trendingItems;
    return widget.trendingItems.where((item) {
      return switch (_selectedFilter) {
        HomeFilterType.all => true,
        HomeFilterType.anime => item.type == MediaType.anime,
        HomeFilterType.manga => item.type == MediaType.manga,
        HomeFilterType.novel => item.type == MediaType.lightNovel,
      };
    }).toList();
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
                      if (widget.openRemoteManga != null)
                        IconButton(
                          tooltip: 'Search manga',
                          onPressed: widget.openRemoteManga,
                          icon: Icon(Icons.search, color: colors.textPrimary),
                        ),
                      if (widget.openRemoteNovels != null)
                        IconButton(
                          tooltip: 'Search novels',
                          onPressed: widget.openRemoteNovels,
                          icon: Icon(
                            Icons.menu_book,
                            color: colors.textPrimary,
                          ),
                        ),
                      if (widget.onNavigateToSearch != null)
                        IconButton(
                          tooltip: 'Search',
                          onPressed: widget.onNavigateToSearch,
                          icon: Icon(
                            Icons.search_rounded,
                            color: colors.textPrimary,
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
          if (widget.featuredItems.isNotEmpty)
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.only(bottom: HikariSpacing.lg),
                child: HeroCarousel(
                  items: widget.featuredItems,
                  onOpenMedia: widget.openMedia,
                  onOpenDetails: widget.onOpenDetails,
                ),
              ),
            ),

          // Continue Watching & Reading Shelf
          if (widget.continueItems.isNotEmpty) ...[
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
                    badgeText: badgeText,
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
