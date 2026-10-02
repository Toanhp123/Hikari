import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/features/home/widgets/catalog_discovery_sections.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/home/home_view_model.dart';
import 'package:hikari/features/home/widgets/continue_shelf.dart';

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
/// Catalog discovery supplies external browse metadata when configured.
/// The resume shelf and media grid are backed by live user progress and Library.
class HomePage extends StatefulWidget {
  const HomePage({
    super.key,
    required this.openMedia,
    this.library,
    this.progressRepository,
    this.discoverCatalog,
    this.openCatalogDetail,
    this.refreshRevision = 0,
    this.continueItems = const [],
    this.onNavigateToSearch,
    this.onNavigateToLibrary,
  });

  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;
  final ProgressRepository? progressRepository;
  final DiscoverCatalog? discoverCatalog;
  final void Function(BuildContext, CatalogEntry)? openCatalogDetail;
  final int refreshRevision;
  final List<ContinueReadingItem> continueItems;
  final VoidCallback? onNavigateToSearch;
  final VoidCallback? onNavigateToLibrary;

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  late final HomeViewModel _model = HomeViewModel(
    widget.library,
    progressRepository: widget.progressRepository,
  );
  HomeFilterType _selectedFilter = HomeFilterType.all;

  @override
  void didUpdateWidget(HomePage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.refreshRevision != widget.refreshRevision) {
      _model.reload();
    }
  }

  @override
  void dispose() {
    _model.dispose();
    super.dispose();
  }

  List<HomeFilterType> _availableFilters(List<Media> items) {
    final mediaTypes = items.map((item) => item.type).toSet();
    if (mediaTypes.length <= 1) return const [];

    return [
      HomeFilterType.all,
      if (mediaTypes.contains(MediaType.anime)) HomeFilterType.anime,
      if (mediaTypes.contains(MediaType.manga)) HomeFilterType.manga,
      if (mediaTypes.contains(MediaType.lightNovel)) HomeFilterType.novel,
    ];
  }

  List<Media> _filteredItems(
    List<Media> items,
    HomeFilterType effectiveFilter,
  ) {
    if (effectiveFilter == HomeFilterType.all) return items;
    return items
        .where((item) {
          return switch (effectiveFilter) {
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
        final feed = state.libraryItems;
        final availableFilters = _availableFilters(feed);
        final effectiveFilter = availableFilters.contains(_selectedFilter)
            ? _selectedFilter
            : HomeFilterType.all;
        final filteredFeed = _filteredItems(feed, effectiveFilter);
        final hasCatalogDiscovery =
            widget.discoverCatalog != null && widget.openCatalogDetail != null;

        return HikariScaffold(
          useSafeArea: true,
          body: CustomScrollView(
            physics: const BouncingScrollPhysics(),
            slivers: [
              _boundedSliverBox(_buildHeader(colors)),
              if (widget.continueItems.isNotEmpty ||
                  state.continueItems.isNotEmpty)
                _boundedSliverBox(
                  Padding(
                    padding: const EdgeInsets.only(bottom: HikariSpacing.lg),
                    child: ContinueShelf(
                      items: widget.continueItems.isNotEmpty
                          ? widget.continueItems
                          : state.continueItems,
                      onOpenMedia: widget.openMedia,
                      onSeeAll: widget.onNavigateToLibrary,
                    ),
                  ),
                ),
              if (hasCatalogDiscovery)
                _boundedSliverBox(
                  Padding(
                    padding: const EdgeInsets.only(bottom: HikariSpacing.xl),
                    child: CatalogDiscoverySections(
                      discover: widget.discoverCatalog!,
                      openDetail: (entry) =>
                          widget.openCatalogDetail!(context, entry),
                    ),
                  ),
                ),
              if (state.progressError != null)
                _boundedSliverBox(
                  const Padding(
                    padding: EdgeInsets.all(HikariSpacing.md),
                    child: Text('Some resume progress could not load.'),
                  ),
                ),
              if (state.error != null && feed.isNotEmpty)
                _boundedSliverBox(
                  Padding(
                    padding: const EdgeInsets.only(bottom: HikariSpacing.lg),
                    child: _buildStaleLibraryNotice(colors),
                  ),
                ),
              if (feed.isNotEmpty) ...[
                _boundedSliverBox(
                  _buildFeedHeader(
                    colors,
                    title: 'Recently added',
                    filters: availableFilters,
                    effectiveFilter: effectiveFilter,
                    onSectionAction: widget.onNavigateToLibrary,
                    sectionActionLabel: 'Library',
                  ),
                ),
                const SliverToBoxAdapter(
                  child: SizedBox(height: HikariSpacing.md),
                ),
                _buildFeedGrid(colors, filteredFeed),
              ] else if (state.loading && !hasCatalogDiscovery)
                _buildLoadingGrid()
              else if (state.error != null)
                _boundedSliverBox(_buildErrorState()),
              const SliverToBoxAdapter(
                child: SizedBox(height: HikariSpacing.xxxl),
              ),
            ],
          ),
        );
      },
    );
  }

  SliverToBoxAdapter _boundedSliverBox(Widget child) {
    return SliverToBoxAdapter(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: HikariBreakpoints.maxContentWidth,
          ),
          child: child,
        ),
      ),
    );
  }

  Widget _buildHeader(HikariColors colors) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        HikariSpacing.lg,
        HikariSpacing.xs,
        HikariSpacing.lg,
        HikariSpacing.sm,
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
                  boxShadow: [
                    BoxShadow(
                      color: colors.primary.withValues(alpha: 0.22),
                      blurRadius: 10,
                      offset: const Offset(0, 3),
                    ),
                  ],
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
                  letterSpacing: 0.2,
                ),
              ),
            ],
          ),
          HikariIconButton(
            tooltip: 'Search',
            onPressed: widget.onNavigateToSearch,
            icon: const Icon(Icons.search_rounded),
          ),
        ],
      ),
    );
  }

  Widget _buildStaleLibraryNotice(HikariColors colors) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
      child: Material(
        color: colors.warning.withValues(alpha: 0.08),
        shape: RoundedRectangleBorder(
          borderRadius: HikariRadius.borderMd,
          side: BorderSide(color: colors.warning.withValues(alpha: 0.28)),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: HikariSpacing.md,
            vertical: HikariSpacing.sm,
          ),
          child: Row(
            children: [
              Icon(Icons.sync_problem_rounded, size: 20, color: colors.warning),
              const SizedBox(width: HikariSpacing.sm),
              Expanded(
                child: Text(
                  'Library refresh failed. Showing the last available items.',
                  style: HikariTypography.bodySmall.copyWith(
                    color: colors.textSecondary,
                  ),
                ),
              ),
              TextButton(onPressed: _model.reload, child: const Text('Retry')),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildFeedHeader(
    HikariColors colors, {
    required String title,
    required List<HomeFilterType> filters,
    required HomeFilterType effectiveFilter,
    required VoidCallback? onSectionAction,
    required String sectionActionLabel,
  }) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  title,
                  style: HikariTypography.titleLarge.copyWith(
                    color: colors.textPrimary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              if (onSectionAction != null)
                InkWell(
                  onTap: onSectionAction,
                  borderRadius: HikariRadius.borderSm,
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 48),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: HikariSpacing.xs,
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            sectionActionLabel,
                            style: HikariTypography.labelMedium.copyWith(
                              color: colors.primaryGlow,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(width: 2),
                          Icon(
                            Icons.chevron_right_rounded,
                            size: 19,
                            color: colors.primaryGlow,
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
            ],
          ),
          if (filters.isNotEmpty) ...[
            const SizedBox(height: HikariSpacing.xs),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: filters
                    .map((filter) {
                      return Padding(
                        padding: const EdgeInsets.only(right: HikariSpacing.sm),
                        child: HikariChip(
                          label: filter.label,
                          isSelected: effectiveFilter == filter,
                          onTap: () => setState(() => _selectedFilter = filter),
                        ),
                      );
                    })
                    .toList(growable: false),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildFeedGrid(HikariColors colors, List<Media> items) {
    if (items.isEmpty) {
      return _boundedSliverBox(
        Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: HikariSpacing.lg,
            vertical: HikariSpacing.xl,
          ),
          child: Center(
            child: Text(
              'No items in this category.',
              style: HikariTypography.bodySmall.copyWith(
                color: colors.textMuted,
              ),
            ),
          ),
        ),
      );
    }

    return _boundedGrid(
      childCount: items.length,
      itemBuilder: (context, index) {
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
      },
    );
  }

  Widget _buildLoadingGrid() {
    return _boundedGrid(
      childCount: 6,
      itemBuilder: (context, index) =>
          const MediaPoster(title: '', isLoading: true),
    );
  }

  Widget _boundedGrid({
    required int childCount,
    required Widget Function(BuildContext, int) itemBuilder,
  }) {
    return SliverLayoutBuilder(
      builder: (context, constraints) {
        final extraWidth =
            constraints.crossAxisExtent > HikariBreakpoints.maxContentWidth
            ? constraints.crossAxisExtent - HikariBreakpoints.maxContentWidth
            : 0.0;
        final horizontalPadding = (extraWidth / 2) + HikariSpacing.lg;

        return SliverPadding(
          padding: EdgeInsets.symmetric(horizontal: horizontalPadding),
          sliver: SliverGrid(
            gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
              maxCrossAxisExtent: HikariBreakpoints.posterGridMaxExtent,
              crossAxisSpacing: HikariSpacing.md,
              mainAxisSpacing: HikariSpacing.md,
              childAspectRatio: 2 / 3,
            ),
            delegate: SliverChildBuilderDelegate(
              itemBuilder,
              childCount: childCount,
            ),
          ),
        );
      },
    );
  }

  Widget _buildErrorState() {
    return AsyncStateView(
      status: AsyncViewStatus.error,
      contentBuilder: (_) => const SizedBox.shrink(),
      errorTitle: 'Could not load your Library',
      errorMessage: 'Home could not refresh your saved media. Retry the Library load or keep exploring while the rest of Hikari remains available.',
      onRetry: _model.reload,
    );
  }
}
