import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_colors.dart';
import 'package:hikari/app/theme/hikari_radius.dart';
import 'package:hikari/app/theme/hikari_typography.dart';

export 'package:hikari/app/theme/hikari_breakpoints.dart';
export 'package:hikari/app/theme/hikari_colors.dart';
export 'package:hikari/app/theme/hikari_motion.dart';
export 'package:hikari/app/theme/hikari_radius.dart';
export 'package:hikari/app/theme/hikari_spacing.dart';
export 'package:hikari/app/theme/hikari_typography.dart';

/// Central theme builder for Hikari Cinematic Neo-Material design.
abstract final class HikariTheme {
  /// Builds the dark or OLED theme data for Hikari.
  static ThemeData darkTheme({bool oled = false, Color? accentColor}) {
    final baseColors = oled
        ? const HikariColors.oled()
        : const HikariColors.dark();
    final effectiveColors = accentColor != null
        ? baseColors.copyWith(
            primary: accentColor,
            primaryGlow: accentColor.withValues(alpha: 0.8),
          )
        : baseColors;

    final colorScheme = ColorScheme.dark(
      brightness: Brightness.dark,
      primary: effectiveColors.primary,
      onPrimary: effectiveColors.onPrimary,
      secondary: effectiveColors.secondary,
      onSecondary: Colors.white,
      surface: effectiveColors.surface,
      onSurface: effectiveColors.textPrimary,
      error: effectiveColors.error,
      onError: Colors.white,
      outline: effectiveColors.border,
      outlineVariant: effectiveColors.borderSubtle,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: effectiveColors.background,
      canvasColor: effectiveColors.surface,
      cardColor: effectiveColors.surfaceContainer,
      dividerColor: effectiveColors.border,
      fontFamily: HikariTypography.fontFamily,
      textTheme: HikariTypography.createTextTheme(
        effectiveColors.textPrimary,
        effectiveColors.textSecondary,
      ),
      appBarTheme: AppBarTheme(
        backgroundColor: effectiveColors.background,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: false,
        iconTheme: IconThemeData(color: effectiveColors.textPrimary),
        titleTextStyle: HikariTypography.titleLarge.copyWith(
          color: effectiveColors.textPrimary,
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: effectiveColors.surfaceElevated,
        shape: const RoundedRectangleBorder(
          borderRadius: HikariRadius.borderLg,
        ),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: effectiveColors.surfaceElevated,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(
            top: Radius.circular(HikariRadius.xl),
          ),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: effectiveColors.surfaceElevated,
        contentTextStyle: HikariTypography.bodyMedium.copyWith(
          color: effectiveColors.textPrimary,
        ),
        shape: const RoundedRectangleBorder(
          borderRadius: HikariRadius.borderSm,
        ),
        behavior: SnackBarBehavior.floating,
      ),
      tooltipTheme: TooltipThemeData(
        decoration: BoxDecoration(
          color: effectiveColors.surfaceElevated,
          borderRadius: HikariRadius.borderXs,
          border: Border.all(color: effectiveColors.border),
        ),
        textStyle: HikariTypography.labelSmall.copyWith(
          color: effectiveColors.textPrimary,
        ),
      ),
      extensions: <ThemeExtension<dynamic>>[effectiveColors],
    );
  }
}

/// Convenient extension on [BuildContext] to access Hikari theme tokens.
extension HikariThemeContext on BuildContext {
  /// Resolves [HikariColors] from the current theme, falling back to standard dark.
  HikariColors get hikariColors =>
      Theme.of(this).extension<HikariColors>() ?? const HikariColors.dark();

  /// Shortcut to [ThemeData]
  ThemeData get theme => Theme.of(this);

  /// Shortcut to [TextTheme]
  TextTheme get textTheme => Theme.of(this).textTheme;
}
