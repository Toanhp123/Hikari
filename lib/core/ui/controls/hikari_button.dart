import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../foundation/hikari_radius.dart';
import '../foundation/hikari_size.dart';
import '../foundation/hikari_spacing.dart';

enum HikariButtonVariant { primary, secondary, ghost, danger }

enum HikariButtonSize {
  small(height: 36, horizontalPadding: 12),
  medium(height: 44, horizontalPadding: 16),
  large(height: 52, horizontalPadding: 24);

  const HikariButtonSize({
    required this.height,
    required this.horizontalPadding,
  });

  final double height;
  final double horizontalPadding;
}

/// Shared Hikari actions backed by native Material buttons.
///
/// Native controls own keyboard activation, focus, disabled semantics and
/// Material interaction states. Size variants retain their text/padding scale;
/// every enabled action has at least the canonical 48dp touch target.
class HikariButton extends StatelessWidget {
  const HikariButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.variant = HikariButtonVariant.primary,
    this.size = HikariButtonSize.medium,
    this.isLoading = false,
    this.isFullWidth = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final Widget? icon;
  final HikariButtonVariant variant;
  final HikariButtonSize size;
  final bool isLoading;
  final bool isFullWidth;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final effectiveOnPressed = isLoading ? null : onPressed;
    final text = Theme.of(context).textTheme;
    final baseLabelStyle = switch (size) {
      HikariButtonSize.small => text.labelMedium,
      HikariButtonSize.medium => text.labelLarge,
      HikariButtonSize.large => text.titleMedium,
    };
    final foregroundColor = effectiveOnPressed == null
        ? colors.onSurface.withValues(alpha: 0.38)
        : switch (variant) {
            HikariButtonVariant.primary => colors.onPrimary,
            HikariButtonVariant.secondary => colors.onSurface,
            HikariButtonVariant.ghost => colors.onSurfaceVariant,
            HikariButtonVariant.danger => colors.onError,
          };
    final labelStyle = baseLabelStyle?.copyWith(color: foregroundColor);
    final commonStyle = ButtonStyle(
      minimumSize: WidgetStatePropertyAll(
        Size(
          HikariSize.touchTarget,
          math.max(size.height, HikariSize.touchTarget),
        ),
      ),
      padding: WidgetStatePropertyAll(
        EdgeInsets.symmetric(
          horizontal: size.horizontalPadding,
          vertical: HikariSpacing.sm,
        ),
      ),
      shape: const WidgetStatePropertyAll(
        RoundedRectangleBorder(borderRadius: HikariRadius.borderMd),
      ),
    );

    final content = Row(
      mainAxisSize: isFullWidth ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (isLoading) ...[
          ExcludeSemantics(
            child: SizedBox(
              width: baseLabelStyle?.fontSize ?? 14,
              height: baseLabelStyle?.fontSize ?? 14,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: foregroundColor,
              ),
            ),
          ),
          const SizedBox(width: HikariSpacing.xs),
        ] else if (icon != null) ...[
          IconTheme(
            data: IconThemeData(color: foregroundColor, size: HikariSize.icon),
            child: ExcludeSemantics(child: icon!),
          ),
          const SizedBox(width: HikariSpacing.xs),
        ],
        Flexible(
          child: Text(label, textAlign: TextAlign.center, style: labelStyle),
        ),
      ],
    );

    final Widget button = switch (variant) {
      HikariButtonVariant.primary => FilledButton(
        onPressed: effectiveOnPressed,
        style: commonStyle,
        child: content,
      ),
      HikariButtonVariant.secondary => OutlinedButton(
        onPressed: effectiveOnPressed,
        style: OutlinedButton.styleFrom(
          backgroundColor: colors.surfaceContainer,
          foregroundColor: colors.onSurface,
          side: BorderSide(color: colors.outline),
        ).merge(commonStyle),
        child: content,
      ),
      HikariButtonVariant.ghost => TextButton(
        onPressed: effectiveOnPressed,
        style: TextButton.styleFrom(foregroundColor: colors.onSurfaceVariant)
            .merge(commonStyle),
        child: content,
      ),
      HikariButtonVariant.danger => FilledButton(
        onPressed: effectiveOnPressed,
        style: FilledButton.styleFrom(
          backgroundColor: colors.error,
          foregroundColor: colors.onError,
          disabledBackgroundColor: colors.onSurface.withValues(alpha: 0.12),
          disabledForegroundColor: colors.onSurface.withValues(alpha: 0.38),
        ).merge(commonStyle),
        child: content,
      ),
    };

    return Semantics(
      value: isLoading ? 'Loading' : null,
      child: isFullWidth
          ? SizedBox(width: double.infinity, child: button)
          : button,
    );
  }
}
