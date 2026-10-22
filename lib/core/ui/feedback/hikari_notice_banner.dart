import 'package:flutter/material.dart';

import '../foundation/hikari_color_roles.dart';
import '../foundation/hikari_radius.dart';
import '../foundation/hikari_spacing.dart';

enum HikariNoticeTone { info, warning, error }

/// Semantic icon + text; color is never the only indication of status.
class HikariNoticeBanner extends StatelessWidget {
  const HikariNoticeBanner({
    super.key,
    required this.message,
    this.tone = HikariNoticeTone.info,
    this.action,
  });

  final String message;
  final HikariNoticeTone tone;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final status = context.hikariStatusColors;
    final (background, foreground, icon) = switch (tone) {
      HikariNoticeTone.info => (
        status.infoContainer,
        status.onInfoContainer,
        Icons.info_outline,
      ),
      HikariNoticeTone.warning => (
        status.warningContainer,
        status.onWarningContainer,
        Icons.warning_amber_rounded,
      ),
      HikariNoticeTone.error => (
        colors.errorContainer,
        colors.onErrorContainer,
        Icons.error_outline,
      ),
    };
    return Semantics(
      liveRegion: true,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: background,
          borderRadius: HikariRadius.borderSm,
        ),
        child: Padding(
          padding: const EdgeInsets.all(HikariSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ExcludeSemantics(child: Icon(icon, color: foreground)),
                  const SizedBox(width: HikariSpacing.sm),
                  Expanded(
                    child: Text(
                      message,
                      style: Theme.of(context).textTheme.bodyMedium
                          ?.copyWith(color: foreground),
                    ),
                  ),
                ],
              ),
              if (action != null)
                Padding(
                  padding: const EdgeInsets.only(top: HikariSpacing.sm),
                  child: action!,
                ),
            ],
          ),
        ),
      ),
    );
  }
}
