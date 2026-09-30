import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

/// A cinematic scaffold that renders an ambient glow gradient mesh behind the content.
class HikariScaffold extends StatelessWidget {
  const HikariScaffold({
    super.key,
    required this.body,
    this.appBar,
    this.bottomNavigationBar,
    this.floatingActionButton,
    this.drawer,
    this.endDrawer,
    this.useSafeArea = true,
    this.showAmbientGlow = true,
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
    final colors = context.hikariColors;

    Widget content = body;
    if (useSafeArea) {
      content = SafeArea(child: content);
    }

    return Scaffold(
      backgroundColor: colors.background,
      appBar: appBar,
      bottomNavigationBar: bottomNavigationBar,
      floatingActionButton: floatingActionButton,
      drawer: drawer,
      endDrawer: endDrawer,
      body: Stack(
        children: [
          // Background ambient gradient
          if (showAmbientGlow && !colors.isOled)
            Positioned(
              top: -120,
              right: -80,
              child: IgnorePointer(
                child: Container(
                  width: 320,
                  height: 320,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: RadialGradient(
                      colors: [
                        colors.primary.withValues(alpha: 0.12),
                        colors.primary.withValues(alpha: 0.0),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          content,
        ],
      ),
    );
  }
}
