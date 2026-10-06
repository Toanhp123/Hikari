import 'package:flutter/material.dart';

import 'package:hikari/app/theme/design_system/design_system.dart';

class HomeHeader extends StatelessWidget {
  const HomeHeader({super.key, this.onSearch});

  final VoidCallback? onSearch;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    return Padding(
      padding: const EdgeInsets.fromLTRB(
        HikariSpace.content,
        HikariSpace.inline,
        HikariSpace.content,
        HikariSpace.compact,
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [colors.primary, colors.secondary],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: HikariShape.medium,
                  boxShadow: [
                    BoxShadow(
                      color: colors.primary.withValues(alpha: 0.22),
                      blurRadius: 10,
                      offset: const Offset(0, 3),
                    ),
                  ],
                ),
                child: Icon(
                  Icons.auto_awesome,
                  color: colors.onPrimary,
                  size: 20,
                ),
              ),
              const SizedBox(width: HikariSpace.compact),
              Text(
                'Hikari',
                style: (theme.textTheme.titleLarge ?? const TextStyle())
                    .copyWith(
                      color: colors.onSurface,
                      fontWeight: FontWeight.w800,
                      letterSpacing: -0.3,
                    ),
              ),
            ],
          ),
          if (onSearch != null)
            IconButton(
              tooltip: 'Search',
              onPressed: onSearch,
              icon: Icon(
                Icons.search_rounded,
                color: colors.onSurfaceVariant,
                size: 24,
              ),
            ),
        ],
      ),
    );
  }
}
