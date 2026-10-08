import 'package:flutter/material.dart';

import 'package:hikari/app/theme/hikari_theme.dart';

class HomeSectionLink extends StatelessWidget {
  const HomeSectionLink({super.key, required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return InkWell(
      onTap: onTap,
      borderRadius: HikariRadius.borderSm,
      child: ConstrainedBox(
        constraints: const BoxConstraints(
          minWidth: HikariSize.touchTarget,
          minHeight: HikariSize.touchTarget,
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.xs),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                label,
                style: (theme.textTheme.labelMedium ?? const TextStyle())
                    .copyWith(
                      color: colors.primary,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.2,
                    ),
              ),
              const SizedBox(width: 2),
              Icon(
                Icons.chevron_right_rounded,
                size: 19,
                color: colors.primary,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
