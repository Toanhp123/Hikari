import 'dart:ui' show ImageFilter;

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

/// Pinned glassmorphic header for Home with dynamic gradient blur on scroll.
class HomeHeader extends StatelessWidget {
  const HomeHeader({super.key, this.onSearch});

  final VoidCallback? onSearch;

  @override
  Widget build(BuildContext context) {
    return SliverPersistentHeader(
      pinned: true,
      delegate: _HomeHeaderDelegate(onSearch: onSearch),
    );
  }
}

class _HomeHeaderDelegate extends SliverPersistentHeaderDelegate {
  const _HomeHeaderDelegate({this.onSearch});

  final VoidCallback? onSearch;

  static const double _headerHeight = 58.0;

  @override
  double get minExtent => _headerHeight;

  @override
  double get maxExtent => _headerHeight;

  @override
  Widget build(
    BuildContext context,
    double shrinkOffset,
    bool overlapsContent,
  ) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;

    // Smooth transition over the first 32 pixels of scroll
    final progress = (shrinkOffset / 32.0).clamp(0.0, 1.0);
    final isScrolled = progress > 0.0;

    Widget content = DecoratedBox(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            colors.surface.withValues(alpha: 0.88 * progress),
            colors.surface.withValues(alpha: 0.65 * progress),
          ],
        ),
        border: Border(
          bottom: BorderSide(
            color: colors.outlineVariant.withValues(alpha: 0.35 * progress),
            width: HikariRadius.borderWidth,
          ),
        ),
      ),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: HikariBreakpoints.maxContentWidth,
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Semantics(
                  headingLevel: 1,
                  child: Row(
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
                          borderRadius: HikariRadius.borderMd,
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
                      const SizedBox(width: HikariSpacing.md),
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
                ),
                if (onSearch != null)
                  IconButton.filledTonal(
                    tooltip: 'Search',
                    style: IconButton.styleFrom(
                      backgroundColor: colors.surfaceContainerHighest
                          .withValues(alpha: 0.5 + (0.3 * progress)),
                      foregroundColor: colors.onSurface,
                      minimumSize: const Size(
                        HikariSize.touchTarget,
                        HikariSize.touchTarget,
                      ),
                    ),
                    onPressed: onSearch,
                    icon: const Icon(
                      Icons.search_rounded,
                      size: HikariSize.icon,
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );

    if (isScrolled) {
      content = ClipRect(
        child: BackdropFilter(
          filter: ImageFilter.blur(
            sigmaX: 16.0 * progress,
            sigmaY: 16.0 * progress,
          ),
          child: content,
        ),
      );
    }

    return RepaintBoundary(child: content);
  }

  @override
  bool shouldRebuild(covariant _HomeHeaderDelegate oldDelegate) {
    return oldDelegate.onSearch != onSearch;
  }
}
