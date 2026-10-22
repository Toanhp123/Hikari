import 'package:flutter/material.dart';

import '../foundation/hikari_spacing.dart';

/// Horizontal filters retain density and scroll under local width constraints.
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
