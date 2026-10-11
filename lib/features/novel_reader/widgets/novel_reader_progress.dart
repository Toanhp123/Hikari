import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_progress_bar.dart';

class NovelReaderProgress extends StatelessWidget {
  const NovelReaderProgress({
    super.key,
    required this.progress,
    required this.backgroundColor,
    required this.foregroundColor,
  });

  final double progress;
  final Color backgroundColor;
  final Color foregroundColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(minHeight: HikariSpacing.xl),
      padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.md),
      color: backgroundColor,
      child: Row(
        children: [
          Expanded(
            child: MediaProgressBar(
              progress: progress,
              height: 2.5,
              showGlow: false,
            ),
          ),
          const SizedBox(width: HikariSpacing.sm),
          Text(
            '${(progress * 100).toInt()}%',
            style: Theme.of(context).textTheme.labelSmall
                ?.copyWith(color: foregroundColor.withValues(alpha: 0.6)),
          ),
        ],
      ),
    );
  }
}
