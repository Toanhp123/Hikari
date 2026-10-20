import 'package:flutter/material.dart';

/// A presentation-only confirmation surface. The caller owns authorization,
/// cancellation guards, state revalidation and the eventual operation.
///
/// Closing with Back, the barrier or Cancel is never interpreted as consent.
Future<bool> showHikariConfirmation({
  required BuildContext context,
  required String title,
  required Widget content,
  required String confirmLabel,
  bool destructive = false,
  bool barrierDismissible = true,
}) async {
  final result = await showDialog<bool>(
    context: context,
    barrierDismissible: barrierDismissible,
    builder: (dialogContext) => HikariConfirmationDialog(
      title: title,
      content: content,
      confirmLabel: confirmLabel,
      destructive: destructive,
    ),
  );
  return result == true;
}

/// Native Material layout, actions and scrolling; no feature/business logic.
class HikariConfirmationDialog extends StatelessWidget {
  const HikariConfirmationDialog({
    super.key,
    required this.title,
    required this.content,
    required this.confirmLabel,
    this.destructive = false,
  });

  final String title;
  final Widget content;
  final String confirmLabel;
  final bool destructive;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return AlertDialog(
      title: Text(title),
      content: content,
      scrollable: true,
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: const Text('Cancel'),
        ),
        FilledButton(
          style: destructive
              ? FilledButton.styleFrom(
                  backgroundColor: scheme.error,
                  foregroundColor: scheme.onError,
                )
              : null,
          onPressed: () => Navigator.of(context).pop(true),
          child: Text(confirmLabel),
        ),
      ],
    );
  }
}
