import 'package:flutter/material.dart';

import 'package:hikari/app/theme/hikari_theme.dart';

class HomeWarningNotice extends StatelessWidget {
  const HomeWarningNotice({
    super.key,
    required this.icon,
    required this.message,
    required this.onRetry,
  });

  final IconData icon;
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final statusColors = context.hikariStatusColors;
    final warningColor = statusColors.onWarningContainer;
    final warningBg = statusColors.warningContainer;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
      child: Material(
        color: warningBg,
        shape: RoundedRectangleBorder(
          borderRadius: HikariRadius.borderLg,
          side: BorderSide(color: warningColor.withValues(alpha: 0.28)),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: HikariSpacing.lg,
            vertical: HikariSpacing.sm,
          ),
          child: Row(
            children: [
              Icon(icon, size: 20, color: warningColor),
              const SizedBox(width: HikariSpacing.sm),
              Expanded(
                child: Text(
                  message,
                  style: (theme.textTheme.bodySmall ?? const TextStyle())
                      .copyWith(color: statusColors.onWarningContainer),
                ),
              ),
              TextButton(onPressed: onRetry, child: const Text('Retry')),
            ],
          ),
        ),
      ),
    );
  }
}
