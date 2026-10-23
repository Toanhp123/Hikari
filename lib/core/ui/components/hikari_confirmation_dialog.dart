import 'package:flutter/material.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/dialog/hikari_confirmation_dialog.dart';

/// Temporary legacy adapter. Remove after feature migration, deadline 07/01.
/// Preserves historical root Navigator and false for every non-confirm exit.
Future<bool> showHikariConfirmation({
  required BuildContext context,
  required String title,
  required Widget content,
  required String confirmLabel,
  bool destructive = false,
  bool barrierDismissible = true,
}) async {
  final result = await showHikariConfirmationDialog(
    context: context,
    useRootNavigator: true,
    title: title,
    content: content,
    confirmLabel: confirmLabel,
    destructive: destructive,
    barrierDismissible: barrierDismissible,
  );
  return result is HikariCompleted<bool> && result.value;
}
