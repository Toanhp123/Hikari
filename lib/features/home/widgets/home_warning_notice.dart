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
    final colors = context.hikariColors;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
      child: Material(
        color: colors.warning.withValues(alpha: 0.08),
        shape: RoundedRectangleBorder(
          borderRadius: HikariRadius.borderMd,
          side: BorderSide(color: colors.warning.withValues(alpha: 0.28)),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: HikariSpacing.md,
            vertical: HikariSpacing.sm,
          ),
          child: Row(
            children: [
              Icon(icon, size: 20, color: colors.warning),
              const SizedBox(width: HikariSpacing.sm),
              Expanded(
                child: Text(
                  message,
                  style: HikariTypography.bodySmall.copyWith(
                    color: colors.textSecondary,
                  ),
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
