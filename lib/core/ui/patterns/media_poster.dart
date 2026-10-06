import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_progress_bar.dart';

/// Shared poster-grid geometry; features still own scroll and padding.
const mediaPosterGridDelegate = SliverGridDelegateWithMaxCrossAxisExtent(
  maxCrossAxisExtent: HikariBreakpoints.posterGridMaxExtent,
  crossAxisSpacing: HikariSpacing.md,
  mainAxisSpacing: HikariSpacing.md,
  childAspectRatio: 0.52,
);

/// Shared 2:3 media artwork pattern with title and metadata below artwork.
///
/// Feature-owned cards decide which product metadata belongs around this visual
/// primitive; the poster itself only owns artwork, identity labels and progress.
class MediaPoster extends StatelessWidget {
  const MediaPoster({
    super.key,
    required this.title,
    this.imageUrl,
    this.imageBytes,
    this.heroTag,
    this.badgeText,
    this.badgeColor,
    this.progress,
    this.subtitle,
    this.onTap,
    this.width,
    this.height,
    this.isLoading = false,
  });

  final String title;
  final String? imageUrl;
  final Uint8List? imageBytes;
  final String? heroTag;
  final String? badgeText;
  final Color? badgeColor;
  final double? progress;
  final String? subtitle;
  final VoidCallback? onTap;
  final double? width;
  final double? height;
  final bool isLoading;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    Widget artwork = AspectRatio(
      aspectRatio: 2 / 3,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: colors.surfaceContainer,
          borderRadius: HikariRadius.borderMd,
          border: Border.all(
            color: colors.outlineVariant.withValues(alpha: 0.6),
            width: 1,
          ),
        ),
        child: ClipRRect(
          borderRadius: HikariRadius.borderMd,
          child: Stack(
            fit: StackFit.expand,
            children: [
              _buildArtwork(colors),
              if (badgeText != null && badgeText!.isNotEmpty)
                Positioned(
                  top: 6,
                  left: 6,
                  child: _MediaBadge(
                    label: badgeText!,
                    color: badgeColor ?? colors.primary,
                  ),
                ),
              if (progress != null && progress! > 0)
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: 0,
                  child: MediaProgressBar(progress: progress!, height: 3),
                ),
            ],
          ),
        ),
      ),
    );

    final tag = heroTag;
    if (tag != null) {
      artwork = Hero(tag: tag, child: artwork);
    }

    final textContent = Padding(
      padding: const EdgeInsets.only(top: 6, left: 2, right: 2),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (!isLoading && title.isNotEmpty) ...[
            SizedBox(
              height: MediaQuery.textScalerOf(context).scale(32.0),
              child: Text(
                title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: (theme.textTheme.labelMedium ?? const TextStyle())
                    .copyWith(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: colors.onSurface,
                      height: 1.25,
                    ),
              ),
            ),
            if (subtitle != null && subtitle!.isNotEmpty) ...[
              const SizedBox(height: 2),
              Text(
                subtitle!,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: (theme.textTheme.bodySmall ?? const TextStyle())
                    .copyWith(color: colors.onSurfaceVariant),
              ),
            ],
          ] else if (isLoading) ...[
            Container(
              height: 12,
              width: double.infinity,
              decoration: ShapeDecoration(
                color: colors.surfaceContainerHighest,
                shape: const StadiumBorder(),
              ),
            ),
            const SizedBox(height: 4),
            Container(
              height: 10,
              width: 60,
              decoration: ShapeDecoration(
                color: colors.surfaceContainerHighest,
                shape: const StadiumBorder(),
              ),
            ),
          ],
        ],
      ),
    );

    Widget content = LayoutBuilder(
      builder: (context, constraints) {
        double? cardWidth = width;
        if (cardWidth == null &&
            constraints.hasBoundedHeight &&
            constraints.hasBoundedWidth) {
          final maxAllowedWidth = (constraints.maxHeight - 60) * (2 / 3);
          if (maxAllowedWidth > 0 && maxAllowedWidth < constraints.maxWidth) {
            cardWidth = maxAllowedWidth;
          }
        }

        Widget artworkBox = artwork;
        if (constraints.hasBoundedHeight) {
          artworkBox = Expanded(
            child: Align(alignment: Alignment.topCenter, child: artwork),
          );
        }

        Widget column = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [artworkBox, textContent],
        );

        if (cardWidth != null) {
          column = SizedBox(width: cardWidth, child: column);
        }

        return column;
      },
    );

    if (onTap != null) {
      content = InkWell(
        onTap: onTap,
        borderRadius: HikariRadius.borderMd,
        child: content,
      );
    }

    return RepaintBoundary(
      child: SizedBox(width: width, height: height, child: content),
    );
  }

  Widget _buildArtwork(ColorScheme colors) {
    if (isLoading) {
      return _PosterSkeleton(
        baseColor: colors.surfaceContainerHigh,
        highlightColor: colors.surfaceContainerHighest,
      );
    }

    final bytes = imageBytes;
    if (bytes != null && bytes.isNotEmpty) {
      return Image.memory(
        bytes,
        fit: BoxFit.cover,
        cacheWidth: 360,
        errorBuilder: (_, _, _) => _fallbackPlaceholder(colors),
      );
    }

    final url = imageUrl;
    if (url != null && url.isNotEmpty) {
      return Image.network(
        url,
        fit: BoxFit.cover,
        cacheWidth: 360,
        errorBuilder: (_, _, _) => _fallbackPlaceholder(colors),
        loadingBuilder: (_, child, loadingProgress) {
          if (loadingProgress == null) return child;
          return _PosterSkeleton(
            baseColor: colors.surfaceContainerHigh,
            highlightColor: colors.surfaceContainerHighest,
          );
        },
      );
    }

    return _fallbackPlaceholder(colors);
  }

  Widget _fallbackPlaceholder(ColorScheme colors) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            colors.surfaceContainerHigh,
            colors.surfaceContainer,
            colors.surface,
          ],
        ),
      ),
      child: Center(
        child: Icon(
          Icons.movie_filter_rounded,
          size: 28,
          color: colors.primary.withValues(alpha: 0.75),
        ),
      ),
    );
  }
}

class _MediaBadge extends StatelessWidget {
  const _MediaBadge({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.9),
        borderRadius: HikariRadius.borderXs,
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        child: Text(
          label,
          style: (theme.textTheme.labelSmall ?? const TextStyle()).copyWith(
            fontWeight: FontWeight.w700,
            color: Colors.white,
            letterSpacing: 0.5,
          ),
        ),
      ),
    );
  }
}

class _PosterSkeleton extends StatelessWidget {
  const _PosterSkeleton({
    required this.baseColor,
    required this.highlightColor,
  });

  final Color baseColor;
  final Color highlightColor;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [baseColor, highlightColor, baseColor],
        ),
      ),
    );
  }
}
