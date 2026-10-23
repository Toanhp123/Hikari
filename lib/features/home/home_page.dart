import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/hikari_ui.dart';
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
    this.readArtwork,
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
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;

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
    // The outer navigation Scaffold exposes its bottom obstruction through
    // MediaQuery when extendBody is enabled. In rail mode this is just the
    // system safe area; never reserve a hardcoded bottom-bar height.
    final bottomObstruction = MediaQuery.paddingOf(context).bottom;
    return ListenableBuilder(
      listenable: _viewModel,
      builder: (context, _) {
        final state = _viewModel.state;
        final feed = state.libraryItems;
        final hasCatalogDiscovery =
            widget.discoverCatalog != null && widget.openCatalogDetail != null;
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
            HikariContentRegion.sliver(
              sliver: SliverMainAxisGroup(
                slivers: [
                  HomeHeader(onSearch: widget.onNavigateToSearch),
                  if (state.continueItems.isNotEmpty)
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.only(
                          bottom: HikariSpacing.xl,
                        ),
                        child: ContinueShelf(
                          items: state.continueItems,
                          artworkByMedia: state.artworkByMedia,
                          readArtwork: widget.readArtwork,
                          onOpenMedia: widget.openMedia,
                          onContinue: widget.onContinue,
                          onSeeAll: widget.onNavigateToLibrary,
                        ),
                      ),
                    ),
                  if (hasCatalogDiscovery) ...[
                    if (featured.isNotEmpty)
                      SliverToBoxAdapter(
                        child: Padding(
                          padding: const EdgeInsets.only(
                            top: HikariSpacing.md,
                            bottom: HikariSpacing.xl,
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
                      const SliverToBoxAdapter(
                        child: Padding(
                          padding: EdgeInsets.only(
                            top: HikariSpacing.md,
                            bottom: HikariSpacing.xl,
                          ),
                          child: HeroCarouselSkeleton(),
                        ),
                      ),
                  ],
                  if (hasCatalogDiscovery)
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.only(
                          bottom: HikariSpacing.xl,
                        ),
                        child: CatalogDiscoverySections(
                          discovery: state.catalogDiscovery,
                          error: state.catalogError,
                          onRetry: () => unawaited(
                            _viewModel.reloadCatalog(forceRefresh: true),
                          ),
                          openDetail: (entry) =>
                              widget.openCatalogDetail!(context, entry),
                          excludeFeatured: true,
                        ),
                      ),
                    ),
                  if (state.progressError != null)
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.only(
                          bottom: HikariSpacing.lg,
                        ),
                        child: HikariNoticeBanner(
                          tone: HikariNoticeTone.warning,
                          message: 'Some resume progress could not load. Your Library is still available.',
                          action: TextButton(
                            onPressed: _viewModel.reload,
                            child: const Text('Retry'),
                          ),
                        ),
                      ),
                    ),
                  if (state.error != null && state.hasLibrarySnapshot)
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.only(
                          bottom: HikariSpacing.lg,
                        ),
                        child: HikariNoticeBanner(
                          tone: HikariNoticeTone.warning,
                          message: 'Library refresh failed. Showing the last available items.',
                          action: TextButton(
                            onPressed: _viewModel.reload,
                            child: const Text('Retry'),
                          ),
                        ),
                      ),
                    ),
                  if (feed.isNotEmpty)
                    SliverToBoxAdapter(
                      child: Padding(
                        padding: const EdgeInsets.only(
                          bottom: HikariSpacing.xl,
                        ),
                        child: HomeRecentShelf(
                          items: _viewModel.visibleLibraryItems,
                          artworkByMedia: state.artworkByMedia,
                          readArtwork: widget.readArtwork,
                          openMedia: widget.openMedia,
                          filters: _viewModel.availableFilters,
                          effectiveFilter: _viewModel.effectiveFilter,
                          onSelectFilter: _viewModel.selectFilter,
                          onOpenLibrary: widget.onNavigateToLibrary,
                        ),
                      ),
                    )
                  else if (state.loading &&
                      !state.hasLibrarySnapshot &&
                      !hasCatalogDiscovery)
                    const SliverToBoxAdapter(
                      child: Padding(
                        padding: EdgeInsets.only(bottom: HikariSpacing.xl),
                        child: HomeRecentShelfSkeleton(),
                      ),
                    )
                  else if (state.error != null && !state.hasLibrarySnapshot)
                    SliverToBoxAdapter(
                      child: HomeLibraryError(onRetry: _viewModel.reload),
                    ),
                  SliverToBoxAdapter(
                    child: SizedBox(
                      key: const ValueKey('home-bottom-clearance'),
                      height: bottomObstruction + HikariSpacing.xl,
                    ),
                  ),
                ],
              ),
            ),
          ],
        );
        return HikariPageScaffold(
          useSafeArea: false,
          body: SafeArea(
            bottom: false,
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
  }
}
