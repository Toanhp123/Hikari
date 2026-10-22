import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';

/// One discoverable plugin. Technical details stay behind the detail dialog.
final class PluginListTile extends StatelessWidget {
  const PluginListTile({
    super.key,
    required this.id,
    required this.name,
    required this.runtime,
    this.onTap,
  });

  final PluginId id;
  final String name;
  final String runtime;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final icon = switch (id.ecosystem) {
      PluginEcosystem.manga => Icons.menu_book_outlined,
      PluginEcosystem.anime => Icons.movie_outlined,
      PluginEcosystem.novel => Icons.auto_stories_outlined,
    };
    return Card(
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(
          horizontal: HikariSpacing.md,
          vertical: HikariSpacing.xs,
        ),
        leading: CircleAvatar(
          backgroundColor: colors.secondaryContainer,
          foregroundColor: colors.onSecondaryContainer,
          child: Icon(icon),
        ),
        title: Text(name, maxLines: 2, overflow: TextOverflow.ellipsis),
        subtitle: Text(
          '${pluginEcosystemLabel(id.ecosystem)} · ${id.upstreamId}\n$runtime',
          maxLines: 3,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(context).textTheme.bodySmall
              ?.copyWith(color: colors.onSurfaceVariant),
        ),
        trailing: const Icon(Icons.chevron_right_rounded),
        onTap: onTap,
      ),
    );
  }
}
