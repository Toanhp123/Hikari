import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

/// Rows remain lazy and each InkWell paints on its own rounded Material.
class CatalogSourcePickerListSurface extends StatelessWidget {
  const CatalogSourcePickerListSurface({
    super.key,
    required this.index,
    required this.count,
    required this.child,
  });

  final int index;
  final int count;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final radius = BorderRadius.vertical(
      top: index == 0 ? const Radius.circular(HikariRadius.lg) : Radius.zero,
      bottom: index == count - 1
          ? const Radius.circular(HikariRadius.lg)
          : Radius.zero,
    );
    return Material(
      color: colors.surfaceContainer,
      shape: RoundedRectangleBorder(
        borderRadius: radius,
        side: BorderSide(color: colors.outlineVariant, width: 0.5),
      ),
      clipBehavior: Clip.antiAlias,
      child: child,
    );
  }
}
