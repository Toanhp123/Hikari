import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_motion.dart';
import 'package:hikari/core/ui/foundation/hikari_radius.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/navigation/hikari_navigation_destination.dart';
import 'package:hikari/core/ui/navigation/hikari_navigation_policy.dart';
import 'package:hikari/core/ui/surfaces/drawer/hikari_drawer.dart';

/// Controlled navigation chrome; app owns selection and retained page state.
class HikariNavigationShell extends StatelessWidget {
  const HikariNavigationShell({
    super.key,
    required this.destinations,
    required this.selectedId,
    required this.onSelected,
    required this.body,
  });
  final List<HikariNavigationDestination> destinations;
  final String selectedId;
  final ValueChanged<String> onSelected;
  final Widget body;

  @override
  Widget build(BuildContext context) {
    final ids = destinations.map((entry) => entry.id).toSet();
    if (ids.length != destinations.length ||
        !ids.contains(selectedId) ||
        ids.contains('')) {
      throw ArgumentError(
        'Unique nonempty destination IDs and valid selection required.',
      );
    }
    final mode = resolveNavigationMode(
      MediaQuery.sizeOf(context).width,
      destinations.length,
    );
    return Scaffold(
      extendBody: true,
      backgroundColor: Colors.transparent,
      drawer: mode == HikariNavigationMode.drawer
          ? Builder(
              builder: (context) => HikariDrawer(
                destinations: destinations,
                selectedId: selectedId,
                onSelected: (id) {
                  Scaffold.of(context).closeDrawer();
                  onSelected(id);
                },
              ),
            )
          : null,
      body: Row(
        children: [
          if (mode == HikariNavigationMode.rail) _rail(context),
          Expanded(child: body),
        ],
      ),
      bottomNavigationBar: switch (mode) {
        HikariNavigationMode.bottomBar => _bottomBar(context),
        HikariNavigationMode.drawer => SafeArea(
          top: false,
          child: Builder(
            builder: (context) => Align(
              heightFactor: 1,
              alignment: AlignmentDirectional.centerStart,
              child: IconButton(
                tooltip: 'Open navigation menu',
                onPressed: () => Scaffold.of(context).openDrawer(),
                icon: const Icon(Icons.menu),
              ),
            ),
          ),
        ),
        HikariNavigationMode.rail => null,
      },
    );
  }

  Widget _bottomBar(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final metrics = TextPainter(
      text: TextSpan(text: 'Settings', style: theme.textTheme.labelSmall),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout();
    final height = math.max(72.0, 32 + 4 + metrics.height + 16);
    metrics.dispose();
    return RepaintBoundary(
      child: ClipRect(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
          child: Container(
            decoration: BoxDecoration(
              color: colors.surfaceContainerLowest.withValues(alpha: .88),
              border: Border(
                top: BorderSide(
                  color: colors.outlineVariant.withValues(alpha: .6),
                  width: HikariRadius.borderWidth,
                ),
              ),
            ),
            child: SafeArea(
              top: false,
              child: SizedBox(
                height: height,
                child: Row(
                  children: [
                    for (final destination in destinations)
                      Expanded(
                        child: Semantics(
                          button: true,
                          selected: selectedId == destination.id,
                          label: destination.label,
                          onTap: () => onSelected(destination.id),
                          child: ExcludeSemantics(
                            child: Tooltip(
                              message: destination.label,
                              child: InkWell(
                                onTap: () => onSelected(destination.id),
                                splashColor: colors.primary.withValues(
                                  alpha: .12,
                                ),
                                highlightColor: Colors.transparent,
                                child: SizedBox(
                                  height: height,
                                  child: _item(
                                    context,
                                    destination,
                                    rail: false,
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _item(
    BuildContext context,
    HikariNavigationDestination destination, {
    required bool rail,
  }) {
    final selected = destination.id == selectedId;
    final colors = Theme.of(context).colorScheme;
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      mainAxisSize: MainAxisSize.min,
      children: [
        AnimatedContainer(
          duration: HikariMotion.duration(context, HikariMotion.standard),
          curve: HikariMotion.curveStandard,
          width: selected ? (rail ? 56 : 60) : 40,
          height: 32,
          decoration: BoxDecoration(
            color: selected ? colors.secondaryContainer : Colors.transparent,
            borderRadius: HikariRadius.borderLg,
          ),
          alignment: Alignment.center,
          child: Icon(
            selected ? destination.selectedIcon : destination.icon,
            size: 22,
            color: selected
                ? colors.onSecondaryContainer
                : colors.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: HikariSpacing.xs),
        AnimatedDefaultTextStyle(
          duration: HikariMotion.duration(context, HikariMotion.standard),
          curve: HikariMotion.curveStandard,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: (Theme.of(context).textTheme.labelSmall ?? const TextStyle())
              .copyWith(
                fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
                letterSpacing: .2,
                color: selected ? colors.onSurface : colors.onSurfaceVariant,
              ),
          child: Text(destination.label),
        ),
      ],
    );
  }

  Widget _rail(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return RepaintBoundary(
      child: Container(
        width: 88,
        decoration: BoxDecoration(
          color: colors.surfaceContainerLow,
          border: BorderDirectional(
            end: BorderSide(
              color: colors.outlineVariant.withValues(alpha: .6),
              width: HikariRadius.borderWidth,
            ),
          ),
        ),
        child: SafeArea(
          left: Directionality.of(context) == TextDirection.ltr,
          right: Directionality.of(context) == TextDirection.rtl,
          child: Column(
            children: [
              const SizedBox(height: HikariSpacing.xl),
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [colors.primary, colors.secondary],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: HikariRadius.borderMd,
                  boxShadow: [
                    BoxShadow(
                      color: colors.primary.withValues(alpha: .25),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: Icon(
                  Icons.auto_awesome,
                  color: colors.onPrimary,
                  size: 22,
                ),
              ),
              const SizedBox(height: HikariSpacing.xxl),
              Expanded(
                child: ListView(
                  padding: EdgeInsets.zero,
                  children: [
                    for (final destination in destinations)
                      Padding(
                        padding: const EdgeInsets.symmetric(
                          vertical: HikariSpacing.xs,
                          horizontal: HikariSpacing.sm,
                        ),
                        child: IconButton(
                          isSelected: destination.id == selectedId,
                          tooltip: destination.label,
                          onPressed: () => onSelected(destination.id),
                          style: IconButton.styleFrom(
                            padding: const EdgeInsets.symmetric(
                              vertical: HikariSpacing.md,
                            ),
                            shape: const RoundedRectangleBorder(
                              borderRadius: HikariRadius.borderMd,
                            ),
                          ),
                          icon: _item(context, destination, rail: true),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
