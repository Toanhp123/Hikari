import 'package:flutter/material.dart';

/// Native TextTheme slots are the semantic API; no parallel style extension.
abstract final class HikariDesignTypography {
  static const textTheme = TextTheme(
    displayLarge: TextStyle(
      fontSize: 32,
      height: 1.25,
      fontWeight: FontWeight.w700,
    ),
    headlineMedium: TextStyle(
      fontSize: 24,
      height: 1.3,
      fontWeight: FontWeight.w700,
    ),
    titleLarge: TextStyle(
      fontSize: 20,
      height: 1.4,
      fontWeight: FontWeight.w600,
    ),
    titleMedium: TextStyle(
      fontSize: 16,
      height: 1.5,
      fontWeight: FontWeight.w600,
    ),
    titleSmall: TextStyle(
      fontSize: 14,
      height: 1.4,
      fontWeight: FontWeight.w600,
    ),
    bodyLarge: TextStyle(fontSize: 16, height: 1.5),
    bodyMedium: TextStyle(fontSize: 14, height: 1.5),
    bodySmall: TextStyle(fontSize: 12, height: 1.5),
    labelLarge: TextStyle(
      fontSize: 14,
      height: 1.4,
      fontWeight: FontWeight.w600,
    ),
    labelMedium: TextStyle(
      fontSize: 12,
      height: 1.4,
      fontWeight: FontWeight.w500,
    ),
    labelSmall: TextStyle(
      fontSize: 12,
      height: 1.4,
      fontWeight: FontWeight.w500,
    ),
  );
}
