import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/media/media.dart';

class ContinueReadingItem {
  const ContinueReadingItem({
    required this.media,
    required this.progress,
    required this.progressLabel,
    this.badgeText,
    this.badgeColor,
  });

  final Media media;
  final double progress;
  final String progressLabel;
  final String? badgeText;
  final Color? badgeColor;
}

/// Horizontal shelf for "Continue Watching & Reading" with unified progress badges.
class ContinueShelf extends StatelessWidget {
  const ContinueShelf({
    super.key,
    required this.items,
    required this.onOpenMedia,
    this.onSeeAll,
  });

  final List<ContinueReadingItem> items;
  final void Function(BuildContext, Media) onOpenMedia;
  final VoidCallback? onSeeAll;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) return const SizedBox.shrink();
    final colors = context.hikariColors;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Shelf Header
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Icon(
                    Icons.history_rounded,
                    size: 20,
                    color: colors.primaryGlow,
                  ),
                  const SizedBox(width: HikariSpacing.xs),
                  Text(
                    'Continue Watching & Reading',
                    style: HikariTypography.titleMedium.copyWith(
                      color: colors.textPrimary,
                    ),
                  ),
                ],
              ),
              if (onSeeAll != null)
                TextButton(
                  onPressed: onSeeAll,
                  style: TextButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                    foregroundColor: colors.primaryGlow,
                  ),
                  child: const Text('See All', style: TextStyle(fontSize: 12)),
                ),
            ],
          ),
        ),
        const SizedBox(height: HikariSpacing.sm),

        // Horizontal Carousel
        SizedBox(
          height: 190,
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
            scrollDirection: Axis.horizontal,
            itemCount: items.length,
            separatorBuilder: (context, index) =>
                const SizedBox(width: HikariSpacing.md),
            itemBuilder: (context, index) {
              final item = items[index];
              return SizedBox(
                width: 120,
                child: MediaPoster(
                  title: item.media.title,
                  subtitle: item.progressLabel,
                  progress: item.progress,
                  badgeText:
                      item.badgeText ??
                      (item.media.type == MediaType.anime
                          ? 'ANIME'
                          : item.media.type == MediaType.manga
                          ? 'MANGA'
                          : 'NOVEL'),
                  badgeColor:
                      item.badgeColor ??
                      (item.media.type == MediaType.anime
                          ? colors.badgeVideo
                          : item.media.type == MediaType.manga
                          ? colors.badgeManga
                          : colors.badgeNovel),
                  onTap: () => onOpenMedia(context, item.media),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
