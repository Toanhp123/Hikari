import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_progress_bar.dart';
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

/// High-priority shelf for resuming media with unified progress treatment.
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
    final cardWidth = context.isCompact ? 164.0 : 196.0;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Continue',
                      style: HikariTypography.titleLarge.copyWith(
                        color: colors.textPrimary,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    Text(
                      'Pick up where you left off',
                      style: HikariTypography.bodySmall.copyWith(
                        color: colors.textMuted,
                      ),
                    ),
                  ],
                ),
              ),
              if (onSeeAll != null)
                InkWell(
                  onTap: onSeeAll,
                  borderRadius: HikariRadius.borderSm,
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(minHeight: 48),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: HikariSpacing.xs,
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            'Library',
                            style: HikariTypography.labelMedium.copyWith(
                              color: colors.primaryGlow,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(width: 2),
                          Icon(
                            Icons.chevron_right_rounded,
                            size: 19,
                            color: colors.primaryGlow,
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: HikariSpacing.md),
        SizedBox(
          height: 184,
          child: ListView.separated(
            padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
            scrollDirection: Axis.horizontal,
            physics: const BouncingScrollPhysics(),
            itemCount: items.length,
            separatorBuilder: (_, _) => const SizedBox(width: HikariSpacing.md),
            itemBuilder: (context, index) => SizedBox(
              width: cardWidth,
              child: _ContinueCard(
                item: items[index],
                onTap: () => onOpenMedia(context, items[index].media),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _ContinueCard extends StatelessWidget {
  const _ContinueCard({required this.item, required this.onTap});

  final ContinueReadingItem item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final badgeText = item.badgeText ?? _typeLabel(item.media.type);
    final badgeColor = item.badgeColor ?? _typeColor(colors, item.media.type);
    final actionIcon = switch (item.media.type) {
      MediaType.anime => Icons.play_arrow_rounded,
      MediaType.manga => Icons.auto_stories_rounded,
      MediaType.lightNovel => Icons.menu_book_rounded,
    };

    return Semantics(
      button: true,
      label: 'Continue ${item.media.title}, ${item.progressLabel}',
      child: Material(
        color: colors.surfaceContainer,
        shape: RoundedRectangleBorder(
          borderRadius: HikariRadius.borderMd,
          side: BorderSide(color: colors.borderSubtle),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    DecoratedBox(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                          colors: [
                            badgeColor.withValues(alpha: 0.18),
                            colors.surfaceElevated,
                            colors.surfaceContainer,
                          ],
                        ),
                      ),
                      child: Center(
                        child: Icon(
                          actionIcon,
                          size: 30,
                          color: badgeColor.withValues(alpha: 0.72),
                        ),
                      ),
                    ),
                    Positioned(
                      left: HikariSpacing.sm,
                      top: HikariSpacing.sm,
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          color: badgeColor.withValues(alpha: 0.9),
                          borderRadius: HikariRadius.borderXs,
                        ),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: HikariSpacing.sm,
                            vertical: 3,
                          ),
                          child: Text(
                            badgeText,
                            style: HikariTypography.labelSmall.copyWith(
                              color: Colors.white,
                              fontWeight: FontWeight.w800,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(HikariSpacing.sm),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item.media.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: HikariTypography.titleSmall.copyWith(
                        color: colors.textPrimary,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            item.progressLabel,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: HikariTypography.bodySmall.copyWith(
                              color: colors.textSecondary,
                            ),
                          ),
                        ),
                        const SizedBox(width: HikariSpacing.sm),
                        Icon(actionIcon, size: 17, color: colors.primaryGlow),
                      ],
                    ),
                    const SizedBox(height: HikariSpacing.sm),
                    Semantics(
                      label: 'Progress',
                      value:
                          '${(item.progress.clamp(0.0, 1.0) * 100).round()}%',
                      child: MediaProgressBar(
                        progress: item.progress,
                        height: 3,
                        showGlow: false,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  static String _typeLabel(MediaType type) => switch (type) {
    MediaType.anime => 'ANIME',
    MediaType.manga => 'MANGA',
    MediaType.lightNovel => 'NOVEL',
  };

  static Color _typeColor(HikariColors colors, MediaType type) =>
      switch (type) {
        MediaType.anime => colors.badgeVideo,
        MediaType.manga => colors.badgeManga,
        MediaType.lightNovel => colors.badgeNovel,
      };
}
