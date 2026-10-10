import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';

import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/features/settings/settings_page.dart';

void main() {
  testWidgets('folder picker failure offers retry without uncaught errors', (
    tester,
  ) async {
    var attempts = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: SettingsPage(
          onChooseLocalFolder: () async {
            if (++attempts == 1) throw StateError('picker unavailable');
          },
        ),
      ),
    );
    await tester.tap(find.text('Local Media Folder'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(
      find.text('Could not choose a local folder. Try again.'),
      findsOneWidget,
    );
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(attempts, 2);
  });
  testWidgets('accent swatches use selected seed and named 48dp targets', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    Color? tapped;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(accentColor: const Color(0xFF06B6D4)),
        home: SettingsPage(
          selectedAccent: const Color(0xFF06B6D4),
          onSelectAccent: (color) => tapped = color,
        ),
      ),
    );
    final cyan = find.bySemanticsLabel('Cyan accent');
    expect(cyan, findsOneWidget);
    expect(
      tester.getSemantics(cyan).flagsCollection.isSelected,
      Tristate.isTrue,
    );
    expect(tester.getSize(cyan).width, greaterThanOrEqualTo(48));
    expect(tester.getSize(cyan).height, greaterThanOrEqualTo(48));
    await tester.tap(cyan);
    expect(tapped, const Color(0xFF06B6D4));
    semantics.dispose();
  });

  testWidgets('default accent restores nullable raw seed', (tester) async {
    final semantics = tester.ensureSemantics();
    Color? latest = const Color(0xFF8B5CF6);
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(accentColor: latest),
        home: SettingsPage(
          selectedAccent: latest,
          onSelectAccent: (value) => latest = value,
        ),
      ),
    );
    final reset = find.bySemanticsLabel('Default accent');
    expect(reset, findsOneWidget);
    expect(tester.getSize(reset).width, greaterThanOrEqualTo(48));
    expect(
      tester.getSemantics(reset).flagsCollection.isSelected,
      Tristate.isFalse,
    );
    await tester.tap(reset);
    await tester.pump();
    expect(latest, isNull);
    semantics.dispose();
  });

  testWidgets('SettingsPage renders sections and handles clear cache', (
    tester,
  ) async {
    bool? oledToggled;
    var cacheCleared = false;

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: SettingsPage(
          onToggleOled: (val) => oledToggled = val,
          onClearCache: () async => cacheCleared = true,
        ),
      ),
    );

    expect(find.text('Settings'), findsOneWidget);
    expect(find.text('Appearance'), findsOneWidget);
    expect(find.text('OLED Pure Black'), findsOneWidget);
    expect(find.text('About Hikari'), findsOneWidget);

    // Toggle OLED
    await tester.tap(find.byType(Switch));
    await tester.pumpAndSettle();
    expect(oledToggled, isTrue);

    // Clear Cache
    await tester.tap(find.text('Clear'));
    await tester.pumpAndSettle();
    expect(cacheCleared, isTrue);
    expect(find.text('Cache cleared.'), findsOneWidget);
  });
  testWidgets('Settings uses the canonical app bar title style', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(theme: HikariTheme.darkTheme(), home: const SettingsPage()),
    );
    expect(find.text('Settings'), findsOneWidget);
    expect(
      Theme.of(tester.element(find.text('Settings')))
          .appBarTheme
          .titleTextStyle
          ?.fontWeight,
      FontWeight.w700,
    );
  });
}
