import 'package:flutter/material.dart';

/// Semantic spacing scale for the Hikari design system.
abstract final class HikariSpacing {
  /// 4.0 dp
  static const double xs = 4.0;

  /// 8.0 dp
  static const double sm = 8.0;

  /// 12.0 dp
  static const double md = 12.0;

  /// 16.0 dp
  static const double lg = 16.0;

  /// 24.0 dp
  static const double xl = 24.0;

  /// 32.0 dp
  static const double xxl = 32.0;

  /// 48.0 dp
  static const double xxxl = 48.0;

  // Convenient EdgeInsets shortcuts
  static const EdgeInsets edgeInsetsXs = EdgeInsets.all(xs);
  static const EdgeInsets edgeInsetsSm = EdgeInsets.all(sm);
  static const EdgeInsets edgeInsetsMd = EdgeInsets.all(md);
  static const EdgeInsets edgeInsetsLg = EdgeInsets.all(lg);
  static const EdgeInsets edgeInsetsXl = EdgeInsets.all(xl);
  static const EdgeInsets edgeInsetsXxl = EdgeInsets.all(xxl);

  static const EdgeInsets edgeInsetsHSm = EdgeInsets.symmetric(horizontal: sm);
  static const EdgeInsets edgeInsetsHMd = EdgeInsets.symmetric(horizontal: md);
  static const EdgeInsets edgeInsetsHLg = EdgeInsets.symmetric(horizontal: lg);
  static const EdgeInsets edgeInsetsHXl = EdgeInsets.symmetric(horizontal: xl);

  static const EdgeInsets edgeInsetsVSm = EdgeInsets.symmetric(vertical: sm);
  static const EdgeInsets edgeInsetsVMd = EdgeInsets.symmetric(vertical: md);
  static const EdgeInsets edgeInsetsVLg = EdgeInsets.symmetric(vertical: lg);
}
