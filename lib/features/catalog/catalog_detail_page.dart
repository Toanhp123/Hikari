import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

class CatalogDetailPage extends StatefulWidget {
  const CatalogDetailPage({
    super.key,
    required this.initialEntry,
    required this.loadDetails,
    required this.openRelated,
    required this.openSourceSearch,
  });

  final CatalogEntry initialEntry;
  final LoadCatalogEntryDetails loadDetails;
  final void Function(CatalogEntry entry) openRelated;
  final void Function(CatalogEntry entry) openSourceSearch;

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
  late Future<CatalogEntryDetails?> _future = _loadDetails();

  Future<CatalogEntryDetails?> _loadDetails() =>
      Future<CatalogEntryDetails?>.delayed(
        Duration.zero,
        () => widget.loadDetails.execute(widget.initialEntry.id),
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
    body: FutureBuilder<CatalogEntryDetails?>(
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
        final entry = details.entry;
        return ListView(
          padding: const EdgeInsets.all(HikariSpacing.lg),
          children: [
            if (entry.bannerUrl != null)
              Image.network(
                entry.bannerUrl!,
                fit: BoxFit.cover,
                errorBuilder: (_, _, _) => const SizedBox.shrink(),
              ),
            if (entry.coverUrl != null)
              Center(
                child: MediaPoster(
                  title: entry.title,
                  imageUrl: entry.coverUrl,
                  width: 180,
                ),
              ),
            Text(entry.title, style: HikariTypography.titleLarge),
            if (details.alternateTitles.isNotEmpty)
              Text(details.alternateTitles.join(' · ')),
            if (details.synonyms.isNotEmpty)
              Wrap(
                spacing: HikariSpacing.sm,
                children: [
                  for (final title in details.synonyms)
                    Chip(label: Text(title)),
                ],
              ),
            if (entry.genres.isNotEmpty)
              Wrap(
                spacing: HikariSpacing.sm,
                children: [
                  for (final genre in entry.genres) Chip(label: Text(genre)),
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
            if (details.averageScore != null)
              Text('Score ${details.averageScore}'),
            if (details.popularity != null)
              Text('Popularity ${details.popularity}'),
            if (details.format != null) Text('Format: ${details.format!.name}'),
            if (details.status != null)
              Text('Status: ${_statusLabel(details.status!)}'),

            if (details.year != null)
              Text('${details.season?.name ?? ''} ${details.year}'),
            if (details.episodes != null) Text('${details.episodes} episodes'),
            if (details.chapters != null) Text('${details.chapters} chapters'),
            if (details.volumes != null) Text('${details.volumes} volumes'),
            for (final studio in details.studios) Text(studio),
            for (final staff in details.staff) Text(staff),
            for (final relation in details.relations)
              ListTile(
                title: Text(relation.entry.title),
                subtitle: Text(relation.relation.name),
                onTap: () => widget.openRelated(relation.entry),
              ),
            const SizedBox(height: HikariSpacing.md),
            FilledButton(
              onPressed: () => widget.openSourceSearch(entry),
              child: Text(entry.type == MediaType.anime ? 'Watch' : 'Read'),
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
