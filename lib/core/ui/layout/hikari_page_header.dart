import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';

/// Wrapping actions never compete with title for one fixed-height row.
class HikariPageHeader extends StatelessWidget {
  const HikariPageHeader({
    super.key,
    required this.title,
    this.subtitle,
    this.actions = const [],
  });
  final String title;
  final String? subtitle;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Semantics(
        header: true,
        child: Text(title, style: Theme.of(context).textTheme.headlineMedium),
      ),
      if (subtitle != null) ...[
        const SizedBox(height: HikariSpacing.sm),
        Text(subtitle!, style: Theme.of(context).textTheme.bodyMedium),
      ],
      if (actions.isNotEmpty) ...[
        const SizedBox(height: HikariSpacing.lg),
        Wrap(
          spacing: HikariSpacing.sm,
          runSpacing: HikariSpacing.sm,
          children: actions,
        ),
      ],
    ],
  );
}
