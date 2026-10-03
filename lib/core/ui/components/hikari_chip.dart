import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

/// A filter pill / badge chip component for category selection and media tags.
class HikariChip extends StatelessWidget {
  const HikariChip({
    super.key,
    required this.label,
    this.isSelected = false,
    this.onTap,
    this.leading,
    this.customBadgeColor,
  });

  final String label;
  final bool isSelected;
  final VoidCallback? onTap;
  final Widget? leading;
  final Color? customBadgeColor;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;

    final Color bg = isSelected
        ? colors.primary.withValues(alpha: 0.18)
        : colors.surfaceContainer;

    final Color fg = isSelected ? colors.primaryGlow : colors.textSecondary;

    final Border border = Border.all(
      color: isSelected ? colors.primary.withValues(alpha: 0.6) : colors.border,
      width: isSelected ? 1.5 : 1.0,
    );

    return GestureDetector(
      onTap: onTap,
      behavior: HitTestBehavior.opaque,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 48), // accessible tap area
        child: Center(
          child: AnimatedContainer(
            duration: HikariMotion.fast,
            curve: HikariMotion.curveStandard,
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            decoration: BoxDecoration(
              color: bg,
              borderRadius: HikariRadius.borderCapsule,
              border: border,
              boxShadow: isSelected
                  ? [
                      BoxShadow(
                        color: colors.primary.withValues(alpha: 0.2),
                        blurRadius: 8,
                        offset: const Offset(0, 2),
                      ),
                    ]
                  : null,
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (customBadgeColor != null) ...[
                  Container(
                    width: 6,
                    height: 6,
                    decoration: BoxDecoration(
                      color: customBadgeColor,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: HikariSpacing.xs),
                ] else if (leading != null) ...[
                  leading!,
                  const SizedBox(width: HikariSpacing.xs),
                ],
                Text(
                  label,
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
                    color: fg,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Horizontal row for feature-owned filter chips.
///
/// The row owns only scrolling and spacing; features still own chip labels,
/// selection semantics and actions.
class HikariChipRow extends StatelessWidget {
  const HikariChipRow({
    super.key,
    required this.children,
    this.spacing = HikariSpacing.sm,
  });

  final List<Widget> children;
  final double spacing;

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    scrollDirection: Axis.horizontal,
    child: Row(
      children: [
        for (var index = 0; index < children.length; index++) ...[
          if (index > 0) SizedBox(width: spacing),
          children[index],
        ],
      ],
    ),
  );
}
