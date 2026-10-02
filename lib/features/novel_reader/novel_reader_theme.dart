import 'package:flutter/material.dart';

enum NovelReaderTheme {
  oled(bg: Color(0xFF000000), fg: Color(0xFFD1D5DB)),
  charcoal(bg: Color(0xFF0F172A), fg: Color(0xFFE2E8F0)),
  paper(bg: Color(0xFFF4ECD8), fg: Color(0xFF3E3428));

  const NovelReaderTheme({required this.bg, required this.fg});

  final Color bg;
  final Color fg;
}
