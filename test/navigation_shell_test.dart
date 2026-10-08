import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/navigation/app_navigation_shell.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_search_bar.dart';
import 'package:hikari/features/home/home_page.dart';
import 'package:hikari/features/home/widgets/home_header.dart';

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
  testWidgets(
    'Home, navigation and SearchBar inherit OLED and accent from root',
    (tester) async {
      tester.view.physicalSize = const Size(375, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      const seed = Color(0xFF06B6D4);
      final canonical = HikariTheme.darkTheme(oled: true, accentColor: seed);
      await tester.pumpWidget(
        MaterialApp(
          theme: canonical,
          home: AppNavigationShell(
            tabs: {
              AppTab.home: HomePage(openMedia: (_, _) {}),
              AppTab.search: HikariSearchBar(onChanged: (_) {}),
              AppTab.local: const Text('Local'),
              AppTab.library: const Text('Library'),
              AppTab.settings: const Text('Settings'),
            },
          ),
        ),
      );
      await tester.pumpAndSettle();

      final homeTheme = Theme.of(tester.element(find.byType(HomeHeader)));
      final navTheme = Theme.of(tester.element(find.byTooltip('Home')));
      expect(homeTheme.colorScheme.surface, Colors.black);
      expect(navTheme.colorScheme.surface, Colors.black);
      expect(homeTheme.colorScheme.primary, canonical.colorScheme.primary);
      expect(navTheme.colorScheme.primary, canonical.colorScheme.primary);
      expect(homeTheme.extension<HikariColors>()!.isOled, isTrue);
      expect(navTheme.extension<HikariColors>()!.isOled, isTrue);

      await tester.tap(find.byTooltip('Search'));
      await tester.pumpAndSettle();
      final searchTheme = Theme.of(tester.element(find.byType(SearchBar)));
      expect(searchTheme.colorScheme.surface, Colors.black);
      expect(searchTheme.colorScheme.primary, canonical.colorScheme.primary);
      expect(searchTheme.extension<HikariColors>()!.isOled, isTrue);
    },
  );

  testWidgets('visited tab state survives live compact/rail resizes', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(599, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    var inits = 0;
    var disposals = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: AppNavigationShell(
          tabs: {
            AppTab.home: const Text('Home View'),
            AppTab.search: _RetainedSearchTab(
              onInit: () => inits++,
              onDispose: () => disposals++,
            ),
            AppTab.local: const Text('Local View'),
            AppTab.library: const Text('Library View'),
            AppTab.settings: const Text('Settings View'),
          },
        ),
      ),
    );
    expect(inits, 0); // Unvisited tabs are not mounted.
    await tester.tap(find.byTooltip('Search'));
    await tester.pumpAndSettle();
    expect(inits, 1);
    await tester.enterText(find.byType(TextField), 'persist me');
    await tester.pump();

    for (final width in [600.0, 601.0, 599.0, 840.0, 375.0]) {
      tester.view.physicalSize = Size(width, 800);
      await tester.pumpAndSettle();
      expect(find.text('persist me'), findsOneWidget);
      expect(inits, 1, reason: 'Resizing must not remount a visited tab');
      expect(disposals, 0);
    }
  });

  testWidgets('wide rail exposes exactly one selected destination', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(900, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: AppNavigationShell(tabs: tabs),
      ),
    );
    expect(
      tester.getSemantics(find.byTooltip('Home')).flagsCollection.isSelected,
      Tristate.isTrue,
    );
    await tester.tap(find.byTooltip('Search'));
    await tester.pumpAndSettle();
    expect(
      tester.getSemantics(find.byTooltip('Search')).flagsCollection.isSelected,
      Tristate.isTrue,
    );
    expect(
      tester.getSemantics(find.byTooltip('Home')).flagsCollection.isSelected,
      Tristate.isFalse,
    );
    semantics.dispose();
  });

  testWidgets('compact navigation grows for large text without overflow', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(375, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2.5)),
          child: AppNavigationShell(tabs: tabs),
        ),
      ),
    );
    expect(tester.getSize(find.byTooltip('Home')).height, greaterThan(72));
    expect(tester.takeException(), isNull);
  });

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

  testWidgets('docked bottom bar adheres to design system contracts', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(375, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: AppNavigationShell(tabs: tabs),
      ),
    );

    // Verify label typography is 12px (accessible, not 10px)
    final textStyles = tester
        .widgetList<AnimatedDefaultTextStyle>(
          find.descendant(
            of: find.byType(InkWell),
            matching: find.byType(AnimatedDefaultTextStyle),
          ),
        )
        .map((widget) => widget.style)
        .toList();
    expect(textStyles, isNotEmpty);
    for (final style in textStyles) {
      expect(style.fontSize, 12.0);
    }

    // Verify selected tab has single pill indicator container
    final homeTooltip = find.byTooltip('Home');
    expect(homeTooltip, findsOneWidget);

    final homeContainer = tester.widget<AnimatedContainer>(
      find.descendant(
        of: homeTooltip,
        matching: find.byType(AnimatedContainer),
      ),
    );
    final boxDecoration = homeContainer.decoration as BoxDecoration;
    expect(boxDecoration.borderRadius, BorderRadius.circular(16));
    // Verify semantics expose selection status
    final handle = tester.ensureSemantics();
    final semantics = tester.getSemantics(homeTooltip);
    expect(semantics.flagsCollection.isSelected, Tristate.isTrue);
    expect(find.bySemanticsLabel('Home'), findsOneWidget);
    handle.dispose();

    // Verify touch target height is at least 48dp (is 64dp)
    final barSize = tester.getSize(homeTooltip);
    expect(barSize.height, greaterThanOrEqualTo(48.0));
  });
}

class _RetainedSearchTab extends StatefulWidget {
  const _RetainedSearchTab({required this.onInit, required this.onDispose});

  final VoidCallback onInit;
  final VoidCallback onDispose;

  @override
  State<_RetainedSearchTab> createState() => _RetainedSearchTabState();
}

class _RetainedSearchTabState extends State<_RetainedSearchTab> {
  final _controller = TextEditingController();

  @override
  void initState() {
    super.initState();
    widget.onInit();
  }

  @override
  void dispose() {
    widget.onDispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => TextField(controller: _controller);
}
