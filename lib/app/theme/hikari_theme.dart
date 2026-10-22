import 'package:flutter/material.dart';

import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';
import 'package:hikari/core/ui/foundation/hikari_radius.dart';
import 'package:hikari/core/ui/foundation/hikari_size.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';

import 'package:hikari/core/ui/foundation/hikari_color_roles.dart';
import 'package:hikari/core/ui/foundation/hikari_typography.dart';

/// Single Material 3 theme owner for Hikari.
abstract final class HikariTheme {
  static ThemeData darkTheme({bool oled = false, Color? accentColor}) {
    final colors =
        ColorScheme.fromSeed(
          seedColor: accentColor ?? HikariAccentSeeds.defaultSeed,
          brightness: Brightness.dark,
        ).copyWith(
          surface: oled ? Colors.black : const Color(0xFF181825),
          surfaceDim: oled ? Colors.black : const Color(0xFF181825),
          surfaceBright: const Color(0xFF383C59),
          surfaceContainerLowest: oled ? Colors.black : const Color(0xFF11111B),
          surfaceContainerLow: const Color(0xFF1E1E2E),
          surfaceContainer: const Color(0xFF252739),
          surfaceContainerHigh: const Color(0xFF2E3247),
          surfaceContainerHighest: const Color(0xFF383C59),
          onSurface: const Color(0xFFCDD6F4),
          onSurfaceVariant: const Color(0xFFA6ADC8),
          outline: const Color(0xFF6C7086),
          outlineVariant: const Color(0xFF313244),
        );
    const statusColors = HikariStatusColors.dark;
    final base = ThemeData(
      useMaterial3: true,
      colorScheme: colors,
      visualDensity: VisualDensity.standard,
      materialTapTargetSize: MaterialTapTargetSize.padded,
    );
    final text = base.textTheme
        .merge(HikariTypography.textTheme)
        .apply(bodyColor: colors.onSurface, displayColor: colors.onSurface);
    const shape = RoundedRectangleBorder(borderRadius: HikariRadius.borderMd);
    const minimumSize = Size(HikariSize.touchTarget, HikariSize.touchTarget);
    const padding = EdgeInsets.symmetric(
      horizontal: HikariSpacing.lg,
      vertical: HikariSpacing.md,
    );
    final border = OutlineInputBorder(
      borderRadius: HikariRadius.borderMd,
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
        thickness: HikariRadius.borderWidth,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: colors.surface,
        foregroundColor: colors.onSurface,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        titleTextStyle: text.titleLarge?.copyWith(fontWeight: FontWeight.w700),
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
        shape: const RoundedRectangleBorder(
          borderRadius: HikariRadius.borderLg,
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: colors.surfaceContainer,
        selectedColor: colors.secondaryContainer,
        secondarySelectedColor: colors.secondaryContainer,
        secondaryLabelStyle: chipLabelStyle,
        labelStyle: chipLabelStyle,
        shape: const StadiumBorder(),
        padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.sm),
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
            width: HikariRadius.focusWidth,
          ),
        ),
        errorBorder: border.copyWith(
          borderSide: BorderSide(color: colors.error),
        ),
        focusedErrorBorder: border.copyWith(
          borderSide: BorderSide(
            color: colors.error,
            width: HikariRadius.focusWidth,
          ),
        ),
      ),
      searchBarTheme: SearchBarThemeData(
        backgroundColor: WidgetStatePropertyAll(colors.surfaceContainer),
        elevation: const WidgetStatePropertyAll(0),
        shape: const WidgetStatePropertyAll(HikariRadius.pill),
        side: WidgetStatePropertyAll(
          BorderSide(
            color: colors.outlineVariant,
            width: HikariRadius.borderWidth,
          ),
        ),
        constraints: const BoxConstraints(minHeight: HikariSize.fieldMinHeight),
        textStyle: WidgetStatePropertyAll(text.bodyLarge),
        hintStyle: WidgetStatePropertyAll(
          text.bodyLarge!.copyWith(color: colors.onSurfaceVariant),
        ),
      ),
      listTileTheme: ListTileThemeData(
        minTileHeight: HikariSize.touchTarget,
        contentPadding: const EdgeInsets.symmetric(
          horizontal: HikariSpacing.lg,
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
          maxWidth: HikariBreakpoints.dialogMaxWidth,
        ),
        shape: const RoundedRectangleBorder(
          borderRadius: HikariRadius.borderXl,
        ),
        titleTextStyle: text.titleLarge?.copyWith(fontWeight: FontWeight.w700),
        contentTextStyle: text.bodyMedium,
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: colors.surfaceContainerHigh,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        showDragHandle: true,
        constraints: const BoxConstraints(
          maxWidth: HikariBreakpoints.sheetMaxWidth,
        ),
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(
            top: Radius.circular(HikariRadius.xl),
          ),
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
      sliderTheme: SliderThemeData(
        activeTrackColor: colors.primary,
        inactiveTrackColor: colors.surfaceContainerHighest,
        thumbColor: colors.primary.withValues(alpha: 0.8),
        overlayColor: colors.primary.withValues(alpha: 0.2),
        trackHeight: 3,
        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
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
}
