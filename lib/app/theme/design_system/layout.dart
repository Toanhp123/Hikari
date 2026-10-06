import 'geometry.dart';

enum HikariLayoutClass { compact, medium, expanded, wide }

/// Classify available logical width (LayoutBuilder), not device type.
abstract final class HikariLayout {
  static const contentMaxWidth = 1440.0;
  static const readingMaxWidth = 720.0;
  static const dialogMaxWidth = 560.0;
  static const sheetMaxWidth = 640.0;

  static HikariLayoutClass classify(double width) {
    if (!width.isFinite || width < 0) {
      throw ArgumentError.value(
        width,
        'width',
        'Must be finite and nonnegative',
      );
    }
    if (width < 600) return HikariLayoutClass.compact;
    if (width < 840) return HikariLayoutClass.medium;
    if (width < 1200) return HikariLayoutClass.expanded;
    return HikariLayoutClass.wide;
  }

  static double gutter(double width) => switch (classify(width)) {
    HikariLayoutClass.compact => HikariSpace.content,
    HikariLayoutClass.medium => HikariSpace.section,
    HikariLayoutClass.expanded ||
    HikariLayoutClass.wide => HikariSpace.separation,
  };
}
