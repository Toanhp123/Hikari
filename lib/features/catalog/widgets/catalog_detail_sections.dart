import 'package:flutter/material.dart';

import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/features/catalog/catalog_detail_labels.dart';

class CatalogDetailQuickFacts extends StatelessWidget {
  const CatalogDetailQuickFacts({super.key, required this.details});

  final CatalogEntryDetails details;

  @override
  Widget build(BuildContext context) {
    final facts = <({String label, String value, IconData icon})>[
      if (details.averageScore != null)
        (
          label: 'Score',
          value: '${details.averageScore}%',
          icon: Icons.star_rounded,
        ),
      if (details.status != null)
        (
          label: 'Status',
          value: catalogStatusLabel(details.status!),
          icon: Icons.schedule_rounded,
        ),
      if (details.format != null)
        (
          label: 'Format',
          value: catalogFormatLabel(details.format!),
          icon: Icons.movie_filter_outlined,
        ),
      if (details.popularity != null)
        (
          label: 'Popularity',
          value: compactCatalogNumber(details.popularity!),
          icon: Icons.trending_up_rounded,
        ),
    ];

    if (facts.isEmpty) return const SizedBox.shrink();
    return CatalogDetailSection(
      title: 'At a glance',
      child: Wrap(
        spacing: HikariSpacing.sm,
        runSpacing: HikariSpacing.sm,
        children: [
          for (final fact in facts)
            _FactTile(label: fact.label, value: fact.value, icon: fact.icon),
        ],
      ),
    );
  }
}

class CatalogDetailGenresSection extends StatelessWidget {
  const CatalogDetailGenresSection({super.key, required this.genres});

  final List<String> genres;

  @override
  Widget build(BuildContext context) => CatalogDetailSection(
    title: 'Genres',
    child: Wrap(
      spacing: HikariSpacing.sm,
      runSpacing: HikariSpacing.sm,
      children: [for (final genre in genres) _StaticTag(label: genre)],
    ),
  );
}

class CatalogDetailTitlesSection extends StatelessWidget {
  const CatalogDetailTitlesSection({super.key, required this.details});

  final CatalogEntryDetails details;

  @override
  Widget build(BuildContext context) => CatalogDetailSection(
    title: 'Titles',
    child: _InfoSurface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (details.alternateTitles.isNotEmpty)
            _LabeledValue(
              label: 'Alternate',
              value: details.alternateTitles.join(' · '),
            ),
          if (details.alternateTitles.isNotEmpty && details.synonyms.isNotEmpty)
            const SizedBox(height: HikariSpacing.md),
          if (details.synonyms.isNotEmpty)
            _LabeledValue(
              label: 'Also known as',
              value: details.synonyms.join(' · '),
            ),
        ],
      ),
    ),
  );
}

class CatalogDetailMetadataSection extends StatelessWidget {
  const CatalogDetailMetadataSection({super.key, required this.details});

  final CatalogEntryDetails details;

  @override
  Widget build(BuildContext context) {
    final rows = <({String label, String value})>[
      if (details.year != null)
        (
          label: 'Release',
          value: [
            if (details.season != null) catalogSeasonLabel(details.season!),
            '${details.year}',
          ].join(' '),
        ),
      if (details.episodes != null)
        (label: 'Episodes', value: '${details.episodes}'),
      if (details.chapters != null)
        (label: 'Chapters', value: '${details.chapters}'),
      if (details.volumes != null)
        (label: 'Volumes', value: '${details.volumes}'),
    ];

    return CatalogDetailSection(
      title: 'Details',
      child: _InfoSurface(
        child: Column(
          children: [
            for (var index = 0; index < rows.length; index++) ...[
              _MetadataRow(label: rows[index].label, value: rows[index].value),
              if (index != rows.length - 1) const Divider(height: 20),
            ],
          ],
        ),
      ),
    );
  }
}

