import 'package:flutter/material.dart';

import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_progress_bar.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/continue_reading_item.dart';
import 'package:hikari/features/home/widgets/home_section_link.dart';

/// High-priority shelf for resuming media with unified progress treatment.
class ContinueShelf extends StatelessWidget {
  const ContinueShelf({
    super.key,
    required this.items,
    required this.onOpenMedia,
    this.onContinue,
    this.onSeeAll,
  });

  final List<ContinueReadingItem> items;
  final void Function(BuildContext, Media) onOpenMedia;
  final void Function(BuildContext, ContinueReadingItem)? onContinue;
  final VoidCallback? onSeeAll;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) =>
        _buildShelf(context, constraints.maxWidth),
  );

  Widget _buildShelf(BuildContext context, double width) {
    if (items.isEmpty) return const SizedBox.shrink();

    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final isCompact =
        HikariBreakpoints.classify(width) == HikariWidthClass.compact;
    final cardWidth = isCompact ? 172.0 : 208.0;

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
                    Semantics(
                      headingLevel: 2,
                      child: Text(
                        'Continue',
                        style: (theme.textTheme.titleLarge ?? const TextStyle())
                            .copyWith(
                              color: colors.onSurface,
                              fontWeight: FontWeight.w800,
                              letterSpacing: -0.2,
                            ),
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Pick up where you left off',
                      style: (theme.textTheme.bodySmall ?? const TextStyle())
                          .copyWith(color: colors.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
              if (onSeeAll != null)
                HomeSectionLink(label: 'Library', onTap: onSeeAll!),
            ],
          ),
        ),
        const SizedBox(height: HikariSpacing.md),
        SizedBox(
          height: 142,
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
                onTap: () => onContinue != null
                    ? onContinue!(context, items[index])
                    : onOpenMedia(context, items[index].media),
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
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final statusColors = theme.extension<HikariStatusColors>();
    final badgeText = mediaTypeBadgeLabel(item.media.type);
    final badgeColor = switch (item.media.type) {
      MediaType.anime => colors.primary,
      MediaType.manga =>
        statusColors?.onWarningContainer ?? const Color(0xFFFAB387),
      MediaType.lightNovel =>
        statusColors?.onInfoContainer ?? const Color(0xFF89DCEB),
    };
    final actionIcon = switch (item.media.type) {
      MediaType.anime => Icons.play_arrow_rounded,
      MediaType.manga => Icons.auto_stories_rounded,
      MediaType.lightNovel => Icons.menu_book_rounded,
    };

    final progressLabel = _progressLabel(item);

    return Semantics(
      button: true,
      label: 'Continue ${item.media.title}, $progressLabel',
      child: Material(
        color: colors.surfaceContainer,
        shape: RoundedRectangleBorder(
          borderRadius: HikariRadius.borderLg,
          side: BorderSide(
            color: colors.outlineVariant.withValues(alpha: 0.6),
            width: HikariRadius.borderWidth,
          ),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          splashColor: colors.primary.withValues(alpha: 0.12),
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
                            badgeColor.withValues(alpha: 0.16),
                            colors.surfaceContainerHigh,
                            colors.surfaceContainer,
                          ],
                        ),
                      ),
                      child: Center(
                        child: Container(
                          width: 38,
                          height: 38,
                          decoration: BoxDecoration(
                            color: badgeColor.withValues(alpha: 0.15),
                            shape: BoxShape.circle,
                          ),
                          child: Icon(actionIcon, size: 22, color: badgeColor),
                        ),
                      ),
                    ),
                    Positioned(
                      left: HikariSpacing.sm,
                      top: HikariSpacing.sm,
                      child: DecoratedBox(
                        decoration: ShapeDecoration(
                          color: badgeColor.withValues(alpha: 0.18),
                          shape: HikariRadius.pill,
                        ),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 6,
                            vertical: 2,
                          ),
                          child: Text(
                            badgeText,
                            style:
                                (theme.textTheme.labelSmall ??
                                        const TextStyle())
                                    .copyWith(
                                      color: badgeColor,
                                      fontWeight: FontWeight.w800,
                                      letterSpacing: 0.4,
                                    ),
                          ),
                        ),
                      ),
                    ),
                    Positioned(
                      left: 0,
                      right: 0,
                      bottom: 0,
                      child: Semantics(
                        label: 'Progress',
                        value:
                            '${(item.progress.clamp(0.0, 1.0) * 100).round()}%',
                        child: MediaProgressBar(
                          progress: item.progress,
                          height: 3,
                          showGlow: false,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  HikariSpacing.sm,
                  6,
                  HikariSpacing.sm,
                  HikariSpacing.sm,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      item.media.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: (theme.textTheme.titleSmall ?? const TextStyle())
                          .copyWith(
                            color: colors.onSurface,
                            fontWeight: FontWeight.w700,
                          ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      progressLabel,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: (theme.textTheme.bodySmall ?? const TextStyle())
                          .copyWith(color: colors.onSurfaceVariant),
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
}

String _progressLabel(ContinueReadingItem item) {
  if (item.chapter != null) {
    if (item.position case PagePosition(:final pageIndex, :final pageCount)) {
      return item.completed
          ? 'Chapter completed · Page ${pageIndex + 1} of $pageCount'
          : 'Last read chapter · Page ${pageIndex + 1} of $pageCount';
    }
    return item.completed ? 'Chapter completed' : 'Last read chapter';
  }
  return switch (item.position) {
    VideoPosition() => 'Resume video',
    PagePosition(:final pageIndex, :final pageCount) =>
      'Page ${pageIndex + 1} of $pageCount',
    TextPosition(:final progression) => '${(progression * 100).round()}% read',
    DocumentPosition() => 'Resume reading',
    _ => 'Resume',
  };
}
