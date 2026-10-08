import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_artwork_decode.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/catalog/catalog.dart';

class HeroCarouselSlide extends StatelessWidget {
  const HeroCarouselSlide({
    super.key,
    required this.entry,
    required this.position,
    required this.total,
    required this.isCompact,
    required this.openDetail,
  });

  final CatalogEntry entry;
  final int position;
  final int total;
  final bool isCompact;
  final ValueChanged<CatalogEntry> openDetail;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final typeLabel = mediaTypeLabel(entry.type);
    final metadata = [typeLabel, ...entry.genres.take(2)].join(' · ');
    final artworkUrl = entry.bannerUrl?.isNotEmpty == true
        ? entry.bannerUrl
        : entry.coverUrl;

    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: 'Item $position of $total',
      child: Stack(
        fit: StackFit.expand,
        children: [
          _buildFallbackArtwork(colors),
          if (artworkUrl != null && artworkUrl.isNotEmpty)
            LayoutBuilder(
              builder: (context, constraints) => Image.network(
                artworkUrl,
                fit: BoxFit.cover,
                alignment: Alignment.center,
                filterQuality: FilterQuality.medium,
                cacheWidth: mediaArtworkCacheWidth(
                  context,
                  constraints.maxWidth,
                ),
                excludeFromSemantics: true,
                errorBuilder: (_, _, _) => const SizedBox.shrink(),
              ),
            ),
          DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  colors.surface.withValues(alpha: 0.35),
                  Colors.transparent,
                ],
                stops: const [0.0, 0.25],
              ),
            ),
          ),
          DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Colors.transparent,
                  colors.surface.withValues(alpha: 0.2),
                  colors.surface.withValues(alpha: 0.75),
                  colors.surfaceContainer,
                ],
                stops: const [0.0, 0.35, 0.72, 1.0],
              ),
            ),
          ),
          if (!isCompact)
            DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.centerLeft,
                  end: Alignment.centerRight,
                  colors: [
                    colors.surface,
                    colors.surface.withValues(alpha: 0.75),
                    Colors.transparent,
                  ],
                  stops: const [0.0, 0.45, 0.82],
                ),
              ),
            ),
          Positioned(
            left: HikariSpacing.lg,
            right: HikariSpacing.lg,
            bottom: HikariSpacing.lg,
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 620),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  DecoratedBox(
                    decoration: ShapeDecoration(
                      color: colors.primaryContainer.withValues(alpha: 0.9),
                      shape: HikariRadius.pill,
                    ),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: HikariSpacing.md,
                        vertical: HikariSpacing.xs,
                      ),
                      child: Text(
                        'Featured',
                        style: (theme.textTheme.labelSmall ?? const TextStyle())
                            .copyWith(
                              color: colors.onPrimaryContainer,
                              fontWeight: FontWeight.w700,
                              letterSpacing: 0.8,
                            ),
                      ),
                    ),
                  ),
                  const SizedBox(height: HikariSpacing.sm),
                  Text(
                    entry.title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style:
                        (isCompact
                                ? theme.textTheme.headlineSmall
                                : theme.textTheme.headlineMedium)
                            ?.copyWith(
                              color: colors.onSurface,
                              fontWeight: FontWeight.w800,
                              shadows: [
                                Shadow(
                                  color: Colors.black.withValues(alpha: 0.6),
                                  blurRadius: 12,
                                ),
                              ],
                            ) ??
                        theme.textTheme.headlineSmall,
                  ),
                  const SizedBox(height: HikariSpacing.xs),
                  Text(
                    metadata,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: (theme.textTheme.bodySmall ?? const TextStyle())
                        .copyWith(
                          color: colors.onSurfaceVariant,
                          fontWeight: FontWeight.w600,
                        ),
                  ),
                  const SizedBox(height: HikariSpacing.md),
                  FilledButton.icon(
                    onPressed: () => openDetail(entry),
                    icon: const Icon(Icons.info_outline_rounded, size: 18),
                    label: const Text('View details'),
                    style: FilledButton.styleFrom(
                      shape: HikariRadius.pill,
                      padding: const EdgeInsets.symmetric(
                        horizontal: HikariSpacing.lg,
                        vertical: HikariSpacing.md,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFallbackArtwork(ColorScheme colors) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: RadialGradient(
          center: const Alignment(0.4, -0.3),
          radius: 1.2,
          colors: [
            colors.primaryContainer.withValues(alpha: 0.4),
            colors.surfaceContainerHigh,
            colors.surfaceContainer,
            colors.surface,
          ],
          stops: const [0.0, 0.4, 0.75, 1.0],
        ),
      ),
      child: Stack(
        children: [
          Positioned(
            top: 20,
            right: 40,
            child: Icon(
              Icons.auto_awesome,
              size: 120,
              color: colors.primary.withValues(alpha: 0.12),
            ),
          ),
        ],
      ),
    );
  }
}
