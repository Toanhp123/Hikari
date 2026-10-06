import 'package:flutter/material.dart';

import 'geometry.dart';
import 'layout.dart';
import 'status_colors.dart';
import 'typography.dart';

/// Opt-in target theme. The legacy HikariTheme remains wired until migration.
abstract final class HikariDesignTheme {
  static ThemeData dark({bool oled = false, Color? accentSeed}) {
    final colors =
        ColorScheme.fromSeed(
          seedColor: accentSeed ?? const Color(0xFF8B5CF6),
          brightness: Brightness.dark,
        ).copyWith(
          surface: oled ? Colors.black : const Color(0xFF0B0F17),
          surfaceDim: oled ? Colors.black : const Color(0xFF0B0F17),
          surfaceBright: const Color(0xFF2A3752),
          surfaceContainerLowest: oled ? Colors.black : const Color(0xFF080B12),
          surfaceContainerLow: const Color(0xFF121620),
          surfaceContainer: const Color(0xFF161B26),
          surfaceContainerHigh: const Color(0xFF1F293D),
          surfaceContainerHighest: const Color(0xFF2A3752),
          onSurface: const Color(0xFFF8FAFC),
          onSurfaceVariant: const Color(0xFFB8C2D1),
          outline: const Color(0xFF8896AA),
          outlineVariant: const Color(0xFF354158),
        );
    const statusColors = HikariStatusColors(
      warningContainer: Color(0xFF3D2B12),
      onWarningContainer: Color(0xFFFFE0A3),
      infoContainer: Color(0xFF102E40),
      onInfoContainer: Color(0xFFB9E5FF),
    );
    final base = ThemeData(
      useMaterial3: true,
      colorScheme: colors,
      visualDensity: VisualDensity.standard,
      materialTapTargetSize: MaterialTapTargetSize.padded,
    );
    final text = base.textTheme
        .merge(HikariDesignTypography.textTheme)
        .apply(bodyColor: colors.onSurface, displayColor: colors.onSurface);
    const shape = RoundedRectangleBorder(borderRadius: HikariShape.medium);
    const minimumSize = Size(HikariSize.touchTarget, HikariSize.touchTarget);
    const padding = EdgeInsets.symmetric(
      horizontal: HikariSpace.content,
      vertical: HikariSpace.compact,
    );
    final border = OutlineInputBorder(
      borderRadius: HikariShape.medium,
      borderSide: BorderSide(color: colors.outline),
    );

    final chipLabelStyle = text.labelLarge!.copyWith(
      color: WidgetStateColor.resolveWith((states) {
        if (states.contains(WidgetState.disabled)) {
          return colors.onSurface.withValues(alpha: 0.38);
        }
        return states.contains(WidgetState.selected)
            ? colors.onSecondaryContainer
            : colors.onSurfaceVariant;
      }),
    );

    return base.copyWith(
      extensions: [statusColors],
      scaffoldBackgroundColor: colors.surface,
      textTheme: text,
      iconTheme: IconThemeData(
        color: colors.onSurfaceVariant,
        size: HikariSize.icon,
      ),
      dividerTheme: DividerThemeData(
        color: colors.outlineVariant,
        thickness: HikariShape.borderWidth,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: colors.surface,
        foregroundColor: colors.onSurface,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: text.titleLarge,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: minimumSize,
          padding: padding,
          shape: shape,
          foregroundColor: colors.onPrimary,
          backgroundColor: colors.primary,
          textStyle: text.labelLarge,
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: minimumSize,
          padding: padding,
          shape: shape,
          foregroundColor: colors.primary,
          side: BorderSide(color: colors.outline),
          textStyle: text.labelLarge,
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          minimumSize: minimumSize,
          padding: padding,
          shape: shape,
          textStyle: text.labelLarge,
        ),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: IconButton.styleFrom(
          minimumSize: minimumSize,
          iconSize: HikariSize.icon,
        ),
      ),
      cardTheme: CardThemeData(
        color: colors.surfaceContainer,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: shape,
      ),
      chipTheme: ChipThemeData(
        backgroundColor: colors.surfaceContainer,
        selectedColor: colors.secondaryContainer,
        secondarySelectedColor: colors.secondaryContainer,
        secondaryLabelStyle: chipLabelStyle,
        labelStyle: chipLabelStyle,
        shape: const RoundedRectangleBorder(borderRadius: HikariShape.small),
        padding: const EdgeInsets.symmetric(horizontal: HikariSpace.inline),
      ),
      inputDecorationTheme: InputDecorationThemeData(
        filled: true,
        fillColor: colors.surfaceContainer,
        constraints: const BoxConstraints(minHeight: HikariSize.fieldMinHeight),
        contentPadding: padding,
        labelStyle: TextStyle(color: colors.onSurfaceVariant),
        hintStyle: TextStyle(color: colors.onSurfaceVariant),
        border: border,
        enabledBorder: border,
        focusedBorder: border.copyWith(
          borderSide: BorderSide(
            color: colors.primary,
            width: HikariShape.focusWidth,
          ),
        ),
        errorBorder: border.copyWith(
          borderSide: BorderSide(color: colors.error),
        ),
        focusedErrorBorder: border.copyWith(
          borderSide: BorderSide(
            color: colors.error,
            width: HikariShape.focusWidth,
          ),
        ),
      ),
      searchBarTheme: SearchBarThemeData(
        backgroundColor: WidgetStatePropertyAll(colors.surfaceContainer),
        elevation: const WidgetStatePropertyAll(0),
        shape: const WidgetStatePropertyAll(shape),
        constraints: const BoxConstraints(minHeight: HikariSize.fieldMinHeight),
        textStyle: WidgetStatePropertyAll(text.bodyLarge),
        hintStyle: WidgetStatePropertyAll(
          text.bodyLarge!.copyWith(color: colors.onSurfaceVariant),
        ),
      ),
      listTileTheme: ListTileThemeData(
        minTileHeight: HikariSize.touchTarget,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: HikariSpace.content,
        ),
        titleTextStyle: text.bodyLarge,
        subtitleTextStyle: text.bodyMedium!.copyWith(
          color: colors.onSurfaceVariant,
        ),
        iconColor: colors.onSurfaceVariant,
        selectedColor: colors.onSecondaryContainer,
        selectedTileColor: colors.secondaryContainer,
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: colors.surfaceContainerHigh,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        constraints: const BoxConstraints(
          maxWidth: HikariLayout.dialogMaxWidth,
        ),
        shape: const RoundedRectangleBorder(
          borderRadius: HikariShape.extraLarge,
        ),
        titleTextStyle: text.titleLarge,
        contentTextStyle: text.bodyMedium,
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: colors.surfaceContainerHigh,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        showDragHandle: true,
        constraints: const BoxConstraints(maxWidth: HikariLayout.sheetMaxWidth),
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: colors.surfaceContainerLow,
        indicatorColor: colors.secondaryContainer,
        elevation: 0,
      ),
      navigationRailTheme: NavigationRailThemeData(
        backgroundColor: colors.surfaceContainerLow,
        indicatorColor: colors.secondaryContainer,
        useIndicator: true,
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: colors.inverseSurface,
        contentTextStyle: text.bodyMedium!.copyWith(
          color: colors.onInverseSurface,
        ),
        actionTextColor: colors.inversePrimary,
      ),
    );
  }

  /// Apply to a native FilledButton; inherited geometry and states remain intact.
  static ButtonStyle destructiveAction(ColorScheme colors) =>
      FilledButton.styleFrom(
        backgroundColor: colors.error,
        foregroundColor: colors.onError,
        disabledBackgroundColor: colors.onSurface.withValues(alpha: 0.12),
        disabledForegroundColor: colors.onSurface.withValues(alpha: 0.38),
      );
}
