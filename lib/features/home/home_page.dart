import 'package:flutter/material.dart';
import 'package:hikari/app/theme/design_system/design_system.dart';
import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/application/progress/load_continue_reading.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/continue_reading_item.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/home/home_view_model.dart';
import 'package:hikari/features/home/widgets/catalog_discovery_sections.dart';
import 'package:hikari/features/home/widgets/continue_shelf.dart';
import 'package:hikari/features/home/widgets/hero_carousel.dart';
import 'package:hikari/features/home/widgets/home_header.dart';
import 'package:hikari/features/home/widgets/home_library_section.dart';
import 'package:hikari/features/home/widgets/home_warning_notice.dart';

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
    this.loadContinueReading,
    this.onContinue,
    this.discoverCatalog,
    this.openCatalogDetail,
    this.refreshRevision = 0,
    this.onNavigateToSearch,
    this.onNavigateToLibrary,
  });

  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;
  final ProgressRepository? progressRepository;
  final LoadContinueReading? loadContinueReading;
  final void Function(BuildContext, ContinueReadingItem)? onContinue;
  final DiscoverCatalog? discoverCatalog;
  final void Function(BuildContext, CatalogEntry)? openCatalogDetail;
  final int refreshRevision;
  final VoidCallback? onNavigateToSearch;
  final VoidCallback? onNavigateToLibrary;

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  late final HomeViewModel _viewModel = HomeViewModel(
    widget.library,
    continueReading: widget.loadContinueReading,
    progressRepository: widget.progressRepository,
    discoverCatalog: widget.discoverCatalog,
  );

  @override
  void didUpdateWidget(HomePage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.refreshRevision != widget.refreshRevision) {
      _viewModel.reload();
    }
  }

  @override
  void dispose() {
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Theme(
      data: HikariDesignTheme.dark(),
      child: Builder(
        builder: (context) {
          return ListenableBuilder(
            listenable: _viewModel,
            builder: (context, _) {
              final state = _viewModel.state;
              final feed = state.libraryItems;
              final hasCatalogDiscovery =
                  widget.discoverCatalog != null &&
                  widget.openCatalogDetail != null;
              final hasRefreshableSource =
                  widget.library != null || hasCatalogDiscovery;
              final featured =
                  state.catalogDiscovery?.sections[CatalogSection.featured] ??
                  const <CatalogEntry>[];

              final scrollView = CustomScrollView(
                physics: const AlwaysScrollableScrollPhysics(
                  parent: BouncingScrollPhysics(),
                ),
                slivers: [
                  HomeBoundedSliverBox(
                    child: HomeHeader(onSearch: widget.onNavigateToSearch),
                  ),
                  if (hasCatalogDiscovery) ...[
                    if (featured.isNotEmpty)
                      HomeBoundedSliverBox(
                        child: Padding(
                          padding: const EdgeInsets.only(
                            bottom: HikariSpace.section,
                          ),
                          child: HeroCarousel(
                            entries: featured,
                            openDetail: (entry) =>
                                widget.openCatalogDetail!(context, entry),
                          ),
                        ),
                      )
                    else if (state.catalogDiscovery == null &&
                        state.catalogError == null)
                      const HomeBoundedSliverBox(
                        child: Padding(
                          padding: EdgeInsets.only(bottom: HikariSpace.section),
                          child: HeroCarouselSkeleton(),
                        ),
                      ),
                  ],
                  if (state.continueItems.isNotEmpty)
                    HomeBoundedSliverBox(
                      child: Padding(
                        padding: const EdgeInsets.only(
                          bottom: HikariSpace.section,
                        ),
                        child: ContinueShelf(
                          items: state.continueItems,
                          onOpenMedia: widget.openMedia,
                          onContinue: widget.onContinue,
                          onSeeAll: widget.onNavigateToLibrary,
                        ),
                      ),
                    ),
                  if (hasCatalogDiscovery)
                    HomeBoundedSliverBox(
                      child: Padding(
                        padding: const EdgeInsets.only(
                          bottom: HikariSpace.section,
                        ),
                        child: CatalogDiscoverySections(
                          discovery: state.catalogDiscovery,
                          error: state.catalogError,
                          onRetry: _viewModel.reloadCatalog,
                          openDetail: (entry) =>
                              widget.openCatalogDetail!(context, entry),
                          excludeFeatured: true,
                        ),
                      ),
                    ),
                  if (state.progressError != null)
                    HomeBoundedSliverBox(
                      child: Padding(
                        padding: const EdgeInsets.only(
                          bottom: HikariSpace.content,
                        ),
                        child: HomeWarningNotice(
                          icon: Icons.history_rounded,
                          message: 'Some resume progress could not load. Your Library is still available.',
                          onRetry: _viewModel.reload,
                        ),
                      ),
                    ),
                  if (state.error != null && state.hasLibrarySnapshot)
                    HomeBoundedSliverBox(
                      child: Padding(
                        padding: const EdgeInsets.only(
                          bottom: HikariSpace.content,
                        ),
                        child: HomeWarningNotice(
                          icon: Icons.sync_problem_rounded,
                          message: 'Library refresh failed. Showing the last available items.',
                          onRetry: _viewModel.reload,
                        ),
                      ),
                    ),
                  if (feed.isNotEmpty) ...[
                    HomeBoundedSliverBox(
                      child: HomeFeedHeader(
                        filters: _viewModel.availableFilters,
                        effectiveFilter: _viewModel.effectiveFilter,
                        onSelectFilter: _viewModel.selectFilter,
                        onOpenLibrary: widget.onNavigateToLibrary,
                      ),
                    ),
                    const SliverToBoxAdapter(
                      child: SizedBox(height: HikariSpace.compact),
                    ),
                    HomeMediaGridSliver(
                      items: _viewModel.visibleLibraryItems,
                      openMedia: widget.openMedia,
                    ),
                  ] else if (state.loading &&
                      !state.hasLibrarySnapshot &&
                      !hasCatalogDiscovery)
                    const HomeLoadingGridSliver()
                  else if (state.error != null && !state.hasLibrarySnapshot)
                    HomeBoundedSliverBox(
                      child: HomeLibraryError(onRetry: _viewModel.reload),
                    ),
                  const SliverToBoxAdapter(
                    child: SizedBox(height: HikariSpace.spacious),
                  ),
                ],
              );
              return Scaffold(
                backgroundColor: Theme.of(context).colorScheme.surface,
                body: SafeArea(
                  child: hasRefreshableSource
                      ? RefreshIndicator.adaptive(
                          onRefresh: _viewModel.refresh,
                          child: scrollView,
                        )
                      : scrollView,
                ),
              );
            },
          );
        },
      ),
    );
  }
}
