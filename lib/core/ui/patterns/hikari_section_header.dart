import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

/// Compact semantic heading shared by Settings and management screens.
final class HikariSectionHeader extends StatelessWidget {
  const HikariSectionHeader({super.key, required this.title, this.description});

  final String title;
  final String? description;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsetsDirectional.only(
        start: HikariSpacing.xs,
        bottom: HikariSpacing.sm,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Semantics(
            header: true,
            child: Text(
              title,
              style: textTheme.labelMedium?.copyWith(
                color: colors.primary,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.4,
              ),
            ),
          ),
          if (description != null) ...[
            const SizedBox(height: HikariSpacing.xs),
            Text(
              description!,
              style: textTheme.bodySmall?.copyWith(
                color: colors.onSurfaceVariant,
              ),
            ),
          ],
        ],
      ),
    );
  }
}
