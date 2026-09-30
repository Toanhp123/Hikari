import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
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
          child: InkWell(
            onTap: onSeeAll,
            borderRadius: HikariRadius.borderSm,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Continue Watching & Reading',
                  style: HikariTypography.titleMedium.copyWith(
                    color: colors.textPrimary,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                Icon(
                  Icons.chevron_right_rounded,
                  size: 22,
                  color: colors.textSecondary,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: HikariSpacing.sm),

        // Horizontal Carousel of Neo-Material Landscape Cards
        SizedBox(
          height: 195,
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
            scrollDirection: Axis.horizontal,
            physics: const BouncingScrollPhysics(),
            itemCount: items.length,
            separatorBuilder: (context, index) =>
                const SizedBox(width: HikariSpacing.md),
            itemBuilder: (context, index) {
              final item = items[index];
              final isHighlighted = index == 0 || item.progress > 0.7;

              return RepaintBoundary(
                child: GestureDetector(
                  onTap: () => onOpenMedia(context, item.media),
                  child: Container(
                    width: 165,
                    decoration: BoxDecoration(
                      color: colors.surfaceContainer,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(
                        color: colors.borderSubtle,
                        width: 1.0,
                      ),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.35),
                          blurRadius: 10,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    clipBehavior: Clip.antiAlias,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Card Thumbnail Header (16:10 ratio)
                        SizedBox(
                          height: 105,
                          width: double.infinity,
                          child: Stack(
                            fit: StackFit.expand,
                            children: [
                              Container(
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
                                    item.media.type == MediaType.anime
                                        ? Icons.movie_filter_rounded
                                        : item.media.type == MediaType.manga
                                        ? Icons.auto_stories_rounded
                                        : Icons.chrome_reader_mode_rounded,
                                    size: 32,
                                    color: colors.primaryGlow.withValues(
                                      alpha: 0.6,
                                    ),
                                  ),
                                ),
                              ),
                              // Subtle bottom scrim
                              Positioned(
                                left: 0,
                                right: 0,
                                bottom: 0,
                                height: 32,
                                child: Container(
                                  decoration: BoxDecoration(
                                    gradient: LinearGradient(
                                      begin: Alignment.topCenter,
                                      end: Alignment.bottomCenter,
                                      colors: [
                                        Colors.transparent,
                                        colors.scrimMedium,
                                      ],
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),

                        // Card Content
                        Expanded(
                          child: Padding(
                            padding: const EdgeInsets.fromLTRB(10, 8, 10, 8),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      item.media.title,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(
                                        fontSize: 13,
                                        fontWeight: FontWeight.w700,
                                        color: colors.textPrimary,
                                      ),
                                    ),
                                    const SizedBox(height: 2),
                                    Text(
                                      item.progressLabel,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: TextStyle(
                                        fontSize: 11,
                                        color: colors.textSecondary,
                                      ),
                                    ),
                                  ],
                                ),

                                // Bottom row with Progress Bar and Play Button
                                Row(
                                  children: [
                                    Expanded(
                                      child: ClipRRect(
                                        borderRadius: BorderRadius.circular(2),
                                        child: SizedBox(
                                          height: 3,
                                          child: LinearProgressIndicator(
                                            value: item.progress.clamp(
                                              0.0,
                                              1.0,
                                            ),
                                            backgroundColor: colors.border,
                                            valueColor:
                                                AlwaysStoppedAnimation<Color>(
                                                  colors.primaryGlow,
                                                ),
                                          ),
                                        ),
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    Container(
                                      width: 24,
                                      height: 24,
                                      decoration: BoxDecoration(
                                        shape: BoxShape.circle,
                                        color: isHighlighted
                                            ? colors.primary
                                            : const Color(0x33FFFFFF),
                                        boxShadow: isHighlighted
                                            ? [
                                                BoxShadow(
                                                  color: colors.primaryGlow
                                                      .withValues(alpha: 0.6),
                                                  blurRadius: 8,
                                                ),
                                              ]
                                            : null,
                                      ),
                                      child: const Icon(
                                        Icons.play_arrow_rounded,
                                        size: 16,
                                        color: Colors.white,
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
