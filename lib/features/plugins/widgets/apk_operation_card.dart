import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';

/// One native journal row; buttons emit intents, never interpret native success.
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
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(HikariSpacing.md),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SelectableText(
            '${pluginEcosystemLabel(operation.pluginId.ecosystem)} · ${operation.pluginId.upstreamId}',
          ),
          SelectableText(
            '${pluginOperationLabel(operation.kind)} · ${apkInstallStatusLabel(operation.status)} · version ${operation.versionCode}\nSigner: ${operation.signer}\nOperation: ${operation.id}',
          ),
          if (operation.reason != null) Text('Reason: ${operation.reason}'),
          if (!operation.isTerminal)
            Wrap(
              spacing: HikariSpacing.sm,
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
      ),
    ),
  );
}
