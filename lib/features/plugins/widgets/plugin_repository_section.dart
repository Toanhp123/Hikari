import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';

/// Repository list is presentation-only; owner/consent changes live in the route.
final class PluginRepositorySection extends StatelessWidget {
  const PluginRepositorySection({
    super.key,
    required this.repositories,
    required this.busy,
    required this.onRefresh,
    required this.onRemove,
  });

  final List<PluginRepository> repositories;
  final bool busy;
  final ValueChanged<RepositoryId> onRefresh;
  final ValueChanged<PluginRepository> onRemove;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Text('Repositories', style: Theme.of(context).textTheme.titleLarge),
      if (repositories.isEmpty)
        const Text(
          'No repositories added. Nothing is downloaded automatically.',
        ),
      for (final repository in repositories)
        Card(
          child: Padding(
            padding: const EdgeInsets.all(HikariSpacing.md),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${pluginEcosystemLabel(repository.ecosystem)} · ${repository.snapshot?.name ?? 'Not refreshed'}',
                ),
                SelectableText(repository.id.toString()),
                if (repository.refreshError != null)
                  Text(
                    '${repository.refreshError}. Last good offers retained.',
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                Wrap(
                  spacing: HikariSpacing.sm,
                  children: [
                    TextButton(
                      onPressed: busy ? null : () => onRefresh(repository.id),
                      child: const Text('Refresh repository'),
                    ),
                    TextButton(
                      onPressed: busy ? null : () => onRemove(repository),
                      child: const Text('Remove repository'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
    ],
  );
}
