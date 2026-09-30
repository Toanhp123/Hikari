import 'package:flutter/material.dart';

/// Semantic Dark Cinema & OLED Theme color palette for the Cinematic Neo-Material design system.
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

  /// Deep Obsidian dark palette (standard).
  const HikariColors.dark()
    : background = const Color(0xFF0B0F17),
      surface = const Color(0xFF121620),
      surfaceContainer = const Color(0xFF161B26),
      surfaceElevated = const Color(0xFF1F293D),
      surfaceHighlight = const Color(0xFF2A3752),
      border = const Color(0xFF26334D),
      borderSubtle = const Color(0x14FFFFFF),
      primary = const Color(0xFF8B5CF6),
      primaryGlow = const Color(0xFFA78BFA),
      secondary = const Color(0xFFEC4899),
      badgeVideo = const Color(0xFFEC4899),
      badgeManga = const Color(0xFF06B6D4),
      badgeNovel = const Color(0xFFF59E0B),
      textPrimary = const Color(0xFFF8FAFC),
      textSecondary = const Color(0xFF94A3B8),
      textMuted = const Color(0xFF64748B),
      onPrimary = const Color(0xFFFFFFFF),
      success = const Color(0xFF10B981),
      warning = const Color(0xFFF59E0B),
      error = const Color(0xFFEF4444),
      info = const Color(0xFF3B82F6),
      isOled = false;

  /// Pure OLED Black palette for maximum battery savings and infinite contrast.
  const HikariColors.oled()
    : background = const Color(0xFF000000),
      surface = const Color(0xFF05080E),
      surfaceContainer = const Color(0xFF0D111A),
      surfaceElevated = const Color(0xFF141924),
      surfaceHighlight = const Color(0xFF1F2738),
      border = const Color(0xFF1E283D),
      borderSubtle = const Color(0x1AFFFFFF),
      primary = const Color(0xFF8B5CF6),
      primaryGlow = const Color(0xFFA78BFA),
      secondary = const Color(0xFFEC4899),
      badgeVideo = const Color(0xFFEC4899),
      badgeManga = const Color(0xFF06B6D4),
      badgeNovel = const Color(0xFFF59E0B),
      textPrimary = const Color(0xFFF8FAFC),
      textSecondary = const Color(0xFF94A3B8),
      textMuted = const Color(0xFF64748B),
      onPrimary = const Color(0xFFFFFFFF),
      success = const Color(0xFF10B981),
      warning = const Color(0xFFF59E0B),
      error = const Color(0xFFEF4444),
      info = const Color(0xFF3B82F6),
      isOled = true;

  final Color background;
  final Color surface;
  final Color surfaceContainer;
  final Color surfaceElevated;
  final Color surfaceHighlight;
  final Color border;
  final Color borderSubtle;
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
