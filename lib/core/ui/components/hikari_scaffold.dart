import 'package:flutter/material.dart';
import 'package:hikari/core/ui/layout/hikari_page_scaffold.dart';

/// Legacy ambient-default adapter. Migrate callers by cleanup Task 07/01.
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
  Widget build(BuildContext context) => HikariPageScaffold(
    body: body,
    appBar: appBar,
    bottomNavigationBar: bottomNavigationBar,
    floatingActionButton: floatingActionButton,
    drawer: drawer,
    endDrawer: endDrawer,
    useSafeArea: useSafeArea,
    showAmbientGlow: showAmbientGlow,
  );
}
