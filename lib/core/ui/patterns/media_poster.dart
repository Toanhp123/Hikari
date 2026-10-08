import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_artwork_decode.dart';
import 'package:hikari/core/ui/patterns/media_progress_bar.dart';

/// Shared poster-grid geometry; features still own scroll and padding.
const mediaPosterGridDelegate = SliverGridDelegateWithMaxCrossAxisExtent(
  maxCrossAxisExtent: HikariBreakpoints.posterGridMaxExtent,
  crossAxisSpacing: HikariSpacing.md,
  mainAxisSpacing: HikariSpacing.md,
  childAspectRatio: 0.52,
);

TextStyle _posterTitleStyle(ThemeData theme) =>
    (theme.textTheme.labelMedium ?? const TextStyle()).copyWith(
      fontWeight: FontWeight.w600,
      color: theme.colorScheme.onSurface,
      height: 1.25,
    );

// Shared, bounded metrics cache: hundreds of posters often share the same
// effective typography, direction and text scaler on a single screen.
final _posterTitleHeights =
    <({TextStyle style, TextDirection direction, TextScaler scaler}), double>{};

/// Height of two real text lines under the active (possibly nonlinear) scaler.
double mediaPosterTitleReserveHeight(BuildContext context) {
  final key = (
    style: _posterTitleStyle(Theme.of(context)),
    direction: Directionality.of(context),
    scaler: MediaQuery.textScalerOf(context),
  );
  final cached = _posterTitleHeights[key];
  if (cached != null) return cached;
  final painter = TextPainter(
    text: TextSpan(text: 'Hg\nHg', style: key.style),
    textDirection: key.direction,
    textScaler: key.scaler,
    maxLines: 2,
  )..layout();
  final height = painter.height;
  painter.dispose();
  if (_posterTitleHeights.length >= 24) _posterTitleHeights.clear();
  _posterTitleHeights[key] = height;
  return height;
}

/// Shelf geometry keeps 2:3 artwork and reserves scaled title space below it.
double mediaPosterShelfHeight(BuildContext context, double posterWidth) =>
    posterWidth * 1.5 + mediaPosterTitleReserveHeight(context) + 14;

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
    this.badgeForegroundColor,
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
  final Color? badgeForegroundColor;
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
    final titleReserve = mediaPosterTitleReserveHeight(context);

    Widget artwork = AspectRatio(
      aspectRatio: 2 / 3,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final decodeWidth = mediaArtworkCacheWidth(
            context,
            constraints.maxWidth,
          );
          return ClipRRect(
            borderRadius: HikariRadius.borderMd,
            child: Stack(
              fit: StackFit.expand,
              children: [
                ColoredBox(color: colors.surfaceContainer),
                _buildArtwork(colors, cacheWidth: decodeWidth),
                if (badgeText != null && badgeText!.isNotEmpty)
                  Positioned(
                    top: 6,
                    left: 6,
                    child: _MediaBadge(
                      label: badgeText!,
                      color: badgeColor ?? colors.primaryContainer,
                      foregroundColor:
                          badgeForegroundColor ??
                          (badgeColor == null
                              ? colors.onPrimaryContainer
                              : null),
                    ),
                  ),
                if (progress != null && progress! > 0)
                  Positioned(
                    left: 0,
                    right: 0,
                    bottom: 0,
                    child: MediaProgressBar(progress: progress!, height: 3),
                  ),
                // The stroke paints above opaque images, not behind them.
                Positioned.fill(
                  child: IgnorePointer(
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        borderRadius: HikariRadius.borderMd,
                        border: Border.all(
                          color: colors.outlineVariant.withValues(alpha: 0.6),
                          width: 1,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          );
        },
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
              height: titleReserve,
              child: Text(
                title,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: _posterTitleStyle(theme),
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
            const SizedBox(height: HikariSpacing.xs),
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
          final textSpace = titleReserve + (subtitle == null ? 14 : 32);
          final maxAllowedWidth = (constraints.maxHeight - textSpace) * (2 / 3);
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
      // Ink is painted on this local, transparent Material *above* artwork.
      // A page-level Material would paint the splash beneath opaque images.
      content = MergeSemantics(
        child: Stack(
          fit: StackFit.passthrough,
          children: [
            content,
            Positioned.fill(
              child: Material(
                type: MaterialType.transparency,
                borderRadius: HikariRadius.borderMd,
                clipBehavior: Clip.antiAlias,
                child: InkWell(
                  onTap: onTap,
                  borderRadius: HikariRadius.borderMd,
                ),
              ),
            ),
          ],
        ),
      );
    }

    return SizedBox(width: width, height: height, child: content);
  }

  Widget _buildArtwork(ColorScheme colors, {int? cacheWidth}) {
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
        cacheWidth: cacheWidth,
        errorBuilder: (_, _, _) => _fallbackPlaceholder(colors),
      );
    }

    final url = imageUrl;
    if (url != null && url.isNotEmpty) {
      return Image.network(
        url,
        fit: BoxFit.cover,
        cacheWidth: cacheWidth,
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
  const _MediaBadge({
    required this.label,
    required this.color,
    this.foregroundColor,
  });

  final String label;
  final Color color;
  final Color? foregroundColor;

  // For custom opaque fills, this always chooses the higher-contrast neutral.
  // The semantic default uses ColorScheme's explicit container/on-container pair.
  Color get effectiveForeground {
    if (foregroundColor != null) return foregroundColor!;
    final luminance = color.computeLuminance();
    final whiteContrast = 1.05 / (luminance + 0.05);
    final blackContrast = (luminance + 0.05) / 0.05;
    return whiteContrast >= blackContrast ? Colors.white : Colors.black;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: color.withValues(alpha: 1),
        borderRadius: HikariRadius.borderXs,
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        child: Text(
          label,
          style: (theme.textTheme.labelSmall ?? const TextStyle()).copyWith(
            fontWeight: FontWeight.w700,
            color: effectiveForeground,
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
