import 'package:flutter/material.dart';

abstract final class HikariDesignMotion {
  static const interaction = Duration(milliseconds: 150);
  static const standard = Duration(milliseconds: 300);
  static const emphasized = Duration(milliseconds: 400);
  static const curve = Easing.standard;
  static const entranceCurve = Easing.emphasizedDecelerate;

  /// Honors MediaQuery.disableAnimations (Android Remove animations).
  /// Does not handle iOS AccessibilityFeatures.reduceMotion. Decorative timing
  /// only; never use for debounce, playback seek, reader timing or auto-hide.
  static Duration duration(BuildContext context, Duration requested) =>
      MediaQuery.disableAnimationsOf(context) ? Duration.zero : requested;
}
