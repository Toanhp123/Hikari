import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_controller.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/adaptive/show_hikari_adaptive.dart';

void main() {
  for (final width in <double>[375, 1440]) {
    testWidgets('Escape at $width reaches guard without a focused control', (
      tester,
    ) async {
      tester.view.physicalSize = Size(width, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      HikariPresentationResult<int>? result;
      var attempts = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () async {
                  result = await showHikariAdaptive<int>(
                    context: context,
                    purpose: HikariPresentationPurpose.selection,
                    useRootNavigator: false,
                    contentBuilder: (_, controller) {
                      controller.setDismissalPolicy(
                        canDismiss: false,
                        guard: (reason) {
                          expect(reason, HikariDismissReason.back);
                          attempts++;
                          return attempts > 1;
                        },
                      );
                      return const Text('Nested step');
                    },
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
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(attempts, 1);
      expect(result, isNull);
      expect(find.text('Nested step'), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(attempts, 2);
      expect((result as HikariCancelled<int>).reason, HikariDismissReason.back);
      expect(find.text('Nested step'), findsNothing);
      expect(tester.takeException(), isNull);
    });
  }
  for (final width in <double>[375, 1440]) {
    testWidgets(
      'awaited result at $width exposes caller route before follow-up navigation',
      (tester) async {
        tester.view.physicalSize = Size(width, 640);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        late HikariPresentationController<int> controller;
        bool? callerCurrent;
        await tester.pumpWidget(
          MaterialApp(
            home: Builder(
              builder: (context) => Scaffold(
                body: TextButton(
                  onPressed: () async {
                    await showHikariAdaptive<int>(
                      context: context,
                      purpose: HikariPresentationPurpose.selection,
                      useRootNavigator: false,
                      contentBuilder: (_, value) {
                        controller = value;
                        return const Text('Pending');
                      },
                    );
                    if (context.mounted) {
                      callerCurrent = ModalRoute.of(context)!.isCurrent;
                    }
                  },
                  child: const Text('Open'),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Open'));
        await tester.pumpAndSettle();
        controller.complete(1);
        await tester.pumpAndSettle();
        expect(callerCurrent, isTrue);
      },
    );
  }
  for (final width in <double>[375, 1440]) {
    testWidgets('synchronous content settlement at $width is build-safe', (
      tester,
    ) async {
      tester.view.physicalSize = Size(width, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      HikariPresentationResult<int>? result;
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () async {
                  result = await showHikariAdaptive<int>(
                    context: context,
                    purpose: HikariPresentationPurpose.selection,
                    useRootNavigator: false,
                    contentBuilder: (_, controller) {
                      controller.setDismissalPolicy(canDismiss: false);
                      controller.complete(42);
                      return const Text('Immediate');
                    },
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
      expect((result as HikariCompleted<int>).value, 42);
      expect(find.text('Open'), findsOneWidget);
    });
  }
  for (final width in <double>[375, 1440]) {
    for (final root in [false, true]) {
      testWidgets(
        'width $width root $root retains session on resize and submits null once',
        (tester) async {
          tester.view.physicalSize = Size(width, 640);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          final rootKey = GlobalKey<NavigatorState>();
          final nestedKey = GlobalKey<NavigatorState>();
          HikariPresentationResult<String?>? result;
          late HikariPresentationController<String?> controller;
          Route<dynamic>? active;
          var completions = 0;
          final field = TextEditingController();
          addTearDown(field.dispose);
          await tester.pumpWidget(
            MaterialApp(
              navigatorKey: rootKey,
              home: Navigator(
                key: nestedKey,
                onGenerateRoute: (_) => MaterialPageRoute<void>(
                  builder: (context) => Scaffold(
                    body: TextButton(
                      onPressed: () async {
                        result = await showHikariAdaptive<String?>(
                          context: context,
                          purpose: HikariPresentationPurpose.selection,
                          useRootNavigator: root,
                          contentBuilder: (context, value) {
                            controller = value;
                            final route = ModalRoute.of(context)!;
                            if (active != null) expect(route, same(active));
                            active = route;
                            expect(
                              route.navigator,
                              root
                                  ? rootKey.currentState
                                  : nestedKey.currentState,
                            );
                            return TextField(controller: field);
                          },
                        );
                        completions++;
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
          await tester.enterText(find.byType(TextField), 'retain');
          final before = active;
          for (final next in <double>[
            599,
            600,
            601,
            839,
            840,
            841,
            1200,
            1440,
            375,
          ]) {
            tester.view.physicalSize = Size(next, 440);
            await tester.pumpAndSettle();
            expect(active, same(before));
            expect(field.text, 'retain');
            expect(
              find.byType(BottomSheet),
              width < 840 ? findsOneWidget : findsNothing,
            );
            expect(
              find.byType(Dialog),
              width >= 840 ? findsOneWidget : findsNothing,
            );
            expect(tester.takeException(), isNull);
          }
          expect(controller.complete(null), isTrue);
          expect(controller.cancel(HikariDismissReason.back), isFalse);
          await tester.pumpAndSettle();
          expect((result as HikariCompleted<String?>).value, isNull);
          expect(completions, 1);
          expect(find.text('Open'), findsOneWidget);
          expect(controller.complete('late'), isFalse);
        },
      );
    }
  }

  testWidgets(
    'settlement removes owned route only when another route is above it',
    (tester) async {
      final navigator = GlobalKey<NavigatorState>();
      late HikariPresentationController<int> controller;
      await tester.pumpWidget(
        MaterialApp(
          navigatorKey: navigator,
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => showHikariAdaptive<int>(
                  context: context,
                  purpose: HikariPresentationPurpose.confirmation,
                  useRootNavigator: true,
                  contentBuilder: (_, value) {
                    controller = value;
                    return const Text('Pending');
                  },
                ),
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      navigator.currentState!.push(
        MaterialPageRoute<void>(
          builder: (_) => const Scaffold(body: Text('Above')),
        ),
      );
      await tester.pumpAndSettle();
      controller.complete(1);
      await tester.pumpAndSettle();
      expect(find.text('Above'), findsOneWidget);
      navigator.currentState!.pop();
      await tester.pumpAndSettle();
      expect(find.text('Open'), findsOneWidget);
    },
  );
}
