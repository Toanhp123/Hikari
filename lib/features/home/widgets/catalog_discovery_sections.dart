import 'package:flutter/material.dart';

import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/home/widgets/hero_carousel.dart';

class CatalogDiscoverySections extends StatefulWidget {
  const CatalogDiscoverySections({
    super.key,
    required this.discover,
    required this.openDetail,
  });

  final DiscoverCatalog discover;
  final void Function(CatalogEntry entry) openDetail;

  @override
  State<CatalogDiscoverySections> createState() =>
      _CatalogDiscoverySectionsState();
}

class _CatalogDiscoverySectionsState extends State<CatalogDiscoverySections> {
  late Future<CatalogDiscovery> _future = widget.discover.execute();

  void _retry() {
    final future = widget.discover.execute();
    setState(() {
      _future = future;
    });
  }

  void _refresh() {
    final future = widget.discover.execute();
    setState(() {
      _future = future;
    });
  }

  @override
  void didUpdateWidget(CatalogDiscoverySections oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.discover != widget.discover) _refresh();
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<CatalogDiscovery>(
    future: _future,
    builder: (context, snapshot) {
      if (snapshot.hasError) {
        return Padding(
          padding: const EdgeInsets.all(HikariSpacing.lg),
          child: AsyncStateView(
            status: AsyncViewStatus.error,
            contentBuilder: (_) => const SizedBox.shrink(),
            errorTitle: 'Catalog unavailable',
            errorMessage: 'Could not load discovery. Check your connection and try again.',
            onRetry: _retry,
          ),
        );
      }
      if (!snapshot.hasData) {
        return const _CatalogLoadingSkeleton();
      }
      final discovery = snapshot.data!;
      final sections = discovery.sections;
      final featured =
          sections[CatalogSection.featured] ?? const <CatalogEntry>[];
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (discovery.warnings.isNotEmpty) ...[
            MaterialBanner(
              content: Text(discovery.warnings.join(' ')),
              actions: [
                TextButton(onPressed: _retry, child: const Text('Retry')),
              ],
            ),
          ],
          if (featured.isNotEmpty)
            HeroCarousel(entries: featured, openDetail: widget.openDetail),
          for (final section in CatalogSection.values)
            if (section != CatalogSection.featured &&
                (sections[section] ?? []).isNotEmpty) ...[
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  HikariSpacing.lg,
                  HikariSpacing.xl,
                  HikariSpacing.lg,
                  HikariSpacing.sm,
                ),
                child: Text(
                  _sectionTitle(section),
                  style: HikariTypography.titleLarge,
                ),
              ),
              Builder(
                builder: (context) {
                  final posterWidth = _catalogPosterWidth(context);
                  return SizedBox(
                    height: posterWidth * 1.5,
                    child: ListView.separated(
                      padding: const EdgeInsets.symmetric(
                        horizontal: HikariSpacing.lg,
                      ),
                      scrollDirection: Axis.horizontal,
                      physics: const BouncingScrollPhysics(),
                      itemCount: sections[section]!.length,
                      separatorBuilder: (_, _) =>
                          const SizedBox(width: HikariSpacing.md),
                      itemBuilder: (context, index) {
                        final entry = sections[section]![index];
                        final typeLabel = _typeLabel(entry.type);
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
                                badgeText: typeLabel.toUpperCase(),
                                onTap: () => widget.openDetail(entry),
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
              padding: EdgeInsets.all(HikariSpacing.lg),
              child: Text('No catalog results available.'),
            ),
        ],
      );
    },
  );

  String _typeLabel(MediaType type) => switch (type) {
    MediaType.anime => 'Anime',
    MediaType.manga => 'Manga',
    MediaType.lightNovel => 'Novel',
  };

  String _sectionTitle(CatalogSection section) => switch (section) {
    CatalogSection.featured => 'Featured',
    CatalogSection.trending => 'Trending',
    CatalogSection.popularAnime => 'Popular Anime',
    CatalogSection.popularManga => 'Popular Manga',
    CatalogSection.popularLightNovels => 'Popular Light Novels',
    CatalogSection.seasonalAnime => 'Seasonal Anime',
  };
}

double _catalogPosterWidth(BuildContext context) => context.isCompact
    ? 132.0
    : context.isMedium
    ? 144.0
    : 156.0;

class _CatalogLoadingSkeleton extends StatelessWidget {
  const _CatalogLoadingSkeleton();

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final posterWidth = _catalogPosterWidth(context);

    return Semantics(
      key: const ValueKey('catalog-loading-skeleton'),
      container: true,
      label: 'Loading catalog',
      child: ExcludeSemantics(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: EdgeInsets.symmetric(
                horizontal: context.isCompact ? 0 : HikariSpacing.lg,
              ),
              child: AspectRatio(
                aspectRatio: context.isCompact ? (16 / 10) : (16 / 7),
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    borderRadius: context.isCompact
                        ? BorderRadius.zero
                        : HikariRadius.borderLg,
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [
                        colors.surfaceElevated,
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
                  HikariSpacing.lg,
                  HikariSpacing.xl,
                  HikariSpacing.lg,
                  HikariSpacing.sm,
                ),
                child: Container(
                  width: section == 0 ? 112 : 148,
                  height: 18,
                  decoration: BoxDecoration(
                    color: colors.surfaceElevated,
                    borderRadius: HikariRadius.borderCapsule,
                  ),
                ),
              ),
              SizedBox(
                height: posterWidth * 1.5,
                child: ListView.separated(
                  padding: const EdgeInsets.symmetric(
                    horizontal: HikariSpacing.lg,
                  ),
                  scrollDirection: Axis.horizontal,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: 4,
                  separatorBuilder: (_, _) =>
                      const SizedBox(width: HikariSpacing.md),
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
