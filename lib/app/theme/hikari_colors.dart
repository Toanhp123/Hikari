import 'package:flutter/material.dart';

import 'hikari_status_colors.dart';

/// Legacy semantic role adapter for Hikari's canonical Material ColorScheme.
@immutable
class HikariColors extends ThemeExtension<HikariColors> {
  const HikariColors({
    required this.background,
    required this.surface,
    required this.surfaceContainer,
    required this.surfaceElevated,
    required this.surfaceHighlight,
    required this.border,
    required this.borderSubtle,
    required this.glassSurface,
    required this.glassBorder,
    required this.scrimMedium,
    required this.scrimStrong,
    required this.primary,
    required this.primaryGlow,
    required this.secondary,
    required this.badgeVideo,
    required this.badgeManga,
    required this.badgeNovel,
    required this.textPrimary,
    required this.textSecondary,
    required this.textMuted,
    required this.onPrimary,
    required this.success,
    required this.warning,
    required this.error,
    required this.info,
    required this.isOled,
  });

  /// Legacy-facing roles are derived from the same semantic ColorScheme
  /// that drives all native controls. No independent legacy color palette.
  factory HikariColors.fromScheme(
    ColorScheme scheme, {
    required HikariStatusColors statusColors,
    required bool oled,
  }) => HikariColors(
    background: scheme.surface,
    surface: scheme.surfaceContainerLow,
    surfaceContainer: scheme.surfaceContainer,
    surfaceElevated: scheme.surfaceContainerHigh,
    surfaceHighlight: scheme.surfaceContainerHighest,
    border: scheme.outline,
    borderSubtle: scheme.outlineVariant,
    glassSurface: scheme.surface.withValues(alpha: oled ? 0.92 : 0.85),
    glassBorder: scheme.outlineVariant.withValues(alpha: 0.4),
    scrimMedium: scheme.scrim.withValues(alpha: 0.6),
    scrimStrong: scheme.scrim.withValues(alpha: 0.8),
    primary: scheme.primary,
    primaryGlow: scheme.primary.withValues(alpha: 0.8),
    secondary: scheme.secondary,
    badgeVideo: scheme.primary,
    badgeManga: statusColors.onWarningContainer,
    badgeNovel: statusColors.onInfoContainer,
    textPrimary: scheme.onSurface,
    textSecondary: scheme.onSurfaceVariant,
    textMuted: scheme.onSurfaceVariant,
    onPrimary: scheme.onPrimary,
    success: const Color(0xFFA6E3A1),
    warning: statusColors.onWarningContainer,
    error: scheme.error,
    info: statusColors.onInfoContainer,
    isOled: oled,
  );

  final Color background;
  final Color surface;
  final Color surfaceContainer;
  final Color surfaceElevated;
  final Color surfaceHighlight;
  final Color border;
  final Color borderSubtle;
  final Color glassSurface;
  final Color glassBorder;
  final Color scrimMedium;
  final Color scrimStrong;
  final Color primary;
  final Color primaryGlow;
  final Color secondary;
  final Color badgeVideo;
  final Color badgeManga;
  final Color badgeNovel;
  final Color textPrimary;
  final Color textSecondary;
  final Color textMuted;
  final Color onPrimary;
  final Color success;
  final Color warning;
  final Color error;
  final Color info;
  final bool isOled;

