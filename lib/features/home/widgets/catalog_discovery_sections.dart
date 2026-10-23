import 'package:flutter/material.dart';
import 'package:hikari/core/ui/hikari_ui.dart';
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
    final current = discovery;
    if (error != null && current == null) {
      return HikariAsyncStateView(
        status: HikariAsyncStatus.fatalError,
        message: 'Catalog unavailable. Could not load discovery. Check your connection and try again.',
        onRetry: onRetry,
        child: const SizedBox.shrink(),
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
          HikariNoticeBanner(
            tone: HikariNoticeTone.warning,
            message: 'Could not refresh discovery. Showing the last available catalog.',
            action: TextButton(onPressed: onRetry, child: const Text('Retry')),
          ),
        if (current.warnings.isNotEmpty)
          HikariNoticeBanner(
            tone: HikariNoticeTone.warning,
            message: current.warnings.join(' '),
            action: TextButton(onPressed: onRetry, child: const Text('Retry')),
          ),
        if (!excludeFeatured && featured.isNotEmpty)
          HeroCarousel(entries: featured, openDetail: openDetail),
        for (final section in CatalogSection.values)
          if (section != CatalogSection.featured &&
              (sections[section] ?? []).isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: HikariSpacing.xl),
              child: HikariHorizontalShelf(
                itemCount: sections[section]!.length,
                itemKeyBuilder: (index) => ValueKey((
                  sections[section]![index].id,
                  sections[section]![index].type,
                )),
                header: HikariSectionHeader(title: _sectionTitle(section)),
                itemHeightBuilder: (context, width) =>
                    hikariCardExtent(context, width: width),
                itemBuilder: (context, index) {
                  final entry = sections[section]![index];
                  return HikariPosterCard(
                    title: entry.title,
                    artwork: HikariArtwork(
                      image: entry.coverUrl == null
                          ? null
                          : NetworkImage(entry.coverUrl!),
                    ),
                    badge: Text(mediaTypeBadgeLabel(entry.type)),
                    onTap: () => openDetail(entry),
                  );
                },
              ),
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

class _CatalogLoadingSkeleton extends StatelessWidget {
  const _CatalogLoadingSkeleton({this.excludeFeatured = false});
  final bool excludeFeatured;

  @override
  Widget build(BuildContext context) => Semantics(
    key: const ValueKey('catalog-loading-skeleton'),
    container: true,
    label: 'Loading catalog',
    child: ExcludeSemantics(
      child: Column(
        children: [
          if (!excludeFeatured) const HeroCarouselSkeleton(),
          for (var section = 0; section < 2; section++)
            Padding(
              padding: const EdgeInsets.only(top: HikariSpacing.xl),
              child: HikariHorizontalShelf(
                itemCount: 4,
                itemHeightBuilder: (context, width) =>
                    hikariCardExtent(context, width: width),
                itemBuilder: (_, _) =>
                    const HikariPosterCard(title: '', artwork: HikariArtwork()),
              ),
            ),
        ],
      ),
    ),
  );
}
