import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';

enum HikariNavigationMode { bottomBar, rail, drawer }

HikariNavigationMode resolveNavigationMode(
  double availableWidth,
  int destinationCount,
) {
  if (!availableWidth.isFinite || availableWidth < 0 || destinationCount < 1) {
    throw ArgumentError('Finite nonnegative width and destinations required.');
  }
  if (availableWidth >= HikariBreakpoints.mediumMin) {
    return HikariNavigationMode.rail;
  }
  return destinationCount <= 5
      ? HikariNavigationMode.bottomBar
      : HikariNavigationMode.drawer;
}
