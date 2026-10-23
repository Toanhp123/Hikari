import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/components/hikari_confirmation_dialog.dart'
    as legacy;
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/dialog/hikari_confirmation_dialog.dart';

void main() {
  for (final action in [
    'confirm',
    'cancel',
    'close',
    'barrier',
    'escape',
    'back',
  ]) {
    testWidgets('$action never implies consent except explicit confirm', (
      tester,
    ) async {
      HikariPresentationResult<bool>? result;
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () async {
                  result = await showHikariConfirmationDialog(
                    context: context,
                    useRootNavigator: false,
                    title: 'Remove?',
                    content: const Text('Progress kept'),
                    confirmLabel: 'Remove',
                    destructive: true,
                  );
                },
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      final confirm = tester.widget<FilledButton>(find.byType(FilledButton));
      expect(confirm.autofocus, isFalse);
      expect(
        confirm.style!.backgroundColor!.resolve({}),
        Theme.of(tester.element(find.byType(FilledButton))).colorScheme.error,
      );
      if (action == 'confirm') {
        await tester.tap(find.text('Remove'));
      } else if (action == 'cancel') {
        await tester.tap(find.text('Cancel'));
      } else if (action == 'close') {
        await tester.tap(find.byTooltip('Close'));
      } else if (action == 'barrier') {
        await tester.tapAt(const Offset(4, 4));
      } else if (action == 'escape') {
        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      } else {
        await tester.binding.handlePopRoute();
      }
      await tester.pumpAndSettle();
      if (action == 'confirm') {
        expect((result as HikariCompleted<bool>).value, isTrue);
      } else {
        expect(result, isA<HikariCancelled<bool>>());
      }
    });
  }
  testWidgets('legacy adapter retains bool contract and root ownership', (
    tester,
  ) async {
    bool? result;
    await tester.pumpWidget(
      MaterialApp(
        home: Navigator(
          onGenerateRoute: (_) => MaterialPageRoute<void>(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () async {
                  result = await legacy.showHikariConfirmation(
                    context: context,
                    title: 'Legacy',
                    content: const Text('Body'),
                    confirmLabel: 'Confirm',
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
    final context = tester.element(find.text('Legacy'));
    expect(
      ModalRoute.of(context)!.navigator,
      Navigator.of(context, rootNavigator: true),
    );
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(result, isFalse);
  });
}
