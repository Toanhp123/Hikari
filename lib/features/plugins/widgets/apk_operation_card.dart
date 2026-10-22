import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';
import 'package:hikari/features/plugins/widgets/plugin_notice.dart';

/// Native journal evidence; no button here infers or changes operation success.
final class ApkOperationCard extends StatelessWidget {
  const ApkOperationCard({
    super.key,
    required this.operation,
    required this.enabled,
    required this.onResume,
    required this.onCancel,
    required this.onRetire,
  });

  final ApkInstallOperation operation;
  final bool enabled;
  final VoidCallback onResume;
  final VoidCallback onCancel;
  final VoidCallback onRetire;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(HikariSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Icon(Icons.history_rounded, color: colors.onSurfaceVariant),
                const SizedBox(width: HikariSpacing.sm),
                Expanded(
                  child: Text(
                    '${pluginEcosystemLabel(operation.pluginId.ecosystem)} · ${operation.pluginId.upstreamId}',
                    style: Theme.of(context).textTheme.titleSmall,
                  ),
                ),
              ],
            ),
            const SizedBox(height: HikariSpacing.sm),
            Text(
              '${pluginOperationLabel(operation.kind)} · ${apkInstallStatusLabel(operation.status)} · version ${operation.versionCode}',
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            if (operation.reason != null) ...[
              const SizedBox(height: HikariSpacing.sm),
              PluginNotice(message: 'Reason: ${operation.reason}', error: true),
            ],
            if (!operation.isTerminal) ...[
              const SizedBox(height: HikariSpacing.sm),
              Wrap(
                spacing: HikariSpacing.sm,
                runSpacing: HikariSpacing.xs,
                children: [
                  if (operation.kind != PluginOperation.uninstall &&
                      operation.status == ApkInstallStatus.pendingUserAction)
                    OutlinedButton(
                      onPressed: enabled ? onResume : null,
                      child: const Text('Resume Android confirmation'),
                    ),
                  if (operation.kind != PluginOperation.uninstall)
                    OutlinedButton(
                      onPressed: enabled ? onCancel : null,
                      child: const Text('Request cancellation'),
                    ),
                  OutlinedButton(
                    onPressed: enabled ? onRetire : null,
                    child: const Text('Retire unknown outcome'),
                  ),
                ],
              ),
            ],
            ExpansionTile(
              key: Key('apk-op-evidence-${operation.id}'),
              tilePadding: EdgeInsets.zero,
              childrenPadding: const EdgeInsets.only(bottom: HikariSpacing.sm),
              shape: const Border(),
              collapsedShape: const Border(),
              title: const Text('Technical evidence'),
              children: [
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: SelectableText(
                    'Signer: ${operation.signer}\nOperation: ${operation.id}',
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
