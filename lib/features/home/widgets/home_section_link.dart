import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

class HomeSectionLink extends StatelessWidget {
  const HomeSectionLink({super.key, required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    return InkWell(
      onTap: onTap,
      borderRadius: HikariRadius.borderSm,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 48),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.xs),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                label,
                style: HikariTypography.labelMedium.copyWith(
                  color: colors.primaryGlow,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(width: 2),
              Icon(
                Icons.chevron_right_rounded,
                size: 19,
                color: colors.primaryGlow,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
