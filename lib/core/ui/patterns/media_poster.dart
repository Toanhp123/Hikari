import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_progress.dart';

/// A 2:3 aspect ratio media poster with scrim gradient, badges, shimmer skeleton, and hero animation.
class MediaPoster extends StatelessWidget {
  const MediaPoster({
    super.key,
    required this.title,
    this.imageUrl,
    this.imageBytes,
    this.heroTag,
    this.badgeText,
    this.badgeColor,
    this.rating,
    this.statusText,
    this.statusColor,
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
  final String? rating;
  final String? statusText;
  final Color? statusColor;
  final double? progress;
  final String? subtitle;
  final VoidCallback? onTap;
  final double? width;
  final double? height;
  final bool isLoading;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;

    Widget posterContent = AspectRatio(
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
            // Image or Skeleton or Fallback
            if (isLoading)
              _ShimmerBox(
                baseColor: colors.surfaceContainer,
                highlightColor: colors.surfaceHighlight,
              )
            else if (imageBytes != null && imageBytes!.isNotEmpty)
              Image.memory(
                imageBytes!,
                fit: BoxFit.cover,
                cacheWidth: 360,
                errorBuilder: (context, error, stackTrace) =>
                    _fallbackPlaceholder(colors),
              )
            else if (imageUrl != null && imageUrl!.isNotEmpty)
              Image.network(
                imageUrl!,
                fit: BoxFit.cover,
                cacheWidth: 360,
                errorBuilder: (context, error, stackTrace) =>
                    _fallbackPlaceholder(colors),
                loadingBuilder: (context, child, loadingProgress) {
                  if (loadingProgress == null) return child;
                  return _ShimmerBox(
                    baseColor: colors.surfaceContainer,
                    highlightColor: colors.surfaceHighlight,
                  );
                },
              )
            else
              _fallbackPlaceholder(colors),

            // Bottom 35% scrim gradient to protect title text
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              height: 90,
              child: Container(
                decoration: const BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Colors.transparent,
                      Color(0xCC0B0F17),
                      Color(0xF50B0F17),
                    ],
                  ),
                ),
              ),
            ),

            // Top-left Star Rating or Badge
            if (rating != null && rating!.isNotEmpty)
              Positioned(
                top: 8,
                left: 8,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 6,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: const Color(0xD90B0F17),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: Colors.white.withValues(alpha: 0.18),
                      width: 0.8,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.5),
                        blurRadius: 6,
                      ),
                    ],
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(
                        Icons.star_rounded,
                        size: 13,
                        color: Color(0xFFFBBF24),
                      ),
                      const SizedBox(width: 3),
                      Text(
                        rating!,
                        style: const TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.w700,
                          color: Colors.white,
                        ),
                      ),
                    ],
                  ),
                ),
              )
            else if (badgeText != null && badgeText!.isNotEmpty)
              Positioned(
                top: 8,
                left: 8,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: (badgeColor ?? colors.primary).withValues(
                      alpha: 0.9,
                    ),
                    borderRadius: HikariRadius.borderXs,
                    boxShadow: [
                      BoxShadow(
                        color: (badgeColor ?? colors.primary).withValues(
                          alpha: 0.4,
                        ),
                        blurRadius: 6,
                      ),
                    ],
                  ),
                  child: Text(
                    badgeText!,
                    style: const TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      color: Colors.white,
                      letterSpacing: 0.5,
                    ),
                  ),
                ),
              ),

            // Top-right Status Pill (Watching / Plan to Watch / Type)
            if (statusText != null && statusText!.isNotEmpty)
              Positioned(
                top: 8,
                right: 8,
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 7,
                    vertical: 3,
                  ),
                  decoration: BoxDecoration(
                    color: (statusColor ?? colors.surfaceElevated).withValues(
                      alpha: 0.85,
                    ),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: Colors.white.withValues(alpha: 0.16),
                      width: 0.8,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.4),
                        blurRadius: 4,
                      ),
                    ],
                  ),
                  child: Text(
                    statusText!,
                    style: const TextStyle(
                      fontSize: 9,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFFF1F5F9),
                      letterSpacing: 0.2,
                    ),
                  ),
                ),
              ),

            // Title & Subtitle in bottom scrim
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
                    style: const TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFFF8FAFC),
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

            // Progress bar at very bottom
            if (progress != null && progress! > 0)
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: MediaProgress(progress: progress!, height: 3.0),
              ),
          ],
        ),
      ),
    );

    if (heroTag != null) {
      posterContent = Hero(tag: heroTag!, child: posterContent);
    }

    if (onTap != null) {
      posterContent = GestureDetector(
        onTap: onTap,
        behavior: HitTestBehavior.opaque,
        child: posterContent,
      );
    }

    // Isolate repaint with RepaintBoundary
    return RepaintBoundary(
      child: SizedBox(width: width, height: height, child: posterContent),
    );
  }

  Widget _fallbackPlaceholder(HikariColors colors) {
    return Container(
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
        child: Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: colors.primary.withValues(alpha: 0.12),
            border: Border.all(
              color: colors.primaryGlow.withValues(alpha: 0.25),
              width: 1.0,
            ),
          ),
          child: Icon(
            Icons.movie_filter_rounded,
            size: 24,
            color: colors.primaryGlow.withValues(alpha: 0.8),
          ),
        ),
      ),
    );
  }
}

class _ShimmerBox extends StatelessWidget {
  const _ShimmerBox({required this.baseColor, required this.highlightColor});

  final Color baseColor;
  final Color highlightColor;

  @override
  Widget build(BuildContext context) {
    return Container(
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
