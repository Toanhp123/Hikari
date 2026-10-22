import 'package:flutter/material.dart';

import '../foundation/hikari_spacing.dart';

/// Semantic section heading with locally wrapping feature-owned actions.
final class HikariSectionHeader extends StatelessWidget {
  const HikariSectionHeader({
    super.key,
    required this.title,
    this.description,
    this.actions = const [],
  });

  final String title;
  final String? description;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsetsDirectional.only(
        start: HikariSpacing.xs,
        bottom: HikariSpacing.sm,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Semantics(
            header: true,
            child: Text(
              title,
              style: text.labelMedium?.copyWith(
                color: colors.primary,
                fontWeight: FontWeight.w600,
                letterSpacing: .4,
              ),
            ),
          ),
          if (description != null) ...[
            const SizedBox(height: HikariSpacing.xs),
            Text(
              description!,
              style: text.bodySmall?.copyWith(color: colors.onSurfaceVariant),
            ),
          ],
          if (actions.isNotEmpty)
            Wrap(
              spacing: HikariSpacing.sm,
              runSpacing: HikariSpacing.xs,
              children: actions,
            ),
        ],
      ),
    );
  }
}
