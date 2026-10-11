import 'package:flutter/material.dart';

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
