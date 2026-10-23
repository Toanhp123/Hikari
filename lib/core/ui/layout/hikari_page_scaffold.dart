import 'package:flutter/material.dart';

/// Route scaffold owns IME resize and SafeArea. Shell clearance arrives through
/// Scaffold's body MediaQuery; callers must not add another navigation inset.
/// Reader canvases/NestedScrollView may opt out and own their edge treatment.
class HikariPageScaffold extends StatelessWidget {
  const HikariPageScaffold({
    super.key,
    required this.body,
    this.appBar,
    this.bottomNavigationBar,
    this.floatingActionButton,
    this.drawer,
    this.endDrawer,
    this.useSafeArea = true,
    this.showAmbientGlow = false,
  });
  final Widget body;
  final PreferredSizeWidget? appBar;
  final Widget? bottomNavigationBar;
  final Widget? floatingActionButton;
  final Widget? drawer;
  final Widget? endDrawer;
  final bool useSafeArea;
  final bool showAmbientGlow;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Scaffold(
      backgroundColor: colors.surface,
      appBar: appBar,
      bottomNavigationBar: bottomNavigationBar,
      floatingActionButton: floatingActionButton,
      drawer: drawer,
      endDrawer: endDrawer,
      body: Stack(
        children: [
          if (showAmbientGlow && colors.surface != Colors.black) ...[
            Positioned(
              top: -120,
              right: -80,
              child: IgnorePointer(
                child: Container(
                  width: 340,
                  height: 340,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: RadialGradient(
                      colors: [
                        colors.primary.withValues(alpha: .14),
                        colors.primary.withValues(alpha: 0),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            Positioned(
              bottom: 120,
              left: -100,
              child: IgnorePointer(
                child: Container(
                  width: 300,
                  height: 300,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: RadialGradient(
                      colors: [
                        colors.secondary.withValues(alpha: .06),
                        colors.secondary.withValues(alpha: 0),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
          if (useSafeArea) SafeArea(child: body) else body,
        ],
      ),
    );
  }
}
