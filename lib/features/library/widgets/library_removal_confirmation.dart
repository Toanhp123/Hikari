import 'package:flutter/material.dart';

/// Confirmation shared by Library and every saved-state LibraryButton.
Future<bool> confirmLibraryRemoval(
  BuildContext context, {
  required String title,
}) async {
  final confirmed = await showDialog<bool>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: const Text('Remove from Library?'),
      content: Text(
        'Remove "$title" from your saved titles? '
        'Reading progress is kept.',
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(dialogContext, false),
          child: const Text('Cancel'),
        ),
        TextButton(
          onPressed: () => Navigator.pop(dialogContext, true),
          child: const Text('Remove'),
        ),
      ],
    ),
  );
  return confirmed == true;
}
