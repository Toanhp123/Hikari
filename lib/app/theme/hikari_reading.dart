import 'package:flutter/material.dart';

/// Prose canvas choices, independent of application chrome and persisted enums.
enum HikariReadingPalette {
  oled(background: Color(0xFF000000), content: Color(0xFFD1D5DB)),
  charcoal(background: Color(0xFF1E1E2E), content: Color(0xFFCDD6F4)),
  paper(background: Color(0xFFF4ECD8), content: Color(0xFF3E3428));

  const HikariReadingPalette({required this.background, required this.content});

  final Color background;
  final Color content;
}

abstract final class HikariReadingMetrics {
  static const maxWidth = 680.0;
  static const lineHeight = 1.65;
  static const paragraphSpacing = 16.0;
  static const horizontalPadding = 20.0;
  static const wideHorizontalPadding = 32.0;
}
