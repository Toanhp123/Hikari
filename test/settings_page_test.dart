import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/features/settings/settings_page.dart';

void main() {
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
}
