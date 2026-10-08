import 'package:flutter/material.dart';

import 'hikari_radius.dart';
import 'hikari_size.dart';
import 'hikari_spacing.dart';
import 'hikari_colors.dart';
import 'hikari_breakpoints.dart';
import 'hikari_status_colors.dart';
import 'hikari_typography.dart';

export 'hikari_breakpoints.dart';
export 'hikari_colors.dart';
export 'hikari_motion.dart';
export 'hikari_radius.dart';
export 'hikari_size.dart';
export 'hikari_spacing.dart';
export 'hikari_status_colors.dart';
export 'hikari_typography.dart';

/// Canonical Material 3 theme for Hikari, including legacy-facing semantic roles.
abstract final class HikariTheme {
  /// The null appearance selection resolves to this canonical seed.
  static const defaultAccentSeed = Color(0xFFB4BEFE);

  /// User-facing preset names and raw seeds; generated primary is not a seed.
  static const accentPresets = <(String, Color)>[
    ('Violet', Color(0xFF8B5CF6)),
    ('Pink', Color(0xFFEC4899)),
    ('Cyan', Color(0xFF06B6D4)),
    ('Green', Color(0xFF10B981)),
    ('Amber', Color(0xFFF59E0B)),
  ];

  static ThemeData darkTheme({bool oled = false, Color? accentColor}) {
    final colors =
        ColorScheme.fromSeed(
          seedColor: accentColor ?? defaultAccentSeed,
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
      extensions: [
        statusColors,
        HikariColors.fromScheme(colors, statusColors: statusColors, oled: oled),
      ],
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
        titleTextStyle: text.titleLarge,
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

/// Existing feature API backed by the canonical theme extension.
extension HikariThemeContext on BuildContext {
  HikariColors get hikariColors {
    final theme = Theme.of(this);
    return theme.extension<HikariColors>() ??
        HikariColors.fromScheme(
          theme.colorScheme,
          statusColors:
              theme.extension<HikariStatusColors>() ?? HikariStatusColors.dark,
          oled: false,
        );
  }

  ThemeData get theme => Theme.of(this);
  TextTheme get textTheme => Theme.of(this).textTheme;
}
