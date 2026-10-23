import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';

/// Bounded local content without a scroll owner. Sliver form preserves lazy
/// layout instead of wrapping a viewport in another scroll view.
class HikariContentRegion extends StatelessWidget {
  const HikariContentRegion({super.key, required this.child}) : sliver = null;
  const HikariContentRegion.sliver({super.key, required this.sliver})
    : child = null;
  final Widget? child;
  final Widget? sliver;

  double _gutter(double width) =>
      width < HikariBreakpoints.mediumMin ? HikariSpacing.lg : HikariSpacing.xl;

  @override
  Widget build(BuildContext context) {
    if (sliver != null) {
      return SliverLayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.crossAxisExtent;
          final inset = math.max(
            _gutter(width),
            (width - HikariBreakpoints.maxContentWidth) / 2,
          );
          return SliverPadding(
            padding: EdgeInsets.symmetric(horizontal: inset),
            sliver: sliver,
          );
        },
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) => Padding(
        padding: EdgeInsets.symmetric(
          horizontal: _gutter(constraints.maxWidth),
        ),
        child: Align(
          alignment: Alignment.topCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: HikariBreakpoints.maxContentWidth,
            ),
            child: child,
          ),
        ),
      ),
    );
  }
}