  @override
  HikariColors copyWith({
    Color? background,
    Color? surface,
    Color? surfaceContainer,
    Color? surfaceElevated,
    Color? surfaceHighlight,
    Color? border,
    Color? borderSubtle,
    Color? glassSurface,
    Color? glassBorder,
    Color? scrimMedium,
    Color? scrimStrong,
    Color? primary,
    Color? primaryGlow,
    Color? secondary,
    Color? badgeVideo,
    Color? badgeManga,
    Color? badgeNovel,
    Color? textPrimary,
    Color? textSecondary,
    Color? textMuted,
    Color? onPrimary,
    Color? success,
    Color? warning,
    Color? error,
    Color? info,
    bool? isOled,
  }) {
    return HikariColors(
      background: background ?? this.background,
      surface: surface ?? this.surface,
      surfaceContainer: surfaceContainer ?? this.surfaceContainer,
      surfaceElevated: surfaceElevated ?? this.surfaceElevated,
      surfaceHighlight: surfaceHighlight ?? this.surfaceHighlight,
      border: border ?? this.border,
      borderSubtle: borderSubtle ?? this.borderSubtle,
      glassSurface: glassSurface ?? this.glassSurface,
      glassBorder: glassBorder ?? this.glassBorder,
      scrimMedium: scrimMedium ?? this.scrimMedium,
      scrimStrong: scrimStrong ?? this.scrimStrong,
      primary: primary ?? this.primary,
      primaryGlow: primaryGlow ?? this.primaryGlow,
      secondary: secondary ?? this.secondary,
      badgeVideo: badgeVideo ?? this.badgeVideo,
      badgeManga: badgeManga ?? this.badgeManga,
      badgeNovel: badgeNovel ?? this.badgeNovel,
      textPrimary: textPrimary ?? this.textPrimary,
      textSecondary: textSecondary ?? this.textSecondary,
      textMuted: textMuted ?? this.textMuted,
      onPrimary: onPrimary ?? this.onPrimary,
      success: success ?? this.success,
      warning: warning ?? this.warning,
      error: error ?? this.error,
      info: info ?? this.info,
      isOled: isOled ?? this.isOled,
    );
  }

  @override
  HikariColors lerp(ThemeExtension<HikariColors>? other, double t) {
    if (other is! HikariColors) return this;
    return HikariColors(
      background: Color.lerp(background, other.background, t) ?? background,
      surface: Color.lerp(surface, other.surface, t) ?? surface,
      surfaceContainer:
          Color.lerp(surfaceContainer, other.surfaceContainer, t) ??
          surfaceContainer,
      surfaceElevated:
          Color.lerp(surfaceElevated, other.surfaceElevated, t) ??
          surfaceElevated,
      surfaceHighlight:
          Color.lerp(surfaceHighlight, other.surfaceHighlight, t) ??
          surfaceHighlight,
      border: Color.lerp(border, other.border, t) ?? border,
      borderSubtle:
          Color.lerp(borderSubtle, other.borderSubtle, t) ?? borderSubtle,
      glassSurface:
          Color.lerp(glassSurface, other.glassSurface, t) ?? glassSurface,
      glassBorder: Color.lerp(glassBorder, other.glassBorder, t) ?? glassBorder,
      scrimMedium: Color.lerp(scrimMedium, other.scrimMedium, t) ?? scrimMedium,
      scrimStrong: Color.lerp(scrimStrong, other.scrimStrong, t) ?? scrimStrong,
      primary: Color.lerp(primary, other.primary, t) ?? primary,
      primaryGlow: Color.lerp(primaryGlow, other.primaryGlow, t) ?? primaryGlow,
      secondary: Color.lerp(secondary, other.secondary, t) ?? secondary,
      badgeVideo: Color.lerp(badgeVideo, other.badgeVideo, t) ?? badgeVideo,
      badgeManga: Color.lerp(badgeManga, other.badgeManga, t) ?? badgeManga,
      badgeNovel: Color.lerp(badgeNovel, other.badgeNovel, t) ?? badgeNovel,
      textPrimary: Color.lerp(textPrimary, other.textPrimary, t) ?? textPrimary,
      textSecondary:
          Color.lerp(textSecondary, other.textSecondary, t) ?? textSecondary,
      textMuted: Color.lerp(textMuted, other.textMuted, t) ?? textMuted,
      onPrimary: Color.lerp(onPrimary, other.onPrimary, t) ?? onPrimary,
      success: Color.lerp(success, other.success, t) ?? success,
      warning: Color.lerp(warning, other.warning, t) ?? warning,
      error: Color.lerp(error, other.error, t) ?? error,
      info: Color.lerp(info, other.info, t) ?? info,
      isOled: t < 0.5 ? isOled : other.isOled,
    );
  }
}
