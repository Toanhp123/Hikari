import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';

class HomeHeader extends StatelessWidget {
  const HomeHeader({super.key, this.onSearch});

  final VoidCallback? onSearch;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        HikariSpacing.lg,
        HikariSpacing.xs,
        HikariSpacing.lg,
        HikariSpacing.sm,
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: [
              Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [colors.primary, colors.secondary],
                  ),
                  borderRadius: HikariRadius.borderSm,
                  boxShadow: [
                    BoxShadow(
                      color: colors.primary.withValues(alpha: 0.22),
                      blurRadius: 10,
                      offset: const Offset(0, 3),
                    ),
                  ],
                ),
                child: const Icon(
                  Icons.auto_awesome,
                  color: Colors.white,
                  size: 18,
                ),
              ),
              const SizedBox(width: HikariSpacing.sm),
              Text(
                'Hikari',
                style: HikariTypography.titleLarge.copyWith(
                  color: colors.textPrimary,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.2,
                ),
              ),
            ],
          ),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              HikariIconButton(
                tooltip: 'Search',
                onPressed: onSearch,
                icon: const Icon(Icons.search_rounded),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
