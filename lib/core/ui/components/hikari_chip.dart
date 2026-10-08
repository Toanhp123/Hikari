import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

/// Native selection semantics and keyboard/focus behavior for feature filters.
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
    final colors = Theme.of(context).colorScheme;
    final content = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (customBadgeColor != null) ...[
          DecoratedBox(
            decoration: BoxDecoration(
              color: customBadgeColor,
              shape: BoxShape.circle,
            ),
            child: const SizedBox.square(dimension: 6),
          ),
          const SizedBox(width: HikariSpacing.xs),
        ] else if (leading != null) ...[
          leading!,
          const SizedBox(width: HikariSpacing.xs),
        ],
        Text(label),
      ],
    );
    if (onTap == null) {
      return Chip(label: content, shape: HikariRadius.pill);
    }
    return FilterChip(
      label: content,
      selected: isSelected,
      onSelected: (_) => onTap!(),
      showCheckmark: false,
      shape: HikariRadius.pill,
      selectedColor: colors.secondaryContainer,
    );
  }
}

/// Horizontal row for feature-owned filter chips; selection stays with features.
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
