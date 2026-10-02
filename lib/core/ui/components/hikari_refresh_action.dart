import 'package:flutter/material.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';

/// Explicit refresh action shared by refreshable presentation surfaces.
class HikariRefreshAction extends StatelessWidget {
  const HikariRefreshAction({
    super.key,
    required this.onPressed,
    required this.tooltip,
    this.refreshing = false,
  });

  final VoidCallback? onPressed;
  final bool refreshing;
  final String tooltip;

  @override
  Widget build(BuildContext context) {
    return HikariIconButton(
      tooltip: tooltip,
      onPressed: refreshing ? null : onPressed,
      icon: refreshing
          ? const SizedBox.square(
              dimension: 18,
              child: CircularProgressIndicator.adaptive(strokeWidth: 2),
            )
          : const Icon(Icons.refresh_rounded),
    );
  }
}
