import 'package:flutter/material.dart';

/// Semantic motion durations and easing curves for the Cinematic Neo-Material design system.
abstract final class HikariMotion {
  /// 150ms - micro-interactions, button tactile feedback, hover highlights
  static const Duration fast = Duration(milliseconds: 150);

  /// 250ms - state changes, expansions, chip selections
  static const Duration normal = Duration(milliseconds: 250);

  /// 350ms - sheet reveals, dialogs, route transitions
  static const Duration slow = Duration(milliseconds: 350);

  /// 500ms - hero transformations, player fade in/out
  static const Duration extraSlow = Duration(milliseconds: 500);

  /// Standard natural curve: easeOutCubic
  static const Curve curveStandard = Curves.easeOutCubic;

  /// Snappy entrance curve
  static const Curve curveEnter = Curves.decelerate;

  /// Quick exit curve
  static const Curve curveExit = Curves.easeInCubic;

  /// Subtle tactile bounce curve
  static const Curve curveBounce = Curves.easeOutBack;
}
