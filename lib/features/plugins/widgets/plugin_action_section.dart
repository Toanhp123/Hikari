import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/content/hikari_section_header.dart';

/// Consistent spacing for native evidence and staged action groups.
/// Children are rendered as-is; the route remains responsible for consent.
final class PluginActionSection extends StatelessWidget {
  const PluginActionSection({
    super.key,
    required this.title,
    required this.children,
    this.description,
  });

  final String title;
  final String? description;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      HikariSectionHeader(title: title, description: description),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(HikariSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (var i = 0; i < children.length; i++) ...[
                if (i > 0) const SizedBox(height: HikariSpacing.sm),
                children[i],
              ],
            ],
          ),
        ),
      ),
      const SizedBox(height: HikariSpacing.xl),
    ],
  );
}
