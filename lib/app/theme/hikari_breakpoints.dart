import 'package:flutter/material.dart';

/// Semantic breakpoints for adaptive layouts in Hikari.
abstract final class HikariBreakpoints {
  /// < 600 dp: Compact (Mobile portrait / small screens)
  static const double compactMax = 599.0;

  /// 600 - 840 dp: Medium (Tablets, foldables, small desktop windows)
  static const double mediumMin = 600.0;
  static const double mediumMax = 839.0;

  /// > 840 dp: Expanded (Desktop, large tablets in landscape)
  static const double expandedMin = 840.0;

  /// Max content width constraint on wide screens to prevent stretched reading/watching UI
  static const double maxContentWidth = 1440.0;
}

/// Responsive extensions on [BuildContext]
extension HikariResponsiveContext on BuildContext {
  double get screenWidth => MediaQuery.sizeOf(this).width;
  double get screenHeight => MediaQuery.sizeOf(this).height;

  bool get isCompact => screenWidth < HikariBreakpoints.mediumMin;
  bool get isMedium =>
      screenWidth >= HikariBreakpoints.mediumMin &&
      screenWidth <= HikariBreakpoints.mediumMax;
  bool get isExpanded => screenWidth >= HikariBreakpoints.expandedMin;

  /// Optimal poster grid column count based on width
  int get responsiveGridColumns {
    final width = screenWidth;
    if (width < 380) return 2;
    if (width < 600) return 3;
    if (width < 900) return 4;
    if (width < 1200) return 5;
    return 6;
  }
}
