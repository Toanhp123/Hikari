import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';

/// Lazy grid with one local geometry policy. Sliver variant borrows its viewport.
class HikariAdaptiveGrid extends StatelessWidget {
  const HikariAdaptiveGrid.builder({
    super.key,
    required this.itemCount,
    required this.itemBuilder,
    required this.itemExtentBuilder,
    this.itemKeyBuilder,
    this.maxItemWidth = HikariBreakpoints.posterGridMaxExtent,
    this.spacing = HikariSpacing.md,
    this.padding = const EdgeInsets.all(HikariSpacing.lg),
    this.controller,
    this.physics,
  }) : assert(itemCount >= 0),
       assert(maxItemWidth > 0 && maxItemWidth < double.infinity),
       assert(spacing >= 0 && spacing < double.infinity),
       sliver = false;
  const HikariAdaptiveGrid.sliver({
    super.key,
    required this.itemCount,
    required this.itemBuilder,
    required this.itemExtentBuilder,
    this.itemKeyBuilder,
    this.maxItemWidth = HikariBreakpoints.posterGridMaxExtent,
    this.spacing = HikariSpacing.md,
    this.padding = const EdgeInsets.all(HikariSpacing.lg),
  }) : assert(itemCount >= 0),
       assert(maxItemWidth > 0 && maxItemWidth < double.infinity),
       assert(spacing >= 0 && spacing < double.infinity),
       sliver = true,
       controller = null,
       physics = null;
  final int itemCount;
  final IndexedWidgetBuilder itemBuilder;
  final Key Function(int)? itemKeyBuilder;
  final double Function(BuildContext, double) itemExtentBuilder;
  final double maxItemWidth;
  final double spacing;
  final EdgeInsetsGeometry padding;
  final ScrollController? controller;
  final ScrollPhysics? physics;
  final bool sliver;

  @override
  Widget build(BuildContext context) {
    // Build the O(n) key map only when a grid can actually render items.
    SliverChildBuilderDelegate buildChildren() {
      final keys = itemKeyBuilder;
      final indices = keys == null
          ? null
          : <Key, int>{for (var i = 0; i < itemCount; i++) keys(i): i};
      assert(
        indices == null || indices.length == itemCount,
        'Media keys must be unique.',
      );
      return SliverChildBuilderDelegate(
        (context, index) => keys == null
            ? itemBuilder(context, index)
            : KeyedSubtree(key: keys(index), child: itemBuilder(context, index)),
        childCount: itemCount,
        findChildIndexCallback: indices == null ? null : (key) => indices[key],
      );
    }
    final resolvedPadding = padding.resolve(Directionality.of(context));
    SliverGridDelegate geometry(double width) {
      final usable = math.max(0.0, width - resolvedPadding.horizontal);
      final count = math.max(
        1,
        ((usable + spacing) / (maxItemWidth + spacing)).ceil(),
      );
      final itemWidth = math.max(0.0, (usable - spacing * (count - 1)) / count);
      final extent = itemExtentBuilder(context, itemWidth);
      return SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: count,
        crossAxisSpacing: spacing,
        mainAxisSpacing: spacing,
        mainAxisExtent: extent.isFinite && extent > 0 ? extent : 1,
      );
    }

    if (sliver) {
      return SliverLayoutBuilder(
        builder: (context, constraints) {
          // Positive outer width can still have zero usable space after gutters.
          if (!constraints.crossAxisExtent.isFinite ||
              constraints.crossAxisExtent <= resolvedPadding.horizontal) {
            return const SliverToBoxAdapter();
          }
          return SliverPadding(
            padding: padding,
            sliver: SliverGrid(
              delegate: buildChildren(),
              gridDelegate: geometry(constraints.crossAxisExtent),
            ),
          );
        },
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        if (!constraints.maxWidth.isFinite ||
            constraints.maxWidth <= resolvedPadding.horizontal) {
          return const SizedBox.shrink();
        }
        return GridView.custom(
          controller: controller,
          physics: physics,
          padding: padding,
          gridDelegate: geometry(constraints.maxWidth),
          childrenDelegate: buildChildren(),
        );
      },
    );
  }
}
