import 'package:flutter/material.dart';

import 'package:hikari/app/theme/design_system/design_system.dart';

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
    final colors = theme.colorScheme;
    final statusColors = theme.extension<HikariStatusColors>();
    final warningColor =
        statusColors?.onWarningContainer ?? const Color(0xFFFAB387);
    final warningBg =
        statusColors?.warningContainer ?? warningColor.withValues(alpha: 0.12);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: HikariSpace.content),
      child: Material(
        color: warningBg,
        shape: RoundedRectangleBorder(
          borderRadius: HikariShape.large,
          side: BorderSide(color: warningColor.withValues(alpha: 0.28)),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: HikariSpace.content,
            vertical: HikariSpace.inline,
          ),
          child: Row(
            children: [
              Icon(icon, size: 20, color: warningColor),
              const SizedBox(width: HikariSpace.inline),
              Expanded(
                child: Text(
                  message,
                  style: (theme.textTheme.bodySmall ?? const TextStyle())
                      .copyWith(color: colors.onSurfaceVariant),
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
