import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/navigation/hikari_navigation_destination.dart';
import 'package:hikari/core/ui/navigation/hikari_navigation_policy.dart';
import 'package:hikari/core/ui/navigation/hikari_navigation_shell.dart';

void main() {
  test('navigation policy validates and separates overflow from five tabs', () {
    for (final width in <double>[375, 599]) {
      expect(resolveNavigationMode(width, 5), HikariNavigationMode.bottomBar);
      expect(resolveNavigationMode(width, 6), HikariNavigationMode.drawer);
    }
    for (final width in <double>[600, 601, 839, 840, 841, 1200, 1440]) {
      expect(resolveNavigationMode(width, 6), HikariNavigationMode.rail);
    }
    expect(() => resolveNavigationMode(double.nan, 5), throwsArgumentError);
    expect(() => resolveNavigationMode(375, 0), throwsArgumentError);
  });
  testWidgets(
    'drawer selection uses controlled ID and Back only closes drawer',
    (tester) async {
      tester.view.physicalSize = const Size(375, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      var selected = '0';
      await tester.pumpWidget(
        MaterialApp(
          home: StatefulBuilder(
            builder: (context, setState) => HikariNavigationShell(
              destinations: [
                for (var i = 0; i < 6; i++)
                  HikariNavigationDestination(
                    id: '$i',
                    label: 'Page $i',
                    icon: Icons.home,
                  ),
              ],
              selectedId: selected,
              onSelected: (id) => setState(() => selected = id),
              body: Text('Selected $selected'),
            ),
          ),
        ),
      );
      await tester.tap(find.byTooltip('Open navigation menu'));
      await tester.pumpAndSettle();
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(selected, '0');
      await tester.tap(find.byTooltip('Open navigation menu'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Page 5'));
      await tester.pumpAndSettle();
      expect(selected, '5');
      expect(find.text('Selected 5'), findsOneWidget);
      expect(find.text('Page 5').hitTestable(), findsNothing);
    },
  );
  for (final width in <double>[375, 600]) {
    testWidgets('RTL 3x navigation at $width supports keyboard selection', (
      tester,
    ) async {
      tester.view.physicalSize = Size(width, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      var selected = '0';
      await tester.pumpWidget(
        MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context)
                .copyWith(textScaler: TextScaler.linear(3)),
            child: Directionality(
              textDirection: TextDirection.rtl,
              child: child!,
            ),
          ),
          home: StatefulBuilder(
            builder: (_, setState) => HikariNavigationShell(
              destinations: [
                for (var i = 0; i < 5; i++)
                  HikariNavigationDestination(
                    id: '$i',
                    label: 'Page $i',
                    icon: Icons.home,
                  ),
              ],
              selectedId: selected,
              onSelected: (id) => setState(() => selected = id),
              body: const Text('Retained'),
            ),
          ),
        ),
      );
      expect(tester.takeException(), isNull);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(FocusManager.instance.primaryFocus, isNotNull);
      expect(tester.takeException(), isNull);
    });
  }
  testWidgets('RTL rail protects physical right safe inset', (tester) async {
    tester.view.physicalSize = const Size(900, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context)
              .copyWith(padding: const EdgeInsets.only(right: 24)),
          child: Directionality(
            textDirection: TextDirection.rtl,
            child: child!,
          ),
        ),
        home: HikariNavigationShell(
          destinations: const [
            HikariNavigationDestination(id: 'a', label: 'A', icon: Icons.home),
          ],
          selectedId: 'a',
          onSelected: (_) {},
          body: const Text('Body'),
        ),
      ),
    );
    expect(tester.getRect(find.byTooltip('A')).right, lessThanOrEqualTo(876));
  });

  testWidgets('duplicate destination IDs rejected', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: HikariNavigationShell(
          destinations: const [
            HikariNavigationDestination(id: 'a', label: 'A', icon: Icons.home),
            HikariNavigationDestination(
              id: 'a',
              label: 'B',
              icon: Icons.search,
            ),
          ],
          selectedId: 'a',
          onSelected: (_) {},
          body: const Text('Body'),
        ),
      ),
    );
    expect(tester.takeException(), isArgumentError);
  });
}
