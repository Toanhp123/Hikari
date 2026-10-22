import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/controls/hikari_button.dart';
import 'package:hikari/core/ui/content/hikari_section_header.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';
import 'package:hikari/features/plugins/widgets/plugin_notice.dart';

/// Repository inventory and its explicitly requested actions.
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
      HikariSectionHeader(
        title: 'Repositories',
        description:
            '${repositories.length} ${repositories.length == 1 ? 'repository' : 'repositories'} · Manage catalog metadata and update ownership',
      ),
      if (repositories.isEmpty)
        const PluginNotice(
          message:
              'No repositories added. Nothing is downloaded automatically.',
        ),
      for (final repository in repositories) ...[
        Card(
          child: Padding(
            padding: const EdgeInsets.all(HikariSpacing.md),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.storage_rounded,
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                    const SizedBox(width: HikariSpacing.sm),
                    Expanded(
                      child: Text(
                        repository.snapshot?.name ?? 'Not refreshed',
                        style: Theme.of(context).textTheme.titleSmall,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: HikariSpacing.xs),
                Text(
                  pluginEcosystemLabel(repository.ecosystem),
                  style: Theme.of(context).textTheme.labelMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: HikariSpacing.xs),
                SelectableText(
                  repository.id.toString(),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                if (repository.refreshError != null) ...[
                  const SizedBox(height: HikariSpacing.sm),
                  PluginNotice(
                    message:
                        '${repository.refreshError}. Last good offers retained.',
                    error: true,
                  ),
                ],
                const SizedBox(height: HikariSpacing.sm),
                Wrap(
                  spacing: HikariSpacing.sm,
                  runSpacing: HikariSpacing.xs,
                  children: [
                    HikariButton(
                      label: 'Refresh repository',
                      variant: HikariButtonVariant.ghost,
                      size: HikariButtonSize.small,
                      icon: const Icon(Icons.refresh_rounded),
                      onPressed: busy ? null : () => onRefresh(repository.id),
                    ),
                    HikariButton(
                      label: 'Remove repository',
                      variant: HikariButtonVariant.ghost,
                      size: HikariButtonSize.small,
                      icon: const Icon(Icons.delete_outline_rounded),
                      onPressed: busy ? null : () => onRemove(repository),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: HikariSpacing.sm),
      ],
    ],
  );
}
