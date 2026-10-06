import 'package:flutter/material.dart';

/// Layout rhythm, not a replacement for intrinsic media measurements.
abstract final class HikariSpace {
  static const micro = 4.0;
  static const inline = 8.0;
  static const compact = 12.0;
  static const content = 16.0;
  static const section = 24.0;
  static const separation = 32.0;
  static const spacious = 48.0;
}

abstract final class HikariShape {
  static const small = BorderRadius.all(Radius.circular(8));
  static const medium = BorderRadius.all(Radius.circular(12));
  static const large = BorderRadius.all(Radius.circular(16));
  static const extraLarge = BorderRadius.all(Radius.circular(24));
  static const pill = StadiumBorder();
  static const borderWidth = 1.0;
  static const focusWidth = 2.0;
}

abstract final class HikariSize {
  static const touchTarget = 48.0;
  static const fieldMinHeight = 56.0;
  static const iconSmall = 16.0;
  static const icon = 24.0;
  static const iconLarge = 32.0;
  static const posterAspectRatio = 2 / 3;
  static const landscapeAspectRatio = 16 / 9;
  static const posterMaxExtent = 200.0;
}
