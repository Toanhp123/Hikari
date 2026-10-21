import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_confirmation_dialog.dart';

void main() {
  Future<List<bool>> mount(
    WidgetTester tester, {
    bool destructive = false,
    bool barrierDismissible = true,
    Widget content = const Text('This change requires confirmation.'),
  }) async {
    final results = <bool>[];
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: Builder(
            builder: (context) => Center(
              child: FilledButton(
                onPressed: () async {
                  results.add(
                    await showHikariConfirmation(
                      context: context,
                      title: 'Confirm change?',
                      content: content,
                      confirmLabel: 'Proceed',
                      destructive: destructive,
                      barrierDismissible: barrierDismissible,
                    ),
                  );
                },
                child: const Text('Open dialog'),
              ),
            ),
          ),
        ),
      ),
    );
    return results;
  }

  testWidgets('Cancel, barrier and Back cannot authorize an operation', (
    tester,
  ) async {
    final results = await mount(tester);

    await tester.tap(find.text('Open dialog'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(results, [false]);

    await tester.tap(find.text('Open dialog'));
    await tester.pumpAndSettle();
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();
    expect(results, [false, false]);

    await tester.tap(find.text('Open dialog'));
    await tester.pumpAndSettle();
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(results, [false, false, false]);
  });

  testWidgets('Escape cancels and restores opener keyboard focus', (
    tester,
  ) async {
    final results = await mount(tester);
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pumpAndSettle();
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();
    expect(results, [false]);
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pumpAndSettle();
    expect(find.text('Confirm change?'), findsOneWidget);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
  });

  testWidgets('Only explicit confirm returns true', (tester) async {
    final results = await mount(tester);
    await tester.tap(find.text('Open dialog'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Proceed'));
    await tester.pumpAndSettle();
    expect(results, [true]);
  });

  testWidgets('Non-dismissible barrier is ignored; Cancel still works', (
    tester,
  ) async {
    final results = await mount(tester, barrierDismissible: false);
    await tester.tap(find.text('Open dialog'));
    await tester.pumpAndSettle();
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();
    expect(find.text('Confirm change?'), findsOneWidget);
    expect(results, isEmpty);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(results, [false]);
  });

  testWidgets('Destructive action inherits error role from root theme', (
    tester,
  ) async {
    await mount(tester, destructive: true);
    await tester.tap(find.text('Open dialog'));
    await tester.pumpAndSettle();
    final context = tester.element(find.text('Proceed'));
    final button = tester.widget<FilledButton>(
      find.ancestor(
        of: find.text('Proceed'),
        matching: find.byType(FilledButton),
      ),
    );
    final scheme = Theme.of(context).colorScheme;
    expect(
      button.style?.backgroundColor?.resolve(<WidgetState>{}),
      scheme.error,
    );
    expect(
      button.style?.foregroundColor?.resolve(<WidgetState>{}),
      scheme.onError,
    );
  });

  testWidgets('Long copy and large text keep both dialog actions accessible', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final results = <bool>[];
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: MediaQuery(
          data: const MediaQueryData(
            size: Size(320, 640),
            textScaler: TextScaler.linear(2),
          ),
          child: Scaffold(
            body: Builder(
              builder: (context) => TextButton(
                onPressed: () async {
                  results.add(
                    await showHikariConfirmation(
                      context: context,
                      title: 'Very long confirmation title about maintenance',
                      content: Text(
                        List.filled(35, 'Please review this detail.').join(' '),
                      ),
                      confirmLabel: 'Confirm maintenance',
                    ),
                  );
                },
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.text('Cancel'), findsOneWidget);
    expect(find.text('Confirm maintenance'), findsOneWidget);
    await tester.ensureVisible(find.text('Confirm maintenance'));
    await tester.tap(find.text('Confirm maintenance'));
    await tester.pumpAndSettle();
    expect(results, [true]);
  });
}
