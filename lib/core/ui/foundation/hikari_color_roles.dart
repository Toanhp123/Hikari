import 'package:flutter/material.dart';

/// Raw user-selectable seeds, never generated ColorScheme.primary values.
abstract final class HikariAccentSeeds {
  static const defaultSeed = Color(0xFFB4BEFE);
  static const presets = <(String, Color)>[
    ('Violet', Color(0xFF8B5CF6)),
    ('Pink', Color(0xFFEC4899)),
    ('Cyan', Color(0xFF06B6D4)),
    ('Green', Color(0xFF10B981)),
    ('Amber', Color(0xFFF59E0B)),
  ];
}

/// Warning and info pairs not represented by Material ColorScheme.
@immutable
class HikariStatusColors extends ThemeExtension<HikariStatusColors> {
  static const dark = HikariStatusColors(
    warningContainer: Color(0xFF453026),
    onWarningContainer: Color(0xFFFAB387),
    infoContainer: Color(0xFF1E3547),
    onInfoContainer: Color(0xFF89DCEB),
  );

  const HikariStatusColors({
    required this.warningContainer,
    required this.onWarningContainer,
    required this.infoContainer,
    required this.onInfoContainer,
  });

  final Color warningContainer;
  final Color onWarningContainer;
  final Color infoContainer;
  final Color onInfoContainer;

  @override
  HikariStatusColors copyWith({
    Color? warningContainer,
    Color? onWarningContainer,
    Color? infoContainer,
    Color? onInfoContainer,
  }) => HikariStatusColors(
    warningContainer: warningContainer ?? this.warningContainer,
    onWarningContainer: onWarningContainer ?? this.onWarningContainer,
    infoContainer: infoContainer ?? this.infoContainer,
    onInfoContainer: onInfoContainer ?? this.onInfoContainer,
  );

  @override
  HikariStatusColors lerp(ThemeExtension<HikariStatusColors>? other, double t) {
    if (other is! HikariStatusColors) return this;
    return HikariStatusColors(
      warningContainer: Color.lerp(
        warningContainer,
        other.warningContainer,
        t,
      )!,
      onWarningContainer: Color.lerp(
        onWarningContainer,
        other.onWarningContainer,
        t,
      )!,
      infoContainer: Color.lerp(infoContainer, other.infoContainer, t)!,
      onInfoContainer: Color.lerp(onInfoContainer, other.onInfoContainer, t)!,
    );
  }
}

extension HikariThemeContext on BuildContext {
  /// Missing required roles indicate a root theme configuration error.
  HikariStatusColors get hikariStatusColors =>
      Theme.of(this).extension<HikariStatusColors>() ??
      (throw StateError('HikariTheme is required above this widget'));
}
