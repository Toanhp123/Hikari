import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_radius.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/media/hikari_media_progress.dart';
import 'package:hikari/core/ui/media/hikari_card_metrics.dart';

/// Internal anatomy shared by the two public card families.
class HikariMediaCardBody extends StatelessWidget {
  const HikariMediaCardBody({
    super.key,
    required this.title,
    required this.artwork,
    required this.aspectRatio,
    this.subtitle,
    this.badge,
    this.progress,
    this.onTap,
    this.heroTag,
  });
  final String title;
  final Widget artwork;
  final double aspectRatio;
  final String? subtitle;
  final Widget? badge;
  final double? progress;
  final VoidCallback? onTap;
  final String? heroTag;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    Widget cover = ClipRRect(
      borderRadius: HikariRadius.borderMd,
      child: AspectRatio(
        aspectRatio: aspectRatio,
        child: Stack(
          fit: StackFit.expand,
          children: [
            ExcludeSemantics(child: artwork),
            if (badge != null)
              PositionedDirectional(
                top: HikariSpacing.xs,
                start: HikariSpacing.xs,
                end: HikariSpacing.xs,
                child: Align(
                  alignment: AlignmentDirectional.topStart,
                  child: badge!,
                ),
              ),
            if (progress != null)
              PositionedDirectional(
                bottom: 0,
                start: 0,
                end: 0,
                child: HikariMediaProgress(progress: progress!),
              ),
            IgnorePointer(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  borderRadius: HikariRadius.borderMd,
                  border: Border.all(color: theme.colorScheme.outlineVariant),
                ),
              ),
            ),
          ],
        ),
      ),
    );
    if (heroTag != null) cover = Hero(tag: heroTag!, child: cover);
    final content = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        cover,
        const SizedBox(height: HikariSpacing.xs),
        SizedBox(
          height: hikariCardTitleHeight(context),
          child: Text(
            title,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: hikariCardTitleStyle(context),
          ),
        ),
        if (subtitle != null) ...[
          const SizedBox(height: 2),
          Text(
            subtitle!,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: hikariCardSubtitleStyle(context),
          ),
        ],
      ],
    );
    if (onTap == null) return content;
    return MergeSemantics(
      child: Stack(
        children: [
          content,
          Positioned.fill(
            child: Material(
              type: MaterialType.transparency,
              child: InkWell(borderRadius: HikariRadius.borderMd, onTap: onTap),
            ),
          ),
        ],
      ),
    );
  }
}
