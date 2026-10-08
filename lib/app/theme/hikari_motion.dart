import 'package:flutter/material.dart';

/// Decorative motion vocabulary. Never use for debounce, seek or auto-hide.
abstract final class HikariMotion {
  static const interaction = Duration(milliseconds: 150);
  static const exit = Duration(milliseconds: 200);
  static const standard = Duration(milliseconds: 300);
  static const emphasized = Duration(milliseconds: 400);

  static const curveStandard = Easing.standard;
  static const curveEnter = Easing.emphasizedDecelerate;
  static const curveExit = Easing.emphasizedAccelerate;
  static const interactionCurve = Curves.easeOutCubic;
  static const pressScale = 0.97;

  // Existing call sites retain their API while using the canonical timings.
  static const fast = interaction;

  /// Honors system settings for decorative transitions only.
  static Duration duration(BuildContext context, Duration requested) {
    if (MediaQuery.disableAnimationsOf(context)) return Duration.zero;
    final view = View.maybeOf(context);
    if (view != null &&
        view.platformDispatcher.accessibilityFeatures.reduceMotion) {
      return Duration.zero;
    }
    return requested;
  }
}
