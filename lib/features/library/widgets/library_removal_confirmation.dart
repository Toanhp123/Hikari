import 'package:flutter/material.dart';
import 'package:hikari/core/ui/components/hikari_confirmation_dialog.dart';

/// Confirmation shared by Library and every saved-state LibraryButton.
Future<bool> confirmLibraryRemoval(
  BuildContext context, {
  required String title,
}) async {
  return showHikariConfirmation(
    context: context,
    title: 'Remove from Library?',
    content: Text(
      'Remove "$title" from your saved titles? '
      'Reading progress is kept.',
    ),
    confirmLabel: 'Remove',
    destructive: true,
  );
}
