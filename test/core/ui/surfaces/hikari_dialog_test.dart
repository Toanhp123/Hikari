import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_controller.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/dialog/hikari_dialog.dart';

void main() {
  testWidgets('nullable completion settles once without popping caller', (
    tester,
  ) async {
    HikariPresentationResult<String?>? result;
    late HikariPresentationController<String?> controller;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            return Scaffold(
              body: TextButton(
                onPressed: () async {
                  result = await showHikariDialog<String?>(
                    context: context,
                    useRootNavigator: false,
                    contentBuilder: (_, value) {
                      controller = value;
                      return HikariDialog(
                        title: const Text('Choose'),
                        body: const Text('Value'),
                        actions: [
                          TextButton(
                            onPressed: () {
                              value.complete(null);
                              value.complete('late');
                            },
                            child: const Text('Submit'),
                          ),
                        ],
                      );
                    },
                  );
                },
                child: const Text('Open'),
              ),
            );
          },
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Submit'));
    await tester.pumpAndSettle();
    expect(result, isA<HikariCompleted<String?>>());
    expect((result as HikariCompleted<String?>).value, isNull);
    expect(controller.complete('disposed'), isFalse);
    expect(find.text('Open'), findsOneWidget);
  });

  for (final dismiss in ['barrier', 'back', 'escape', 'close']) {
    testWidgets('$dismiss cancels without approval and focus returns', (
      tester,
    ) async {
      final invoker = FocusNode();
      addTearDown(invoker.dispose);
      HikariPresentationResult<bool>? result;
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) {
              return Scaffold(
                body: TextButton(
                  focusNode: invoker,
                  onPressed: () async {
                    result = await showHikariDialog<bool>(
                      context: context,
                      useRootNavigator: true,
                      contentBuilder: (_, controller) => HikariDialog(
                        title: const Text('Confirm'),
                        body: const Text('No automatic consent'),
                        onClose: () =>
                            controller.cancel(HikariDismissReason.closeButton),
                        actions: [
                          TextButton(
                            autofocus: true,
                            onPressed: () => controller.cancel(
                              HikariDismissReason.cancelAction,
                            ),
                            child: const Text('Cancel'),
                          ),
                        ],
                      ),
                    );
                  },
                  child: const Text('Open'),
                ),
              );
            },
          ),
        ),
      );
      invoker.requestFocus();
      await tester.pump();
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      expect(invoker.hasFocus, isFalse);
      if (dismiss == 'barrier') {
        await tester.tapAt(const Offset(4, 4));
      } else if (dismiss == 'back') {
        await tester.binding.handlePopRoute();
      } else if (dismiss == 'escape') {
        await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      } else {
        await tester.tap(find.byTooltip('Close'));
      }
      await tester.pumpAndSettle();
      expect(result, isA<HikariCancelled<bool>>());
      expect((result as HikariCancelled<bool>).reason, switch (dismiss) {
        'barrier' => HikariDismissReason.barrier,
        'close' => HikariDismissReason.closeButton,
        'escape' => HikariDismissReason.back,
        _ => HikariDismissReason.routeDismissed,
      });
      expect(invoker.hasFocus, isTrue);
    });
  }

  testWidgets('protected dialog barrier has no dismissal affordance', (
    tester,
  ) async {
    late HikariPresentationController<void> controller;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () => showHikariDialog<void>(
              context: context,
              useRootNavigator: false,
              contentBuilder: (_, value) {
                controller = value;
                return const HikariDialog(
                  title: Text('Protected'),
                  body: Text('Body'),
                );
              },
            ),
            child: const Text('Open'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    controller.setDismissalPolicy(canDismiss: false);
    await tester.pumpAndSettle();
    expect(
      tester.widget<ModalBarrier>(find.byType(ModalBarrier).last).dismissible,
      isFalse,
    );
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();
    expect(controller.isSettled, isFalse);
    controller.setDismissalPolicy(canDismiss: true);
    await tester.pumpAndSettle();
    expect(
      tester.widget<ModalBarrier>(find.byType(ModalBarrier).last).dismissible,
      isTrue,
    );
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();
    expect(find.byType(Dialog), findsNothing);
  });

  testWidgets('bounded dialog scrolls at 3x in short window', (tester) async {
    tester.view.physicalSize = const Size(1000, 440);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        builder: (_, child) => MediaQuery(
          data: MediaQueryData(
            size: const Size(1000, 440),
            textScaler: TextScaler.linear(3),
          ),
          child: child!,
        ),
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () {
                showHikariDialog<void>(
                  context: context,
                  useRootNavigator: false,
                  contentBuilder: (_, controller) => HikariDialog(
                    title: const Text('Long title wraps safely'),
                    body: Text(
                      List.filled(30, 'Long dialog content.').join(' '),
                    ),
                    actions: [
                      TextButton(
                        onPressed: () =>
                            controller.cancel(HikariDismissReason.cancelAction),
                        child: const Text('Cancel'),
                      ),
                    ],
                  ),
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
    expect(tester.takeException(), isNull);
    final surface = find.descendant(
      of: find.byType(Dialog),
      matching: find.byWidgetPredicate(
        (widget) => widget is Material && widget.type == MaterialType.card,
      ),
    );
    expect(tester.getSize(surface).width, lessThanOrEqualTo(560));
    await tester.ensureVisible(find.text('Cancel'));
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(find.byType(Dialog), findsNothing);
  });

  for (final root in [false, true]) {
    testWidgets('explicit root=$root owns route and teardown settles future', (
      tester,
    ) async {
      final rootKey = GlobalKey<NavigatorState>();
      final nestedKey = GlobalKey<NavigatorState>();
      HikariPresentationResult<int>? result;
      late HikariPresentationController<int> controller;
      await tester.pumpWidget(
        MaterialApp(
          navigatorKey: rootKey,
          home: Navigator(
            key: nestedKey,
            onGenerateRoute: (_) => MaterialPageRoute<void>(
              builder: (context) => Scaffold(
                body: TextButton(
                  onPressed: () async {
                    result = await showHikariDialog<int>(
                      context: context,
                      useRootNavigator: root,
                      contentBuilder: (context, value) {
                        controller = value;
                        expect(
                          ModalRoute.of(context)!.navigator,
                          root ? rootKey.currentState : nestedKey.currentState,
                        );
                        return const HikariDialog(
                          title: Text('Owned'),
                          body: Text('Pending'),
                        );
                      },
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
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
      expect(result, isA<HikariCancelled<int>>());
      expect(
        (result as HikariCancelled<int>).reason,
        HikariDismissReason.routeDisposed,
      );
      expect(controller.complete(7), isFalse);
    });
  }

  testWidgets('Tab stays within dialog', (tester) async {
    final first = FocusNode();
    final second = FocusNode();
    addTearDown(first.dispose);
    addTearDown(second.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () => showHikariDialog<void>(
                context: context,
                useRootNavigator: false,
                contentBuilder: (_, _) => HikariDialog(
                  title: const Text('Focus'),
                  body: const Text('Body'),
                  actions: [
                    TextButton(
                      focusNode: first,
                      autofocus: true,
                      onPressed: () {},
                      child: const Text('First'),
                    ),
                    TextButton(
                      focusNode: second,
                      onPressed: () {},
                      child: const Text('Second'),
                    ),
                  ],
                ),
              ),
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    expect(first.hasFocus, isTrue);
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    expect(second.hasFocus, isTrue);
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    expect(first.hasFocus, isTrue);
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
  });
}
