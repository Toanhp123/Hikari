import 'package:flutter/material.dart';

/// Prose canvas choices, independent of application chrome and persisted enums.
enum HikariReadingPalette {
  oled(background: Color(0xFF000000), content: Color(0xFFD1D5DB)),
  charcoal(background: Color(0xFF0F172A), content: Color(0xFFE2E8F0)),
  paper(background: Color(0xFFF4ECD8), content: Color(0xFF3E3428));

  const HikariReadingPalette({required this.background, required this.content});

  final Color background;
  final Color content;
}
