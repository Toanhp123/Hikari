import 'package:flutter/material.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_controller.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/dialog/hikari_dialog.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';

typedef ApkRetirementChoice = ({
  bool abandonOwnedSession,
  bool acknowledgeUnrevokedRemoval,
});

/// Explicit user acknowledgments, not proof of native retirement or revocation.
class ApkRetirementDialog extends StatefulWidget {
  const ApkRetirementDialog({
    super.key,
    required this.operation,
    required this.controller,
  });
  final ApkInstallOperation operation;
  final HikariPresentationController<ApkRetirementChoice> controller;

  @override
  State<ApkRetirementDialog> createState() => _ApkRetirementDialogState();
}

class _ApkRetirementDialogState extends State<ApkRetirementDialog> {
  bool _abandon = false;
  bool _acknowledged = false;

  @override
  Widget build(BuildContext context) {
    final displayed = widget.operation;
    final removal = displayed.kind == PluginOperation.uninstall;
    return HikariDialog(
      title: const Text('Retire unknown outcome?'),
      onClose: () => widget.controller.cancel(HikariDismissReason.closeButton),
      body: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          SelectableText(
            '${displayed.pluginId.upstreamId} · ${displayed.versionCode} · ${displayed.signer}',
          ),
          const Text(
            'Outcome remains unknown, not success or cancellation. Native authority must verify no owned session remains.',
          ),
          CheckboxListTile(
            value: _abandon,
            onChanged: (value) => setState(() => _abandon = value!),
            title: const Text('Abandon remaining owned install sessions'),
          ),
          if (removal) ...[
            const Text(
              'Old system removal dialog cannot be revoked and could remove a replacement. Dismiss it before retiring this operation.',
            ),
            CheckboxListTile(
              value: _acknowledged,
              onChanged: (value) => setState(() => _acknowledged = value!),
              title: const Text('I dismissed the old system removal dialog'),
            ),
          ],
        ],
      ),
      actions: [
        TextButton(
          autofocus: true,
          onPressed: () =>
              widget.controller.cancel(HikariDismissReason.cancelAction),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: removal && !_acknowledged
              ? null
              : () => widget.controller.complete((
                  abandonOwnedSession: _abandon,
                  acknowledgeUnrevokedRemoval: _acknowledged,
                )),
          child: const Text('Retire operation'),
        ),
      ],
    );
  }
}
