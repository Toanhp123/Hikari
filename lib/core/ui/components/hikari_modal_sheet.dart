import 'package:flutter/material.dart';

/// Opens a Material sheet with Hikari's common safe-area and drag behavior.
///
/// The caller owns its scrollable body, height constraints, keyboard insets
/// and the result.
/// This is intentionally a thin route helper, not a modal workflow framework.
Future<T?> showHikariModalSheet<T>({
  required BuildContext context,
  required WidgetBuilder builder,
  Color? backgroundColor,
  BoxConstraints? constraints,
  bool isDismissible = true,
  bool enableDrag = true,
}) => showModalBottomSheet<T>(
  context: context,
  backgroundColor: backgroundColor,
  constraints: constraints,
  isScrollControlled: true,
  useSafeArea: true,
  showDragHandle: enableDrag,
  isDismissible: isDismissible,
  enableDrag: enableDrag,
  builder: builder,
);
