import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/home/home_view_model.dart';
import 'package:hikari/features/home/widgets/catalog_discovery_sections.dart';
import 'package:hikari/features/home/widgets/continue_shelf.dart';
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
    this.discoverCatalog,
    this.openCatalogDetail,
    this.refreshRevision = 0,
    this.onNavigateToSearch,
    this.onNavigateToLibrary,
  });

  final void Function(BuildContext, Media) openMedia;
  final LibraryRepository? library;
  final ProgressRepository? progressRepository;
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
    return ListenableBuilder(
      listenable: _viewModel,
      builder: (context, _) {
        final state = _viewModel.state;
        final feed = state.libraryItems;
        final hasCatalogDiscovery =
            widget.discoverCatalog != null && widget.openCatalogDetail != null;
        final hasRefreshableSource =
            widget.library != null || hasCatalogDiscovery;

        final scrollView = CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(
            parent: BouncingScrollPhysics(),
          ),
          slivers: [
            HomeBoundedSliverBox(
              child: HomeHeader(onSearch: widget.onNavigateToSearch),
            ),
            if (state.continueItems.isNotEmpty)
              HomeBoundedSliverBox(
                child: Padding(
                  padding: const EdgeInsets.only(bottom: HikariSpacing.lg),
                  child: ContinueShelf(
                    items: state.continueItems,
                    onOpenMedia: widget.openMedia,
                    onSeeAll: widget.onNavigateToLibrary,
                  ),
                ),
              ),
            if (hasCatalogDiscovery)
              HomeBoundedSliverBox(
                child: Padding(
                  padding: const EdgeInsets.only(bottom: HikariSpacing.xl),
                  child: CatalogDiscoverySections(
                    discovery: state.catalogDiscovery,
                    error: state.catalogError,
                    onRetry: _viewModel.reloadCatalog,
                    openDetail: (entry) =>
                        widget.openCatalogDetail!(context, entry),
                  ),
                ),
              ),
            if (state.progressError != null)
              HomeBoundedSliverBox(
                child: Padding(
                  padding: const EdgeInsets.only(bottom: HikariSpacing.lg),
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
                  padding: const EdgeInsets.only(bottom: HikariSpacing.lg),
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
                child: SizedBox(height: HikariSpacing.md),
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
              child: SizedBox(height: HikariSpacing.xxxl),
            ),
          ],
        );
        return HikariScaffold(
          useSafeArea: true,
          body: hasRefreshableSource
              ? RefreshIndicator.adaptive(
                  onRefresh: _viewModel.refresh,
                  child: scrollView,
                )
              : scrollView,
        );
      },
    );
  }
}
