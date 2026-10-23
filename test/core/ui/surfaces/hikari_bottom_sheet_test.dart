import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_controller.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/bottom_sheet/hikari_bottom_sheet.dart';

void main() {
  for (final height in <double>[640, 440, 300]) {
    testWidgets('sheet at 375x$height with IME and 3x scrolls footer', (
      tester,
    ) async {
      tester.view.physicalSize = Size(375, height);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      HikariPresentationResult<int>? result;
      await tester.pumpWidget(
        MaterialApp(
          builder: (_, child) => MediaQuery(
            data: MediaQueryData(
              size: Size(375, height),
              viewInsets: const EdgeInsets.only(bottom: 120),
              textScaler: TextScaler.linear(3),
            ),
            child: child!,
          ),
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () async {
                  result = await showHikariBottomSheet<int>(
                    context: context,
                    useRootNavigator: false,
                    contentBuilder: (_, controller) => HikariBottomSheet(
                      title: const Text('Choose source'),
                      body: Text(List.filled(30, 'Sheet body').join(' ')),
                      actions: [
                        TextButton(
                          onPressed: () => controller.complete(42),
                          child: const Text('Submit'),
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
      expect(find.byType(SingleChildScrollView), findsOneWidget);
      expect(
        tester.widget<BottomSheet>(find.byType(BottomSheet)).showDragHandle,
        isTrue,
      );
      await tester.ensureVisible(find.text('Submit'));
      await tester.tap(find.text('Submit'));
      await tester.pumpAndSettle();
      expect((result as HikariCompleted<int>).value, 42);
      expect(find.text('Open'), findsOneWidget);
    });
  }

  for (final exit in ['barrier', 'back', 'drag', 'dispose']) {
    testWidgets('$exit settles pending sheet once', (tester) async {
      HikariPresentationResult<int>? result;
      late HikariPresentationController<int> controller;
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () async {
                  result = await showHikariBottomSheet<int>(
                    context: context,
                    useRootNavigator: true,
                    contentBuilder: (_, value) {
                      controller = value;
                      return const HikariBottomSheet(
                        title: Text('Choose'),
                        body: SizedBox(height: 200, child: Text('Body')),
                      );
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
      if (exit == 'dispose') {
        await tester.pumpWidget(const SizedBox());
      } else if (exit == 'barrier') {
        await tester.tapAt(const Offset(4, 4));
      } else if (exit == 'back') {
        await tester.binding.handlePopRoute();
      } else {
        await tester.fling(
          find.byType(BottomSheet),
          const Offset(0, 500),
          1800,
        );
      }
      await tester.pumpAndSettle();
      expect(result, isA<HikariCancelled<int>>());
      expect(
        (result as HikariCancelled<int>).reason,
        exit == 'dispose'
            ? HikariDismissReason.routeDisposed
            : HikariDismissReason.routeDismissed,
      );
      expect(controller.complete(1), isFalse);
      expect(find.byType(BottomSheet), findsNothing);
    });
  }

  testWidgets('protected sheet vetoes drag barrier and first Back before pop', (
    tester,
  ) async {
    late HikariPresentationController<int> controller;
    HikariPresentationResult<int>? result;
    var blockedBack = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () async {
                result = await showHikariBottomSheet<int>(
                  context: context,
                  useRootNavigator: false,
                  contentBuilder: (_, value) {
                    controller = value;
                    return const HikariBottomSheet(
                      title: Text('Protected'),
                      body: SizedBox(height: 200),
                    );
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
    controller.setDismissalPolicy(
      canDismiss: false,
      guard: (reason) {
        if (reason == HikariDismissReason.back) {
          blockedBack++;
          controller.setDismissalPolicy(canDismiss: true);
        }
        return false;
      },
    );
    await tester.pumpAndSettle();
    await tester.fling(find.byType(BottomSheet), const Offset(0, 500), 1800);
    await tester.pumpAndSettle();
    expect(result, isNull);
    await tester.tapAt(const Offset(4, 4));
    await tester.pumpAndSettle();
    expect(result, isNull);
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(blockedBack, 1);
    expect(result, isNull);
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(result, isA<HikariCancelled<int>>());
  });

  testWidgets('resize retains route content and form state', (tester) async {
    final field = TextEditingController();
    addTearDown(field.dispose);
    Route<dynamic>? route;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () => showHikariBottomSheet<void>(
                context: context,
                useRootNavigator: false,
                contentBuilder: (context, _) {
                  final current = ModalRoute.of(context)!;
                  if (route != null) expect(current, same(route));
                  route = current;
                  return HikariBottomSheet(
                    title: const Text('Form'),
                    body: TextField(controller: field),
                  );
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
    await tester.enterText(find.byType(TextField), 'persist');
    tester.view.physicalSize = const Size(1440, 440);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpAndSettle();
    expect(field.text, 'persist');
    expect(find.byType(BottomSheet), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
  });
}
