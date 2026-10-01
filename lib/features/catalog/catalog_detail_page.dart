import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/load_catalog_details.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

class CatalogDetailPage extends StatefulWidget {
  const CatalogDetailPage({
    super.key,
    required this.initial,
    required this.loadDetails,
    required this.openRelated,
    required this.openSourceSearch,
  });

  final CatalogMedia initial;
  final LoadCatalogDetails loadDetails;
  final void Function(CatalogMedia media) openRelated;
  final void Function(CatalogMedia media) openSourceSearch;

  @override
  State<CatalogDetailPage> createState() => _CatalogDetailPageState();
}

String _statusLabel(CatalogStatus status) => switch (status) {
  CatalogStatus.finished => 'finished',
  CatalogStatus.releasing => 'releasing',
  CatalogStatus.notYetReleased => 'not yet released',
  CatalogStatus.cancelled => 'cancelled',
  CatalogStatus.hiatus => 'hiatus',
};

class _CatalogDetailPageState extends State<CatalogDetailPage> {
  late Future<CatalogDetails?> _future = _loadDetails();

  Future<CatalogDetails?> _loadDetails() => Future<CatalogDetails?>.delayed(
    Duration.zero,
    () => widget.loadDetails.execute(widget.initial.id),
  );

  void _retry() {
    final future = _loadDetails();
    setState(() {
      _future = future;
    });
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Catalog details')),
    body: FutureBuilder<CatalogDetails?>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return AsyncStateView(
            status: AsyncViewStatus.error,
            contentBuilder: (_) => const SizedBox.shrink(),
            errorTitle: 'Details unavailable',
            errorMessage: 'Catalog details could not load.',
            onRetry: _retry,
          );
        }
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }
        final details = snapshot.data;
        if (details == null) {
          return AsyncStateView(
            status: AsyncViewStatus.empty,
            contentBuilder: (_) => const SizedBox.shrink(),
            emptyTitle: 'Catalog entry not found',
            emptyMessage: 'This entry is no longer available.',
            emptyAction: TextButton(
              onPressed: _retry,
              child: const Text('Try Again'),
            ),
          );
        }
        final media = details.media;
        return ListView(
          padding: const EdgeInsets.all(HikariSpacing.lg),
          children: [
            if (media.bannerUrl != null)
              Image.network(
                media.bannerUrl!,
                fit: BoxFit.cover,
                errorBuilder: (_, _, _) => const SizedBox.shrink(),
              ),
            if (media.coverUrl != null)
              Center(
                child: MediaPoster(
                  title: media.title,
                  imageUrl: media.coverUrl,
                  width: 180,
                ),
              ),
            Text(media.title, style: HikariTypography.titleLarge),
            if (media.alternateTitles.isNotEmpty)
              Text(media.alternateTitles.join(' · ')),
            if (media.synonyms.isNotEmpty)
              Wrap(
                spacing: HikariSpacing.sm,
                children: [
                  for (final title in media.synonyms) Chip(label: Text(title)),
                ],
              ),
            if (media.genres.isNotEmpty)
              Wrap(
                spacing: HikariSpacing.sm,
                children: [
                  for (final genre in media.genres) Chip(label: Text(genre)),
                ],
              ),
            if (details.description case final description?
                when description.isNotEmpty)
              Text(description),
            if (details.warnings.isNotEmpty)
              MaterialBanner(
                content: Text(details.warnings.join(' ')),
                actions: [
                  TextButton(onPressed: _retry, child: const Text('Retry')),
                ],
              ),
            if (media.averageScore != null) Text('Score ${media.averageScore}'),
            if (media.popularity != null)
              Text('Popularity ${media.popularity}'),
            if (media.format != null) Text('Format: ${media.format!.name}'),
            if (media.status != null)
              Text('Status: ${_statusLabel(media.status!)}'),

            if (media.year != null)
              Text('${media.season?.name ?? ''} ${media.year}'),
            if (media.episodes != null) Text('${media.episodes} episodes'),
            if (media.chapters != null) Text('${media.chapters} chapters'),
            if (media.volumes != null) Text('${media.volumes} volumes'),
            for (final studio in media.studios) Text(studio),
            for (final staff in media.staff) Text(staff),
            for (final relation in details.relations)
              ListTile(
                title: Text(relation.media.title),
                subtitle: Text(relation.relation.name),
                onTap: () => widget.openRelated(relation.media),
              ),
            const SizedBox(height: HikariSpacing.md),
            FilledButton(
              onPressed: () => widget.openSourceSearch(media),
              child: Text(media.type == MediaType.anime ? 'Watch' : 'Read'),
            ),
            const Text(
              'Search configured local and source catalogs. AniList does not provide playable streams or reading content.',
            ),
          ],
        );
      },
    ),
  );
}
