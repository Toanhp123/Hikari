import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_progress_bar.dart';

/// Shared 2:3 media artwork pattern.
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
    final colors = context.hikariColors;

    Widget content = AspectRatio(
      aspectRatio: 2 / 3,
      child: Container(
        decoration: BoxDecoration(
          color: colors.surfaceContainer,
          borderRadius: HikariRadius.borderMd,
          border: Border.all(color: colors.border),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.35),
              blurRadius: 10,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        clipBehavior: Clip.antiAlias,
        child: Stack(
          fit: StackFit.expand,
          children: [
            _buildArtwork(colors),
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              height: 90,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Colors.transparent,
                      colors.scrimStrong,
                      colors.background.withValues(alpha: 0.96),
                    ],
                  ),
                ),
              ),
            ),
            if (badgeText != null && badgeText!.isNotEmpty)
              Positioned(
                top: 8,
                left: 8,
                child: _MediaBadge(
                  label: badgeText!,
                  color: badgeColor ?? colors.primary,
                ),
              ),
            Positioned(
              left: 8,
              right: 8,
              bottom: progress != null ? 10 : 8,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    title,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: colors.textPrimary,
                      height: 1.25,
                    ),
                  ),
                  if (subtitle != null && subtitle!.isNotEmpty) ...[
                    const SizedBox(height: 2),
                    Text(
                      subtitle!,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 10,
                        color: colors.textSecondary,
                      ),
                    ),
                  ],
                ],
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
    );

    final tag = heroTag;
    if (tag != null) {
      content = Hero(tag: tag, child: content);
    }
    if (onTap != null) {
      content = InkWell(onTap: onTap, child: content);
    }

    return RepaintBoundary(
      child: SizedBox(width: width, height: height, child: content),
    );
  }

  Widget _buildArtwork(HikariColors colors) {
    if (isLoading) {
      return _PosterSkeleton(
        baseColor: colors.surfaceContainer,
        highlightColor: colors.surfaceHighlight,
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
            baseColor: colors.surfaceContainer,
            highlightColor: colors.surfaceHighlight,
          );
        },
      );
    }

    return _fallbackPlaceholder(colors);
  }

  Widget _fallbackPlaceholder(HikariColors colors) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            colors.surfaceElevated,
            colors.surfaceContainer,
            colors.surface,
          ],
        ),
      ),
      child: Center(
        child: Icon(
          Icons.movie_filter_rounded,
          size: 28,
          color: colors.primaryGlow.withValues(alpha: 0.75),
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
    return DecoratedBox(
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.9),
        borderRadius: HikariRadius.borderXs,
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        child: Text(
          label,
          style: const TextStyle(
            fontSize: 10,
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
