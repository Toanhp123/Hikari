import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/navigation/app_navigation_shell.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

void main() {
  // Deliberately unordered: destination identity must not depend on insertion order.
  const tabs = {
    AppTab.settings: Text('Settings View'),
    AppTab.local: Text('Local View'),
    AppTab.home: Text('Home View'),
    AppTab.library: Text('Library View'),
    AppTab.search: Text('Search View'),
  };
  for (final width in [375.0, 1000.0]) {
    testWidgets('all destinations map safely at width $width', (tester) async {
      tester.view.physicalSize = Size(width, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      AppTab? selected;
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: AppNavigationShell(
            tabs: tabs,
            onTabChanged: (tab) => selected = tab,
          ),
        ),
      );
      expect(find.text('Home View'), findsOneWidget);
      for (final tab in AppTab.values.skip(1)) {
        await tester.tap(find.byTooltip(tab.label));
        await tester.pumpAndSettle();
        expect(find.text('${tab.label} View'), findsOneWidget);
        expect(selected, tab);
      }
      expect(tester.takeException(), isNull);
    });
  }
  testWidgets('missing destination fails explicitly', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: AppNavigationShell(tabs: {AppTab.home: tabs[AppTab.home]!}),
      ),
    );
    expect(tester.takeException(), isArgumentError);
  });
  testWidgets('controller navigation selects keyed page', (tester) async {
    final controller = AppNavigationController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: AppNavigationShell(tabs: tabs, controller: controller),
      ),
    );
    controller.selectTab(AppTab.local);
    await tester.pumpAndSettle();
    expect(find.text('Local View'), findsOneWidget);
  });
}
