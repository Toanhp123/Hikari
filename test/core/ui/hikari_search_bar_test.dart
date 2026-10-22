import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/controls/hikari_search_field.dart';

void main() {
  testWidgets('selection-only controller updates never duplicate an edit', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final edits = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HikariSearchField(controller: controller, onChanged: edits.add),
        ),
      ),
    );
    await tester.enterText(find.byType(EditableText), 'query');
    await tester.pump(const Duration(milliseconds: 100));
    controller.selection = const TextSelection.collapsed(offset: 1);
    await tester.pump(const Duration(milliseconds: 200));
    expect(edits, ['query']);
  });
  testWidgets('external clear rebuilds actions without emitting another edit', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    final edits = <String>[];
    final submits = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HikariSearchField(
            controller: controller,
            onChanged: edits.add,
            onSubmitted: submits.add,
          ),
        ),
      ),
    );
    await tester.enterText(find.byType(EditableText), 'stale');
    await tester.pump(const Duration(milliseconds: 100));
    controller.clear();
    await tester.pump();
    expect(find.byTooltip('Clear search'), findsNothing);
    expect(find.byTooltip('Submit search'), findsNothing);
    await tester.pump(const Duration(milliseconds: 400));
    expect(edits, ['stale']);
    expect(submits, isEmpty);
    controller.text = 'external';
    await tester.pump();
    expect(find.byTooltip('Clear search'), findsOneWidget);
    await tester.tap(find.byTooltip('Submit search'));
    await tester.pump();
    expect(edits, ['stale']);
    expect(submits, ['external']);
  });

  testWidgets(
    'replacement unsubscribes old controller and never disposes caller controllers',
    (tester) async {
      final old = TextEditingController();
      final next = TextEditingController(text: 'next');
      addTearDown(old.dispose);
      addTearDown(next.dispose);
      final edits = <String>[];
      Widget app(TextEditingController? controller) => MaterialApp(
        home: Scaffold(
          body: HikariSearchField(controller: controller, onChanged: edits.add),
        ),
      );
      await tester.pumpWidget(app(old));
      await tester.enterText(find.byType(EditableText), 'pending');
      await tester.pumpWidget(app(next));
      await tester.pump(const Duration(milliseconds: 400));
      expect(edits, ['pending']);
      old.text = 'ignored';
      await tester.pump();
      expect(find.text('next'), findsOneWidget);
      next.clear();
      await tester.pump();
      expect(find.byTooltip('Clear search'), findsNothing);
      await tester.pumpWidget(app(null));
      next.text = 'still owned';
      await tester.enterText(find.byType(EditableText), 'internal');
      await tester.pump(const Duration(milliseconds: 299));
      expect(edits, ['pending', 'internal']);
      await tester.pump(const Duration(milliseconds: 1));
      expect(edits, ['pending', 'internal']);
      await tester.pumpWidget(const SizedBox());
      old.clear();
      next.clear();
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('keyboard submit emits once without duplicating edits', (
    tester,
  ) async {
    final edits = <String>[];
    final submits = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HikariSearchField(
            onChanged: edits.add,
            onSubmitted: submits.add,
          ),
        ),
      ),
    );
    await tester.enterText(find.byType(EditableText), 'query');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pump(const Duration(milliseconds: 500));
    expect(edits, ['query']);
    expect(submits, ['query']);
  });
  testWidgets(
    'local filter emits immediately and Clear resets controlled text',
    (tester) async {
      final updates = <String>[];
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: HikariSearchField(
              hintText: 'Search plugins',
              onChanged: updates.add,
            ),
          ),
        ),
      );
      await tester.enterText(find.byType(EditableText), 'manga');
      await tester.pump();
      expect(updates.last, 'manga');
      await tester.tap(find.byTooltip('Clear search'));
      await tester.pump();
      expect(updates.last, '');
      expect(find.text('manga'), findsNothing);
    },
  );

  testWidgets('remote search only submits on explicit action when wired', (
    tester,
  ) async {
    final submits = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HikariSearchField(onChanged: (_) {}, onSubmitted: submits.add),
        ),
      ),
    );
    await tester.enterText(find.byType(EditableText), 'one');
    await tester.pump();
    expect(submits, isEmpty);
    await tester.tap(find.byTooltip('Submit search'));
    await tester.pump();
    expect(submits, ['one']);
  });
}
