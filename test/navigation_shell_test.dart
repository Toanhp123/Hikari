import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/features/shell/app_navigation_shell.dart';

void main() {
  testWidgets('AppNavigationShell switches tabs on compact screen', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(400, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);

    int? selected;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: AppNavigationShell(
          onTabChanged: (i) => selected = i,
          tabs: const [
            Text('Home View'),
            Text('Search View'),
            Text('Library View'),
            Text('Settings View'),
          ],
        ),
      ),
    );

    expect(find.text('Home View'), findsOneWidget);
    expect(find.text('Search View'), findsNothing);

    await tester.tap(find.text('Search'));
    await tester.pumpAndSettle();

    expect(selected, 1);
    expect(find.text('Search View'), findsOneWidget);
  });

  testWidgets(
    'AppNavigationShell switches tabs on wide screen (Navigation Rail)',
    (tester) async {
      tester.view.physicalSize = const Size(1000, 800);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: const AppNavigationShell(
            tabs: [
              Text('Home View'),
              Text('Search View'),
              Text('Library View'),
              Text('Settings View'),
            ],
          ),
        ),
      );

      expect(find.text('Home View'), findsOneWidget);
      await tester.tap(find.byTooltip('Library'));
      await tester.pumpAndSettle();

      expect(find.text('Library View'), findsOneWidget);
    },
  );
}
