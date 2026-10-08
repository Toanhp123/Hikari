import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_artwork_decode.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_detail_labels.dart';

class CatalogDetailHero extends StatelessWidget {
  const CatalogDetailHero({
    super.key,
    required this.entry,
    required this.details,
    required this.onPrimaryAction,
  });

  final CatalogEntry entry;
  final CatalogEntryDetails? details;
  final VoidCallback onPrimaryAction;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final isCompact = context.isCompact;
    final textScale = MediaQuery.textScalerOf(context).scale(1);
    final topPadding = textScale > 1.25 ? 44.0 : 72.0;
    final imageUrl = (entry.bannerUrl?.isNotEmpty ?? false)
        ? entry.bannerUrl
        : entry.coverUrl;
    final actionLabel = entry.type == MediaType.anime ? 'Watch' : 'Read';
    final metadata = <String>[
      catalogMediaTypeLabel(entry.type),
      if (details?.year != null) '${details!.year}',
      if (details?.format != null) catalogFormatLabel(details!.format!),
    ];

    return Stack(
      fit: StackFit.expand,
      children: [
        _HeroArtwork(imageUrl: imageUrl),
        DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [
                colors.surface.withValues(alpha: 0.04),
                colors.scrim.withValues(alpha: 0.6),
                colors.scrim.withValues(alpha: 0.8),
                colors.surface,
              ],
              stops: const [0, 0.44, 0.76, 1],
            ),
          ),
        ),
        DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.centerLeft,
              end: Alignment.centerRight,
              colors: [
                colors.scrim.withValues(alpha: 0.8),
                colors.scrim.withValues(alpha: 0.6),
                Colors.transparent,
              ],
              stops: const [0, 0.5, 0.9],
            ),
          ),
        ),
        SafeArea(
          bottom: false,
          child: Align(
            alignment: Alignment.bottomCenter,
            child: ConstrainedBox(
              constraints: const BoxConstraints(
                maxWidth: HikariBreakpoints.maxContentWidth,
              ),
              child: Padding(
                padding: EdgeInsets.fromLTRB(
                  HikariSpacing.lg,
                  topPadding,
                  HikariSpacing.lg,
                  HikariSpacing.xl,
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    SizedBox(
                      width: isCompact
                          ? (textScale > 1.3 ? 92.0 : 104.0)
                          : 132.0,
                      child: AspectRatio(
                        aspectRatio: 2 / 3,
                        child: _CoverArtwork(
                          imageUrl: entry.coverUrl,
                          label: entry.title,
                        ),
                      ),
                    ),
                    const SizedBox(width: HikariSpacing.lg),
                    Expanded(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _TypePill(type: entry.type),
                          const SizedBox(height: HikariSpacing.sm),
                          Text(
                            entry.title,
                            maxLines: isCompact ? (textScale > 1.3 ? 2 : 3) : 2,
                            overflow: TextOverflow.ellipsis,
                            style:
                                (isCompact
                                        ? (textScale > 1.25
                                              ? Theme.of(context)
                                                    .textTheme
                                                    .titleLarge!
                                              : Theme.of(context)
                                                    .textTheme
                                                    .headlineMedium!)
                                        : Theme.of(context)
                                              .textTheme
                                              .displayLarge!)
                                    .copyWith(
                                      color: Colors.white,
                                      fontWeight: FontWeight.w800,
                                      shadows: [
                                        Shadow(
                                          color: colors.scrim.withValues(
                                            alpha: 0.8,
                                          ),
                                          blurRadius: 12,
                                        ),
                                      ],
                                    ),
                          ),
                          if (metadata.isNotEmpty) ...[
                            const SizedBox(height: HikariSpacing.xs),
                            Text(
                              metadata.join(' · '),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: Theme.of(context).textTheme.labelMedium!
                                  .copyWith(
                                    color: Colors.white.withValues(alpha: 0.82),
                                    fontWeight: FontWeight.w600,
                                  ),
                            ),
                          ],
                          if (details?.averageScore != null) ...[
                            const SizedBox(height: HikariSpacing.xs),
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.star_rounded,
                                  size: 17,
                                  color: colors.primary,
                                ),
                                const SizedBox(width: HikariSpacing.xs),
                                Text(
                                  '${details!.averageScore}%',
                                  style: Theme.of(context).textTheme.labelLarge!
                                      .copyWith(color: Colors.white),
                                ),
                              ],
                            ),
                          ],
                          const SizedBox(height: HikariSpacing.md),
                          Semantics(
                            button: true,
                            label: '$actionLabel ${entry.title}',
                            child: FilledButton.icon(
                              onPressed: onPrimaryAction,
                              icon: Icon(
                                entry.type == MediaType.anime
                                    ? Icons.play_arrow_rounded
                                    : Icons.menu_book_rounded,
                                size: 19,
                              ),
                              label: Text(actionLabel),
                              style: FilledButton.styleFrom(
                                minimumSize: const Size(
                                  HikariSize.touchTarget,
                                  44,
                                ),
                                padding: const EdgeInsets.symmetric(
                                  horizontal: HikariSpacing.lg,
                                  vertical: HikariSpacing.sm,
                                ),
                                shape: const RoundedRectangleBorder(
                                  borderRadius: HikariRadius.borderMd,
                                ),
                              ),
                            ),
                          ),
                          const SizedBox(height: HikariSpacing.xs),
                          Text(
                            entry.type == MediaType.anime
                                ? 'Choose a source to continue'
                                : 'Choose where to read',
                            style: Theme.of(context).textTheme.bodySmall!
                                .copyWith(
                                  color: Colors.white.withValues(alpha: 0.72),
                                ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _HeroArtwork extends StatelessWidget {
  const _HeroArtwork({required this.imageUrl});

  final String? imageUrl;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final url = imageUrl;
    if (url == null || url.isEmpty) return _fallback(colors);
    return LayoutBuilder(
      builder: (context, constraints) => Image.network(
        url,
        fit: BoxFit.cover,
        alignment: Alignment.center,
        cacheWidth: mediaArtworkCacheWidth(context, constraints.maxWidth),
        excludeFromSemantics: true,
        errorBuilder: (_, _, _) => _fallback(colors),
        loadingBuilder: (_, child, progress) =>
            progress == null ? child : _fallback(colors),
      ),
    );
  }

  Widget _fallback(ColorScheme colors) => DecoratedBox(
    decoration: BoxDecoration(
      gradient: RadialGradient(
        center: const Alignment(0.4, -0.35),
        radius: 1.15,
        colors: [
          colors.primary.withValues(alpha: 0.34),
          colors.surfaceContainerHigh,
          colors.surfaceContainer,
          colors.surface,
        ],
        stops: const [0, 0.38, 0.72, 1],
      ),
    ),
  );
}

class _CoverArtwork extends StatelessWidget {
  const _CoverArtwork({required this.imageUrl, required this.label});

  final String? imageUrl;
  final String label;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final url = imageUrl;
    final artwork = url == null || url.isEmpty
        ? _fallback(colors)
        : LayoutBuilder(
            builder: (context, constraints) => Image.network(
              url,
              fit: BoxFit.cover,
              cacheWidth: mediaArtworkCacheWidth(context, constraints.maxWidth),
              excludeFromSemantics: true,
              errorBuilder: (_, _, _) => _fallback(colors),
              loadingBuilder: (_, image, progress) =>
                  progress == null ? image : _fallback(colors),
            ),
          );

    return Semantics(
      image: true,
      label: '$label cover',
      child: DecoratedBox(
        decoration: BoxDecoration(
          borderRadius: HikariRadius.borderMd,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.42),
              blurRadius: 18,
              offset: const Offset(0, 8),
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: HikariRadius.borderMd,
          child: Stack(
            fit: StackFit.expand,
            children: [
              artwork,
              Positioned.fill(
                child: IgnorePointer(
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      borderRadius: HikariRadius.borderMd,
                      border: Border.all(
                        color: colors.outlineVariant.withValues(alpha: 0.4),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _fallback(ColorScheme colors) => DecoratedBox(
    decoration: BoxDecoration(
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [
          colors.surfaceContainerHighest,
          colors.surfaceContainerHigh,
          colors.surfaceContainer,
        ],
      ),
    ),
    child: Center(
      child: Icon(
        Icons.movie_filter_rounded,
        size: 30,
        color: colors.primary.withValues(alpha: 0.8),
      ),
    ),
  );
}

class _TypePill extends StatelessWidget {
  const _TypePill({required this.type});

  final MediaType type;

  @override
  Widget build(BuildContext context) {
    final badge = mediaTypeBadgeColors(context, type);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: badge.background,
        borderRadius: HikariRadius.borderXl,
        border: Border.all(color: badge.foreground.withValues(alpha: 0.48)),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        child: Text(
          catalogMediaTypeLabel(type),
          style: Theme.of(context).textTheme.labelSmall!.copyWith(
            color: badge.foreground,
            fontWeight: FontWeight.w800,
            letterSpacing: 0.6,
          ),
        ),
      ),
    );
  }
}
