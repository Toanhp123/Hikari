import 'package:flutter/material.dart';

enum HikariWidthClass { compact, medium, expanded, wide }

/// Adaptive window thresholds and bounded content constraints.
abstract final class HikariBreakpoints {
  /// 600 <= width < 840 dp: medium window.
  static const double mediumMin = 600.0;

  /// >= 840 dp: expanded-or-larger window.
  static const double expandedMin = 840.0;

  /// Content caps; actual component variants must use local layout constraints.
  static const double maxContentWidth = 1440.0;
  static const double dialogMaxWidth = 560.0;
  static const double sheetMaxWidth = 640.0;

  /// Maximum poster width used by adaptive media grids.
  static const double posterGridMaxExtent = 200.0;

  static HikariWidthClass classify(double width) {
    if (!width.isFinite || width < 0) {
      throw ArgumentError.value(
        width,
        'width',
        'Must be finite and nonnegative',
      );
    }
    if (width < 600) return HikariWidthClass.compact;
    if (width < 840) return HikariWidthClass.medium;
    if (width < 1200) return HikariWidthClass.expanded;
    return HikariWidthClass.wide;
  }
}

/// Responsive extensions on [BuildContext]
extension HikariResponsiveContext on BuildContext {
  double get screenWidth => MediaQuery.sizeOf(this).width;
  double get screenHeight => MediaQuery.sizeOf(this).height;

  bool get isCompact => screenWidth < HikariBreakpoints.mediumMin;
  bool get isMedium =>
      screenWidth >= HikariBreakpoints.mediumMin &&
      screenWidth < HikariBreakpoints.expandedMin;
  bool get isExpanded => screenWidth >= HikariBreakpoints.expandedMin;
}
