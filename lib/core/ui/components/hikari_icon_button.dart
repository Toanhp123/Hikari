import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

enum HikariIconButtonVariant { standard, filled, glass, primary }

/// Native focus/keyboard/semantics with Hikari's existing icon variants.
class HikariIconButton extends StatelessWidget {
  const HikariIconButton({
    super.key,
    required this.icon,
    required this.onPressed,
    this.tooltip,
    this.variant = HikariIconButtonVariant.standard,
    this.size = 48.0,
    this.iconSize = 22.0,
    this.color,
    this.hasBadge = false,
  });

  final Widget icon;
  final VoidCallback? onPressed;
  final String? tooltip;
  final HikariIconButtonVariant variant;
  final double size;
  final double iconSize;
  final Color? color;
  final bool hasBadge;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final enabled = onPressed != null;
    final targetSize = math.max(size, HikariSize.touchTarget);
    final background = switch (variant) {
      HikariIconButtonVariant.standard => Colors.transparent,
      HikariIconButtonVariant.filled => scheme.surfaceContainer,
      HikariIconButtonVariant.glass => scheme.surfaceContainerHigh.withValues(
        alpha: 0.7,
      ),
      HikariIconButtonVariant.primary => scheme.primary,
    };
    final border = switch (variant) {
      HikariIconButtonVariant.filled => BorderSide(color: scheme.outline),
      HikariIconButtonVariant.glass => BorderSide(color: scheme.outlineVariant),
      _ => BorderSide.none,
    };
    final foreground =
        color ??
        (variant == HikariIconButtonVariant.primary
            ? scheme.onPrimary
            : scheme.onSurface);

    return IconButton(
      tooltip: tooltip,
      onPressed: onPressed,
      iconSize: iconSize,
      constraints: BoxConstraints(minWidth: targetSize, minHeight: targetSize),
      padding: EdgeInsets.zero,
      style: IconButton.styleFrom(
        backgroundColor: background,
        foregroundColor: foreground,
        disabledForegroundColor: scheme.onSurface.withValues(alpha: 0.38),
        side: border,
        shape: RoundedRectangleBorder(borderRadius: HikariRadius.borderMd),
      ),
      icon: SizedBox.square(
        dimension: iconSize + 8,
        child: Stack(
          alignment: Alignment.center,
          children: [
            IconTheme(
              data: IconThemeData(
                size: iconSize,
                color: enabled
                    ? foreground
                    : scheme.onSurface.withValues(alpha: 0.38),
              ),
              child: icon,
            ),
            if (hasBadge)
              Positioned(
                top: 0,
                right: 0,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: scheme.secondary,
                    shape: BoxShape.circle,
                  ),
                  child: const SizedBox.square(dimension: 8),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
