import 'package:flutter/material.dart';
import 'package:hikari/app/theme/design_system/design_system.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/features/home/widgets/hero_carousel.dart';

class CatalogDiscoverySections extends StatelessWidget {
  const CatalogDiscoverySections({
    super.key,
    required this.discovery,
    required this.error,
    required this.onRetry,
    required this.openDetail,
    this.excludeFeatured = false,
  });

  final CatalogDiscovery? discovery;
  final Object? error;
  final VoidCallback onRetry;
  final void Function(CatalogEntry entry) openDetail;
  final bool excludeFeatured;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final current = discovery;
    if (error != null && current == null) {
      return Padding(
        padding: const EdgeInsets.all(HikariSpace.content),
        child: AsyncStateView(
          status: AsyncViewStatus.error,
          contentBuilder: (_) => const SizedBox.shrink(),
          errorTitle: 'Catalog unavailable',
          errorMessage:
              'Could not load discovery. Check your connection and try again.',
          onRetry: onRetry,
        ),
      );
    }
    if (current == null) {
      return _CatalogLoadingSkeleton(excludeFeatured: excludeFeatured);
    }

    final sections = current.sections;
    final featured =
        sections[CatalogSection.featured] ?? const <CatalogEntry>[];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (error != null)
          MaterialBanner(
            content: const Text(
              'Could not refresh discovery. Showing the last available catalog.',
            ),
            actions: [
              TextButton(onPressed: onRetry, child: const Text('Retry')),
            ],
          ),
        if (current.warnings.isNotEmpty) ...[
          MaterialBanner(
            content: Text(current.warnings.join(' ')),
            actions: [
              TextButton(onPressed: onRetry, child: const Text('Retry')),
            ],
          ),
        ],
        if (!excludeFeatured && featured.isNotEmpty)
          HeroCarousel(entries: featured, openDetail: openDetail),
        for (final section in CatalogSection.values)
          if (section != CatalogSection.featured &&
              (sections[section] ?? []).isNotEmpty) ...[
            Padding(
              padding: const EdgeInsets.fromLTRB(
                HikariSpace.content,
                HikariSpace.section,
                HikariSpace.content,
                HikariSpace.inline,
              ),
              child: Semantics(
                headingLevel: 2,
                child: Text(
                  _sectionTitle(section),
                  style: theme.textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.15,
                  ),
                ),
              ),
            ),
            Builder(
              builder: (context) {
                final posterWidth = _catalogPosterWidth(context);
                return SizedBox(
                  height: posterWidth * 1.5 + 46.0,
                  child: ListView.separated(
                    padding: const EdgeInsets.symmetric(
                      horizontal: HikariSpace.content,
                    ),
                    scrollDirection: Axis.horizontal,
                    physics: const BouncingScrollPhysics(),
                    itemCount: sections[section]!.length,
                    separatorBuilder: (_, _) =>
                        const SizedBox(width: HikariSpace.compact),
                    itemBuilder: (context, index) {
                      final entry = sections[section]![index];
                      return SizedBox(
                        width: posterWidth,
                        child: Semantics(
                          button: true,
                          label: 'View details for ${entry.title}',
                          child: Material(
                            color: Colors.transparent,
                            child: MediaPoster(
                              title: entry.title,
                              imageUrl: entry.coverUrl,
                              badgeText: mediaTypeBadgeLabel(entry.type),
                              onTap: () => openDetail(entry),
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                );
              },
            ),
          ],
        if (sections.values.every((items) => items.isEmpty))
          const Padding(
            padding: EdgeInsets.all(HikariSpace.content),
            child: Text('No catalog results available.'),
          ),
      ],
    );
  }
}

String _sectionTitle(CatalogSection section) => switch (section) {
  CatalogSection.featured => 'Featured',
  CatalogSection.trending => 'Trending',
  CatalogSection.popularAnime => 'Popular Anime',
  CatalogSection.popularManga => 'Popular Manga',
  CatalogSection.popularLightNovels => 'Popular Light Novels',
  CatalogSection.seasonalAnime => 'Seasonal Anime',
};

double _catalogPosterWidth(BuildContext context) {
  final width = MediaQuery.sizeOf(context).width;
  final layout = HikariLayout.classify(width);
  return switch (layout) {
    HikariLayoutClass.compact => 132.0,
    HikariLayoutClass.medium => 144.0,
    HikariLayoutClass.expanded || HikariLayoutClass.wide => 156.0,
  };
}

class _CatalogLoadingSkeleton extends StatelessWidget {
  const _CatalogLoadingSkeleton({this.excludeFeatured = false});

  final bool excludeFeatured;

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final isCompact = HikariLayout.classify(width) == HikariLayoutClass.compact;
    final colors = Theme.of(context).colorScheme;
    final posterWidth = _catalogPosterWidth(context);

    return Semantics(
      key: const ValueKey('catalog-loading-skeleton'),
      container: true,
      label: 'Loading catalog',
      child: ExcludeSemantics(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (!excludeFeatured)
              Padding(
                padding: EdgeInsets.symmetric(
                  horizontal: isCompact
                      ? HikariSpace.content
                      : HikariSpace.section,
                ),
                child: AspectRatio(
                  aspectRatio: isCompact ? (16 / 10) : (16 / 7),
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      borderRadius: HikariShape.large,
                      gradient: LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [
                          colors.surfaceContainerHighest,
                          colors.surfaceContainer,
                          colors.surface,
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            for (var section = 0; section < 2; section++) ...[
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  HikariSpace.content,
                  HikariSpace.section,
                  HikariSpace.content,
                  HikariSpace.inline,
                ),
                child: Container(
                  width: section == 0 ? 112 : 148,
                  height: 18,
                  decoration: ShapeDecoration(
                    color: colors.surfaceContainerHighest,
                    shape: HikariShape.pill,
                  ),
                ),
              ),
              SizedBox(
                height: posterWidth * 1.5 + 46.0,
                child: ListView.separated(
                  padding: const EdgeInsets.symmetric(
                    horizontal: HikariSpace.content,
                  ),
                  scrollDirection: Axis.horizontal,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: 4,
                  separatorBuilder: (_, _) =>
                      const SizedBox(width: HikariSpace.compact),
                  itemBuilder: (_, _) => SizedBox(
                    width: posterWidth,
                    child: const MediaPoster(title: '', isLoading: true),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
