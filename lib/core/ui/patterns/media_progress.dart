import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

/// An elegant glowing progress bar showing reading or playback completion.
class MediaProgress extends StatelessWidget {
  const MediaProgress({
    super.key,
    required this.progress,
    this.height = 3.5,
    this.showGlow = true,
  });

  /// Progress from 0.0 to 1.0. Clamped automatically.
  final double progress;
  final double height;
  final bool showGlow;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final clamped = progress.clamp(0.0, 1.0);

    return LayoutBuilder(
      builder: (context, constraints) {
        final totalWidth = constraints.maxWidth;
        final progressWidth = totalWidth * clamped;

        return ClipRRect(
          borderRadius: BorderRadius.circular(height / 2),
          child: Container(
            height: height,
            width: totalWidth,
            color: colors.surfaceHighlight.withValues(alpha: 0.6),
            alignment: Alignment.centerLeft,
            child: Container(
              width: progressWidth,
              height: height,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [colors.primary, colors.secondary],
                ),
                boxShadow: showGlow
                    ? [
                        BoxShadow(
                          color: colors.primary.withValues(alpha: 0.5),
                          blurRadius: 4,
                          spreadRadius: 1,
                        ),
                      ]
                    : null,
              ),
            ),
          ),
        );
      },
    );
  }
}
