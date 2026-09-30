import 'package:flutter/material.dart';

/// Semantic corner radius scale for the Cinematic Neo-Material design system.
abstract final class HikariRadius {
  /// 4.0 dp - micro elements, inner badges
  static const double xs = 4.0;

  /// 8.0 dp - small chips, inputs, nested surfaces
  static const double sm = 8.0;

  /// 12.0 dp - standard poster & cards
  static const double md = 12.0;

  /// 16.0 dp - elevated containers, dialogs
  static const double lg = 16.0;

  /// 20.0 dp - sheets, hero sections
  static const double xl = 20.0;

  /// 24.0 dp - capsule buttons, pills
  static const double capsule = 24.0;

  /// 999.0 dp - full circular radius
  static const double full = 999.0;

  // BorderRadius shortcuts
  static const BorderRadius borderXs = BorderRadius.all(Radius.circular(xs));
  static const BorderRadius borderSm = BorderRadius.all(Radius.circular(sm));
  static const BorderRadius borderMd = BorderRadius.all(Radius.circular(md));
  static const BorderRadius borderLg = BorderRadius.all(Radius.circular(lg));
  static const BorderRadius borderXl = BorderRadius.all(Radius.circular(xl));
  static const BorderRadius borderCapsule = BorderRadius.all(
    Radius.circular(capsule),
  );
  static const BorderRadius borderFull = BorderRadius.all(
    Radius.circular(full),
  );
}
