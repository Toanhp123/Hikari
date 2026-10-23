import 'package:flutter/material.dart';
import 'package:hikari/core/ui/hikari_ui.dart';

/// Membership wording belongs to the feature; the typed host owns dismissal.
Future<bool> confirmLibraryMembershipChange(
  BuildContext context, {
  required String title,
  required bool removing,
}) async {
  final result = await showHikariConfirmationDialog(
    context: context,
    useRootNavigator: true,
    title: removing ? 'Remove from Library?' : 'Add to Library?',
    content: Text(
      removing
          ? 'Remove "$title" from your saved titles? Reading progress is kept.'
          : 'Add "$title" to your saved titles?',
    ),
    confirmLabel: removing ? 'Remove' : 'Add',
    destructive: removing,
  );
  return result is HikariCompleted<bool> && result.value;
}
