import 'package:flutter/material.dart';
import 'package:hikari/core/ui/hikari_ui.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/features/catalog/widgets/catalog_detail_sections.dart';

class CatalogDetailLoadingBody extends StatelessWidget {
  const CatalogDetailLoadingBody({super.key, required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Semantics(
      label: 'Loading catalog details for $title',
      child: ExcludeSemantics(
        child: Column(
          key: const Key('catalog-detail-loading'),
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              spacing: HikariSpacing.sm,
              runSpacing: HikariSpacing.sm,
              children: List.generate(
                4,
                (_) => _SkeletonBox(
                  width: context.isCompact ? 92 : 132,
                  height: 64,
                  color: colors.surfaceContainer,
                ),
              ),
            ),
            const SizedBox(height: HikariSpacing.xl),
            _SkeletonBox(
              width: 108,
              height: 18,
              color: colors.surfaceContainer,
            ),
            const SizedBox(height: HikariSpacing.md),
            _SkeletonBox(
              width: double.infinity,
              height: 96,
              color: colors.surfaceContainer,
            ),
            const SizedBox(height: HikariSpacing.xl),
            _SkeletonBox(width: 84, height: 18, color: colors.surfaceContainer),
            const SizedBox(height: HikariSpacing.md),
            HikariHorizontalShelf(
              itemCount: 4,
              itemExtent: 132,
              itemKeyBuilder: (index) => ValueKey(index),
              itemHeightBuilder: (context, width) =>
                  hikariCardExtent(context, width: width),
              itemBuilder: (_, _) =>
                  const HikariPosterCard(title: '', artwork: HikariArtwork()),
            ),
          ],
        ),
      ),
    );
  }
}

class CatalogDetailFallbackBody extends StatelessWidget {
  const CatalogDetailFallbackBody({
    super.key,
    required this.entry,
    required this.failed,
    required this.onRetry,
  });

  final CatalogEntry entry;
  final bool failed;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      HikariNoticeBanner(
        tone: HikariNoticeTone.warning,
        message: failed
            ? 'More details could not load. Check your connection and retry. You can still choose a source for ${entry.title}.'
            : 'Catalog entry unavailable. This metadata entry may have moved or been removed. You can still search configured sources by title.',
        action: TextButton(onPressed: onRetry, child: const Text('Retry')),
      ),
      if (entry.genres.isNotEmpty) ...[
        const SizedBox(height: HikariSpacing.xl),
        CatalogDetailGenresSection(genres: entry.genres),
      ],
      const SizedBox(height: HikariSpacing.xl),
      const CatalogDetailProvenance(),
    ],
  );
}

class CatalogDetailContent extends StatelessWidget {
  const CatalogDetailContent({
    super.key,
    required this.details,
    required this.refreshFailed,
    required this.descriptionExpanded,
    required this.onToggleDescription,
    required this.onRetry,
    required this.openRelated,
  });

  final CatalogEntryDetails details;
  final bool refreshFailed;
  final bool descriptionExpanded;
  final VoidCallback onToggleDescription;
  final VoidCallback onRetry;
  final ValueChanged<CatalogEntry> openRelated;

  @override
  Widget build(BuildContext context) {
    final main = _buildMain(context);
    final supporting = _buildSupporting();

    if (context.isExpanded) {
      return Row(
        key: const Key('catalog-detail-supporting-pane'),
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(flex: 2, child: main),
          const SizedBox(width: HikariSpacing.xxl),
          SizedBox(width: 360, child: supporting),
        ],
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (_hasQuickFacts(details)) ...[
          CatalogDetailQuickFacts(details: details),
          const SizedBox(height: HikariSpacing.xl),
        ],
        main,
        if (_hasMetadata(details)) ...[
          const SizedBox(height: HikariSpacing.xl),
          CatalogDetailMetadataSection(details: details),
        ],
        if (_hasCredits(details)) ...[
          const SizedBox(height: HikariSpacing.xl),
          CatalogDetailCreditsSection(details: details),
        ],
        const SizedBox(height: HikariSpacing.xl),
        const CatalogDetailProvenance(),
      ],
    );
  }

  Widget _buildMain(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      if (refreshFailed) ...[
        HikariNoticeBanner(
          tone: HikariNoticeTone.warning,
          message:
              'Refresh failed. Showing the last available catalog details.',
          action: TextButton(onPressed: onRetry, child: const Text('Retry')),
        ),
        const SizedBox(height: HikariSpacing.xl),
      ],
      if (details.warnings.isNotEmpty) ...[
        HikariNoticeBanner(
          tone: HikariNoticeTone.warning,
          message: details.warnings.join(' '),
          action: TextButton(onPressed: onRetry, child: const Text('Retry')),
        ),
        const SizedBox(height: HikariSpacing.xl),
      ],
      if (details.entry.genres.isNotEmpty) ...[
        CatalogDetailGenresSection(genres: details.entry.genres),
        const SizedBox(height: HikariSpacing.xl),
      ],
      if (details.description case final description?
          when description.trim().isNotEmpty) ...[
        const HikariSectionHeader(title: 'Synopsis'),
        Text(
          description,
          key: const Key('catalog-detail-description'),
          maxLines: descriptionExpanded ? null : 6,
          overflow: descriptionExpanded
              ? TextOverflow.visible
              : TextOverflow.fade,
          style: Theme.of(context).textTheme.bodyMedium!.copyWith(
            color: Theme.of(context).colorScheme.onSurfaceVariant,
            height: 1.6,
          ),
        ),
        if (description.length > 280) ...[
          const SizedBox(height: HikariSpacing.sm),
          TextButton(
            onPressed: onToggleDescription,
            child: Text(descriptionExpanded ? 'Show less' : 'Show more'),
          ),
        ],
        const SizedBox(height: HikariSpacing.xl),
      ],
      if (details.alternateTitles.isNotEmpty ||
          details.synonyms.isNotEmpty) ...[
        CatalogDetailTitlesSection(details: details),
        const SizedBox(height: HikariSpacing.xl),
      ],
      if (details.relations.isNotEmpty)
        CatalogDetailRelationsSection(
          relations: details.relations,
          openRelated: openRelated,
        ),
    ],
  );

  Widget _buildSupporting() => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      if (_hasQuickFacts(details)) ...[
        CatalogDetailQuickFacts(details: details),
        const SizedBox(height: HikariSpacing.xl),
      ],
      if (_hasMetadata(details)) ...[
        CatalogDetailMetadataSection(details: details),
        const SizedBox(height: HikariSpacing.xl),
      ],
      if (_hasCredits(details)) ...[
        CatalogDetailCreditsSection(details: details),
        const SizedBox(height: HikariSpacing.xl),
      ],
      const CatalogDetailProvenance(),
    ],
  );

  bool _hasQuickFacts(CatalogEntryDetails value) =>
      value.averageScore != null ||
      value.status != null ||
      value.format != null ||
      value.popularity != null;

  bool _hasMetadata(CatalogEntryDetails value) =>
      value.year != null ||
      value.episodes != null ||
      value.chapters != null ||
      value.volumes != null;

  bool _hasCredits(CatalogEntryDetails value) =>
      value.studios.isNotEmpty || value.staff.isNotEmpty;
}

class _SkeletonBox extends StatelessWidget {
  const _SkeletonBox({
    required this.width,
    required this.height,
    required this.color,
  });

  final double width;
  final double height;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    width: width,
    height: height,
    decoration: BoxDecoration(
      color: color,
      borderRadius: HikariRadius.borderMd,
    ),
  );
}