class CatalogDetailCreditsSection extends StatelessWidget {
  const CatalogDetailCreditsSection({super.key, required this.details});

  final CatalogEntryDetails details;

  @override
  Widget build(BuildContext context) => CatalogDetailSection(
    title: 'Credits',
    child: _InfoSurface(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (details.studios.isNotEmpty)
            _LabeledValue(label: 'Studios', value: details.studios.join(' · ')),
          if (details.studios.isNotEmpty && details.staff.isNotEmpty)
            const SizedBox(height: HikariSpacing.md),
          if (details.staff.isNotEmpty)
            _LabeledValue(label: 'Staff', value: details.staff.join(' · ')),
        ],
      ),
    ),
  );
}

class CatalogDetailRelationsSection extends StatelessWidget {
  const CatalogDetailRelationsSection({
    super.key,
    required this.relations,
    required this.openRelated,
  });

  final List<CatalogRelatedEntry> relations;
  final ValueChanged<CatalogEntry> openRelated;

  @override
  Widget build(BuildContext context) {
    final width = context.isCompact ? 132.0 : 148.0;
    return CatalogDetailSection(
      title: 'Related',
      child: SizedBox(
        height: width * 1.5 + 56.0,
        child: ListView.separated(
          scrollDirection: Axis.horizontal,
          physics: const BouncingScrollPhysics(),
          itemCount: relations.length,
          separatorBuilder: (_, _) => const SizedBox(width: HikariSpacing.md),
          itemBuilder: (context, index) {
            final relation = relations[index];
            return SizedBox(
              width: width,
              child: Material(
                color: Colors.transparent,
                child: MediaPoster(
                  title: relation.entry.title,
                  imageUrl: relation.entry.coverUrl,
                  subtitle: catalogRelationLabel(relation.relation),
                  onTap: () => openRelated(relation.entry),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

class CatalogDetailNotice extends StatelessWidget {
  const CatalogDetailNotice({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
    required this.onRetry,
  });

  final IconData icon;
  final String title;
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final statusColors = context.hikariStatusColors;
    final onWarning = statusColors.onWarningContainer;

    return _InfoSurface(
      borderColor: onWarning.withValues(alpha: 0.32),
      backgroundColor: statusColors.warningContainer,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: onWarning, size: 22),
          const SizedBox(width: HikariSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: Theme.of(context).textTheme.titleSmall!
                      .copyWith(color: onWarning, fontWeight: FontWeight.w700),
                ),
                const SizedBox(height: 3),
                Text(
                  message,
                  style: Theme.of(context).textTheme.bodySmall!
                      .copyWith(color: onWarning.withValues(alpha: 0.9)),
                ),
              ],
            ),
          ),
          TextButton(
            onPressed: onRetry,
            style: TextButton.styleFrom(foregroundColor: onWarning),
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }
}

class CatalogDetailWarning extends StatelessWidget {
  const CatalogDetailWarning({
    super.key,
    required this.messages,
    required this.onRetry,
  });

  final List<String> messages;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final statusColors = context.hikariStatusColors;
    final onWarning = statusColors.onWarningContainer;

    return _InfoSurface(
      borderColor: onWarning.withValues(alpha: 0.28),
      backgroundColor: statusColors.warningContainer,
      child: Row(
        children: [
          Icon(Icons.info_outline_rounded, size: 20, color: onWarning),
          const SizedBox(width: HikariSpacing.sm),
          Expanded(
            child: Text(
              messages.join(' '),
              style: Theme.of(context).textTheme.bodySmall!
                  .copyWith(color: onWarning.withValues(alpha: 0.9)),
            ),
          ),
          TextButton(
            onPressed: onRetry,
            style: TextButton.styleFrom(foregroundColor: onWarning),
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }
}

class CatalogDetailProvenance extends StatelessWidget {
  const CatalogDetailProvenance({super.key});

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final statusColors = context.hikariStatusColors;

    return _InfoSurface(
      borderColor: colors.outlineVariant.withValues(alpha: 0.5),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            Icons.hub_outlined,
            size: 20,
            color: statusColors.onInfoContainer,
          ),
          const SizedBox(width: HikariSpacing.sm),
          Expanded(
            child: Text.rich(
              TextSpan(
                style: Theme.of(context).textTheme.bodySmall!
                    .copyWith(color: colors.onSurfaceVariant),
                children: [
                  TextSpan(
                    text: 'Metadata by AniList. ',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: colors.onSurface,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const TextSpan(
                    text: 'Watch or Read lets you choose from configured content sources.',
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class CatalogDetailSection extends StatelessWidget {
  const CatalogDetailSection({
    super.key,
    required this.title,
    required this.child,
  });

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(
        title,
        style: Theme.of(context).textTheme.titleMedium!.copyWith(
          color: Theme.of(context).colorScheme.onSurface,
          fontWeight: FontWeight.w700,
        ),
      ),
      const SizedBox(height: HikariSpacing.md),
      child,
    ],
  );
}

class _InfoSurface extends StatelessWidget {
  const _InfoSurface({
    required this.child,
    this.borderColor,
    this.backgroundColor,
  });

  final Widget child;
  final Color? borderColor;
  final Color? backgroundColor;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(HikariSpacing.lg),
      decoration: BoxDecoration(
        color: backgroundColor ?? colors.surfaceContainer,
        borderRadius: HikariRadius.borderLg,
        border: Border.all(
          color: borderColor ?? colors.outlineVariant.withValues(alpha: 0.6),
        ),
      ),
      child: child,
    );
  }
}

class _LabeledValue extends StatelessWidget {
  const _LabeledValue({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: Theme.of(context).textTheme.labelMedium!
              .copyWith(color: colors.onSurfaceVariant),
        ),
        const SizedBox(height: 3),
        Text(
          value,
          style: Theme.of(context).textTheme.bodyMedium!
              .copyWith(color: colors.onSurfaceVariant),
        ),
      ],
    );
  }
}

class _FactTile extends StatelessWidget {
  const _FactTile({
    required this.label,
    required this.value,
    required this.icon,
  });

  final String label;
  final String value;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final textScale = MediaQuery.textScalerOf(context).scale(1);
    final width =
        (context.isCompact ? 104.0 : 120.0) * (textScale > 1.25 ? 1.15 : 1.0);

    return Container(
      width: width,
      constraints: const BoxConstraints(minHeight: 72),
      padding: const EdgeInsets.all(HikariSpacing.md),
      decoration: BoxDecoration(
        color: colors.surfaceContainer,
        borderRadius: HikariRadius.borderMd,
        border: Border.all(color: colors.outlineVariant.withValues(alpha: 0.6)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 18, color: colors.primary.withValues(alpha: 0.8)),
          const SizedBox(height: HikariSpacing.sm),
          Text(
            value,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.titleSmall!
                .copyWith(color: colors.onSurface, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 2),
          Text(
            label,
            style: Theme.of(context).textTheme.bodySmall!
                .copyWith(color: colors.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}

class _StaticTag extends StatelessWidget {
  const _StaticTag({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: colors.surfaceContainer,
        borderRadius: HikariRadius.borderCapsule,
        border: Border.all(color: colors.outlineVariant.withValues(alpha: 0.6)),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
        child: Text(
          label,
          style: Theme.of(context).textTheme.labelMedium!.copyWith(
            color: colors.onSurfaceVariant,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }
}

class _MetadataRow extends StatelessWidget {
  const _MetadataRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 92,
          child: Text(
            label,
            style: Theme.of(context).textTheme.labelMedium!
                .copyWith(color: colors.onSurfaceVariant),
          ),
        ),
        Expanded(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: Theme.of(context).textTheme.bodySmall!.copyWith(
              color: colors.onSurfaceVariant,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
      ],
    );
  }
}
