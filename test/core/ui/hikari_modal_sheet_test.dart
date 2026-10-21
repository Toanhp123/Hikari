import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_modal_sheet.dart';

void main() {
  for (final back in [false, true]) {
    testWidgets(
      '${back ? 'Back' : 'barrier'} cancels default sheet without result',
      (tester) async {
        int? result = 99;
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: Builder(
                builder: (context) => TextButton(
                  child: const Text('Open'),
                  onPressed: () async {
                    result = await showHikariModalSheet<int>(
                      context: context,
                      builder: (_) =>
                          const SizedBox(height: 160, child: Text('Choices')),
                    );
                  },
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Open'));
        await tester.pumpAndSettle();
        if (back) {
          await tester.binding.handlePopRoute();
        } else {
          await tester.tapAt(const Offset(5, 5));
        }
        await tester.pumpAndSettle();
        expect(result, isNull);
        expect(find.byType(BottomSheet), findsNothing);
      },
    );
  }

  testWidgets('caller bounded long list scrolls above keyboard inset', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    tester.view.viewInsets = const FakeViewPadding(bottom: 200);
    addTearDown(tester.view.reset);
    int? result;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              child: const Text('Open'),
              onPressed: () async {
                result = await showHikariModalSheet<int>(
                  context: context,
                  builder: (sheetContext) => Padding(
                    padding: EdgeInsets.only(
                      bottom: MediaQuery.viewInsetsOf(sheetContext).bottom,
                    ),
                    child: SizedBox(
                      height: 240,
                      child: ListView.builder(
                        itemCount: 40,
                        itemBuilder: (_, index) => ListTile(
                          title: Text('Chapter $index'),
                          onTap: () => Navigator.pop(sheetContext, index),
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('Chapter 39'),
      200,
      scrollable: find.byType(Scrollable).last,
    );
    await tester.ensureVisible(find.text('Chapter 39'));
    await tester.pumpAndSettle();
    expect(
      tester.getBottomLeft(find.text('Chapter 39')).dy,
      lessThanOrEqualTo(440),
    );
    await tester.tap(find.text('Chapter 39'));
    await tester.pumpAndSettle();
    expect(result, 39);
    expect(tester.takeException(), isNull);
  });
  testWidgets('sheet returns its typed result and retains Material semantics', (
    tester,
  ) async {
    int? selected;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () async {
                selected = await showHikariModalSheet<int>(
                  context: context,
                  builder: (sheetContext) => SafeArea(
                    top: false,
                    child: ListTile(
                      title: const Text('Select chapter 12'),
                      onTap: () => Navigator.of(sheetContext).pop(12),
                    ),
                  ),
                );
              },
              child: const Text('Open chapters'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open chapters'));
    await tester.pumpAndSettle();
    final sheet = tester.widget<BottomSheet>(find.byType(BottomSheet));
    expect(sheet.showDragHandle, isTrue);
    await tester.tap(find.text('Select chapter 12'));
    await tester.pumpAndSettle();
    expect(selected, 12);
    expect(tester.takeException(), isNull);
  });

  testWidgets('non-dismissible barrier does not close a guarded sheet', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () => showHikariModalSheet<void>(
                context: context,
                isDismissible: false,
                enableDrag: false,
                builder: (sheetContext) => TextButton(
                  onPressed: () => Navigator.pop(sheetContext),
                  child: const Text('Explicitly close'),
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
    await tester.tapAt(const Offset(5, 5));
    await tester.pumpAndSettle();
    expect(find.text('Explicitly close'), findsOneWidget);
    await tester.tap(find.text('Explicitly close'));
    await tester.pumpAndSettle();
    expect(find.byType(BottomSheet), findsNothing);
    expect(tester.takeException(), isNull);
  });
}
