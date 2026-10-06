import 'package:flutter/material.dart';

abstract final class HikariDesignMotion {
  static const interaction = Duration(milliseconds: 150);
  static const exit = Duration(milliseconds: 200);
  static const standard = Duration(milliseconds: 300);
  static const emphasized = Duration(milliseconds: 400);

  static const curve = Easing.standard;
  static const entranceCurve = Easing.emphasizedDecelerate;
  static const exitCurve = Easing.emphasizedAccelerate;
  static const interactionCurve = Curves.easeOutCubic;

  static const pressScale = 0.97;

  /// Honors MediaQuery.disableAnimations (Android Remove animations) and
  /// platform AccessibilityFeatures.reduceMotion (iOS/macOS Reduce Motion).
  /// Decorative timing only; never use for debounce, playback seek, reader
  /// timing or auto-hide.
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
