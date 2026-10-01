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
            if (entry.alternateTitles.isNotEmpty)
              Text(entry.alternateTitles.join(' · ')),
            if (entry.synonyms.isNotEmpty)
              Wrap(
                spacing: HikariSpacing.sm,
                children: [
                  for (final title in entry.synonyms) Chip(label: Text(title)),
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
            if (entry.averageScore != null) Text('Score ${entry.averageScore}'),
            if (entry.popularity != null)
              Text('Popularity ${entry.popularity}'),
            if (entry.format != null) Text('Format: ${entry.format!.name}'),
            if (entry.status != null)
              Text('Status: ${_statusLabel(entry.status!)}'),

            if (entry.year != null)
              Text('${entry.season?.name ?? ''} ${entry.year}'),
            if (entry.episodes != null) Text('${entry.episodes} episodes'),
            if (entry.chapters != null) Text('${entry.chapters} chapters'),
            if (entry.volumes != null) Text('${entry.volumes} volumes'),
            for (final studio in entry.studios) Text(studio),
            for (final staff in entry.staff) Text(staff),
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
