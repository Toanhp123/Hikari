import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/catalog/catalog.dart';

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
            errorMessage: 'Catalog discovery could not load.',
            onRetry: _retry,
          ),
        );
      }
      if (!snapshot.hasData) {
        return const Padding(
          padding: EdgeInsets.all(HikariSpacing.lg),
          child: LinearProgressIndicator(),
        );
      }
      final discovery = snapshot.data!;
      final sections = discovery.sections;
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
          for (final section in CatalogSection.values)
            if ((sections[section] ?? []).isNotEmpty) ...[
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  HikariSpacing.lg,
                  HikariSpacing.md,
                  HikariSpacing.lg,
                  HikariSpacing.sm,
                ),
                child: Text(
                  _sectionTitle(section),
                  style: HikariTypography.titleMedium,
                ),
              ),
              SizedBox(
                height: 228,
                child: ListView.separated(
                  padding: const EdgeInsets.symmetric(
                    horizontal: HikariSpacing.lg,
                  ),
                  scrollDirection: Axis.horizontal,
                  itemCount: sections[section]!.length,
                  separatorBuilder: (_, _) =>
                      const SizedBox(width: HikariSpacing.md),
                  itemBuilder: (context, index) {
                    final entry = sections[section]![index];
                    return SizedBox(
                      width: 148,
                      child: Material(
                        color: Colors.transparent,
                        child: MediaPoster(
                          title: entry.title,
                          imageUrl: entry.coverUrl,
                          subtitle: entry.type.name,
                          badgeText: entry.type.name.toUpperCase(),
                          onTap: () => widget.openDetail(entry),
                        ),
                      ),
                    );
                  },
                ),
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

  String _sectionTitle(CatalogSection section) => switch (section) {
    CatalogSection.featured => 'Featured',
    CatalogSection.trending => 'Trending',
    CatalogSection.popularAnime => 'Popular Anime',
    CatalogSection.popularManga => 'Popular Manga',
    CatalogSection.popularLightNovels => 'Popular Light Novels',
    CatalogSection.seasonalAnime => 'Seasonal Anime',
  };
}
