import 'package:flutter/material.dart';

import '../foundation/hikari_radius.dart';

/// Controlled filter selection. Null callback disables interaction.
class HikariFilterChip extends StatelessWidget {
  const HikariFilterChip({
    super.key,
    required this.label,
    required this.selected,
    required this.onSelected,
    this.leading,
  });

  final String label;
  final bool selected;
  final ValueChanged<bool>? onSelected;
  final Widget? leading;

  @override
  Widget build(BuildContext context) => FilterChip(
    label: Text(label),
    avatar: leading,
    selected: selected,
    onSelected: onSelected,
    showCheckmark: false,
    shape: HikariRadius.pill,
    selectedColor: Theme.of(context).colorScheme.secondaryContainer,
    materialTapTargetSize: MaterialTapTargetSize.padded,
  );
}
