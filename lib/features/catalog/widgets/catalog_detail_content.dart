import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/features/catalog/widgets/catalog_detail_sections.dart';

class CatalogDetailLoadingBody extends StatelessWidget {
  const CatalogDetailLoadingBody({super.key, required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
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
            SizedBox(
              height: 210,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: 4,
                separatorBuilder: (_, _) =>
                    const SizedBox(width: HikariSpacing.md),
                itemBuilder: (_, _) => const SizedBox(
                  width: 132,
                  child: MediaPoster(title: '', isLoading: true),
                ),
              ),
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
      CatalogDetailNotice(
        icon: failed ? Icons.cloud_off_rounded : Icons.info_outline_rounded,
        title: failed
            ? 'More details could not load'
            : 'Catalog entry unavailable',
        message: failed
            ? 'Check your connection and retry. You can still choose a source for ${entry.title}.'
            : 'This metadata entry may have moved or been removed. You can still search configured sources by title.',
        onRetry: onRetry,
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
        CatalogDetailNotice(
          icon: Icons.cloud_off_rounded,
          title: 'Refresh failed',
          message: 'Showing the last available catalog details.',
          onRetry: onRetry,
        ),
        const SizedBox(height: HikariSpacing.xl),
      ],
      if (details.warnings.isNotEmpty) ...[
        CatalogDetailWarning(messages: details.warnings, onRetry: onRetry),
        const SizedBox(height: HikariSpacing.xl),
      ],
      if (details.entry.genres.isNotEmpty) ...[
        CatalogDetailGenresSection(genres: details.entry.genres),
        const SizedBox(height: HikariSpacing.xl),
      ],
      if (details.description case final description?
          when description.trim().isNotEmpty) ...[
        CatalogDetailSection(
          title: 'Synopsis',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                description,
                key: const Key('catalog-detail-description'),
                maxLines: descriptionExpanded ? null : 6,
                overflow: descriptionExpanded
                    ? TextOverflow.visible
                    : TextOverflow.fade,
                style: HikariTypography.bodyMedium.copyWith(
                  color: context.hikariColors.textSecondary,
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
            ],
          ),
        ),
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
