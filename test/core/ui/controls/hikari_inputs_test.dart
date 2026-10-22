import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/controls/hikari_search_field.dart';
import 'package:hikari/core/ui/controls/hikari_segmented_control.dart';

void main() {
  test('controlled inputs have canonical owners without request policy', () {
    for (final name in ['search_field', 'choice_menu', 'segmented_control']) {
      final file = File('lib/core/ui/controls/hikari_$name.dart');
      expect(file.existsSync(), isTrue, reason: file.path);
      final source = file.readAsStringSync();
      expect(source, isNot(contains('package:hikari/app/')));
      if (name == 'search_field') expect(source, isNot(contains('Timer')));
    }
  });

  test('feature search callers use canonical field without legacy wrapper', () {
    for (final path in [
      'lib/features/catalog/widgets/catalog_search_content.dart',
      'lib/features/source_search/widgets/source_search_content.dart',
      'lib/features/plugins/widgets/plugin_browse_filters.dart',
    ]) {
      final source = File(path).readAsStringSync();
      expect(source, contains('HikariSearchField('), reason: path);
      expect(source, isNot(contains('HikariSearchBar(')), reason: path);
    }
    expect(
      File('lib/core/ui/components/hikari_search_bar.dart').existsSync(),
      isFalse,
    );
    expect(
      File('lib/core/ui/components/hikari_chip.dart').existsSync(),
      isFalse,
    );
  });

  testWidgets('search borrows controllers and submits only explicit actions', (
    tester,
  ) async {
    final first = TextEditingController();
    final second = TextEditingController(text: 'external');
    final focus = FocusNode();
    addTearDown(first.dispose);
    addTearDown(second.dispose);
    addTearDown(focus.dispose);
    final edits = <String>[];
    final submits = <String>[];
    Widget app(TextEditingController? controller) => MaterialApp(
      theme: HikariTheme.darkTheme(),
      home: Scaffold(
        body: HikariSearchField(
          controller: controller,
          focusNode: focus,
          onChanged: edits.add,
          onSubmitted: submits.add,
        ),
      ),
    );
    await tester.pumpWidget(app(first));
    await tester.enterText(find.byType(EditableText), 'typed');
    expect(edits, ['typed']);
    expect(submits, isEmpty);
    await tester.testTextInput.receiveAction(TextInputAction.search);
    expect(submits, ['typed']);
    expect(edits, ['typed']);
    await tester.pumpWidget(app(second));
    first.text = 'ignored';
    await tester.pump();
    expect(find.text('external'), findsOneWidget);
    second.clear();
    await tester.pump();
    expect(find.byTooltip('Clear search'), findsNothing);
    second.text = 'new';
    await tester.pump();
    await tester.tap(find.byTooltip('Submit search'));
    expect(submits, ['typed', 'new']);
    await tester.pumpWidget(app(null));
    await tester.pumpWidget(const SizedBox());
    first.clear();
    second.clear();
    focus.requestFocus();
    await tester.pump(const Duration(seconds: 1));
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'segments reflow using local width and retain controlled selection',
    (tester) async {
      var selected = 1;
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: MediaQuery(
            data: const MediaQueryData(textScaler: TextScaler.linear(3)),
            child: Scaffold(
              body: SingleChildScrollView(
                child: SizedBox(
                  width: 220,
                  child: HikariSegmentedControl<int>(
                    value: selected,
                    onChanged: (value) => selected = value,
                    segments: const [
                      ButtonSegment(
                        value: 1,
                        label: Text('First translated choice'),
                      ),
                      ButtonSegment(
                        value: 2,
                        label: Text('Second translated choice'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      expect(
        tester
            .widget<SegmentedButton<int>>(find.byType(SegmentedButton<int>))
            .direction,
        Axis.vertical,
      );
      await tester.tap(find.text('Second translated choice'));
      expect(selected, 2);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'segments measure explicit label typography before choosing axis',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: Scaffold(
            body: SizedBox(
              width: 375,
              child: HikariSegmentedControl<int>(
                value: 1,
                onChanged: (_) {},
                segments: const [
                  ButtonSegment(
                    value: 1,
                    label: Text('First', style: TextStyle(fontSize: 80)),
                  ),
                  ButtonSegment(
                    value: 2,
                    label: Text('Second', style: TextStyle(fontSize: 80)),
                  ),
                ],
              ),
            ),
          ),
        ),
      );
      expect(
        tester
            .widget<SegmentedButton<int>>(find.byType(SegmentedButton<int>))
            .direction,
        Axis.vertical,
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('root search theme handles 3x text in short IME viewport', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(375, 320));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: MediaQuery(
          data: const MediaQueryData(
            textScaler: TextScaler.linear(3),
            viewInsets: EdgeInsets.only(bottom: 140),
          ),
          child: const Scaffold(
            body: SingleChildScrollView(
              child: HikariSearchField(hintText: 'Search'),
            ),
          ),
        ),
      ),
    );
    expect(tester.takeException(), isNull);
  });
}
