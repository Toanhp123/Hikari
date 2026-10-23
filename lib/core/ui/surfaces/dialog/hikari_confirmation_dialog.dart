import 'package:flutter/material.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_controller.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/dialog/hikari_dialog.dart';

/// Interaction only. Feature must revalidate context and native authorization.
Future<HikariPresentationResult<bool>> showHikariConfirmationDialog({
  required BuildContext context,
  required bool useRootNavigator,
  required String title,
  required Widget content,
  required String confirmLabel,
  bool destructive = false,
  bool barrierDismissible = true,
}) => showHikariDialog<bool>(
  context: context,
  useRootNavigator: useRootNavigator,
  barrierDismissible: barrierDismissible,
  contentBuilder: (_, controller) => HikariConfirmationDialog(
    controller: controller,
    title: title,
    content: content,
    confirmLabel: confirmLabel,
    destructive: destructive,
  ),
);

class HikariConfirmationDialog extends StatelessWidget {
  const HikariConfirmationDialog({
    super.key,
    required this.controller,
    required this.title,
    required this.content,
    required this.confirmLabel,
    this.destructive = false,
  });
  final HikariPresentationController<bool> controller;
  final String title;
  final Widget content;
  final String confirmLabel;
  final bool destructive;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return HikariDialog(
      title: Text(title),
      body: content,
      onClose: () => controller.cancel(HikariDismissReason.closeButton),
      actions: [
        TextButton(
          autofocus: true,
          onPressed: () => controller.cancel(HikariDismissReason.cancelAction),
          child: const Text('Cancel'),
        ),
        FilledButton(
          style: destructive
              ? FilledButton.styleFrom(
                  backgroundColor: scheme.error,
                  foregroundColor: scheme.onError,
                )
              : null,
          onPressed: () => controller.complete(true),
          child: Text(confirmLabel),
        ),
      ],
    );
  }
}
