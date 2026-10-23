import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_radius.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';

/// Domain-free row. Feature owns identity, formatting and readability decisions.
class HikariChapterRow extends StatelessWidget {
  const HikariChapterRow({
    super.key,
    required this.title,
    required this.subtitle,
    required this.enabled,
    this.onTap,
    this.isContinuation = false,
  });
  final String title;
  final String subtitle;
  final bool enabled;
  final VoidCallback? onTap;
  final bool isContinuation;
  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(
      horizontal: HikariSpacing.md,
      vertical: HikariSpacing.xs,
    ),
    child: ListTile(
      shape: const RoundedRectangleBorder(borderRadius: HikariRadius.borderMd),
      tileColor: Theme.of(context).colorScheme.surfaceContainerLow,
      title: Text(title),
      subtitle: Text(
        [
          subtitle,
          if (isContinuation) 'Continue reading',
        ].where((text) => text.isNotEmpty).join(' · '),
      ),
      trailing: enabled && onTap != null
          ? const Icon(Icons.chevron_right_rounded)
          : null,
      enabled: enabled,
      onTap: enabled ? onTap : null,
    ),
  );
}
