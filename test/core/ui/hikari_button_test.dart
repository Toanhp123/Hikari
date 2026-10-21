import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';

void main() {
  for (final loading in [false, true]) {
    testWidgets(
      '${loading ? 'loading' : 'disabled'} action blocks tap and keyboard with native semantics',
      (tester) async {
        final semantics = tester.ensureSemantics();
        var calls = 0;
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: Column(
                children: [
                  HikariButton(
                    label: 'Install',
                    isLoading: loading,
                    onPressed: loading ? () => calls++ : null,
                  ),
                  TextButton(onPressed: () {}, child: const Text('Next')),
                ],
              ),
            ),
          ),
        );
        expect(
          tester.getSemantics(find.byType(FilledButton)),
          matchesSemantics(
            label: 'Install',
            isButton: true,
            hasEnabledState: true,
            isEnabled: false,
          ),
        );
        await tester.tap(find.text('Install'), warnIfMissed: false);
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.sendKeyEvent(LogicalKeyboardKey.enter);
        await tester.pump();
        expect(calls, 0);
        if (loading) expect(find.bySemanticsLabel('Install'), findsOneWidget);
        semantics.dispose();
      },
    );
  }
  testWidgets('shared action uses native button, callback and minimum target', (
    tester,
  ) async {
    var taps = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: Center(
            child: HikariButton(
              label: 'Refresh',
              icon: const Icon(Icons.refresh),
              variant: HikariButtonVariant.ghost,
              size: HikariButtonSize.small,
              onPressed: () => taps++,
            ),
          ),
        ),
      ),
    );
    final button = tester.widget<TextButton>(find.byType(TextButton));
    expect(button.onPressed, isNotNull);
    expect(
      tester.getSize(find.byType(TextButton)).height,
      greaterThanOrEqualTo(48),
    );
    await tester.tap(find.text('Refresh'));
    await tester.pump();
    expect(taps, 1);
  });

  testWidgets('loading state disables action without hiding the label', (
    tester,
  ) async {
    var taps = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HikariButton(
            label: 'Install',
            isLoading: true,
            onPressed: () => taps++,
          ),
        ),
      ),
    );
    expect(find.text('Install'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(
      tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
      isNull,
    );
    await tester.tap(find.text('Install'), warnIfMissed: false);
    await tester.pump();
    expect(taps, 0);
  });
}
