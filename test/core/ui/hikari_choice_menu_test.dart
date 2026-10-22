import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/controls/hikari_choice_menu.dart';

void main() {
  testWidgets('empty options keep disabled placeholder and caller error', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HikariChoiceMenu<String>(
            label: 'Language',
            value: 'gone',
            options: const [],
            errorText: 'Options unavailable',
            onChanged: (_) => fail('disabled'),
          ),
        ),
      ),
    );
    expect(find.text('Select Language'), findsOneWidget);
    expect(find.text('Options unavailable'), findsOneWidget);
    expect(
      tester.widget<OutlinedButton>(find.byType(OutlinedButton)).onPressed,
      isNull,
    );
  });

  testWidgets('local narrow constraints and long menu labels do not overflow', (
    tester,
  ) async {
    const long =
        'Very long language name that must wrap rather than overflow its menu';
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: MediaQuery(
            data: const MediaQueryData(
              size: Size(800, 600),
              textScaler: TextScaler.linear(2),
            ),
            child: Align(
              alignment: Alignment.topLeft,
              child: SizedBox(
                width: 180,
                child: Wrap(
                  children: [
                    HikariChoiceMenu<String>(
                      label: 'Language',
                      value: 'en',
                      icon: Icons.language,
                      options: const [
                        HikariChoiceOption(value: 'en', label: long),
                      ],
                      onChanged: (_) {},
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
    expect(tester.takeException(), isNull);
    expect(
      tester.getSize(find.byType(OutlinedButton)).width,
      lessThanOrEqualTo(180),
    );
    await tester.tap(find.byType(OutlinedButton));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    final text = tester.renderObject<RenderBox>(find.text(long));
    expect(text.size.height, greaterThan(40));
  });

  testWidgets('short filters keep natural width in Wrap', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 600,
            child: Wrap(
              children: [
                HikariChoiceMenu<int>(
                  label: 'View',
                  value: 1,
                  options: const [HikariChoiceOption(value: 1, label: 'All')],
                  onChanged: (_) {},
                ),
              ],
            ),
          ),
        ),
      ),
    );
    expect(tester.getSize(find.byType(OutlinedButton)).width, lessThan(300));
  });

  testWidgets(
    'dynamic options preserve caller value without inventing selection',
    (tester) async {
      var options = const [HikariChoiceOption(value: 'en', label: 'English')];
      late StateSetter update;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: StatefulBuilder(
              builder: (context, setState) {
                update = setState;
                return HikariChoiceMenu<String>(
                  label: 'Language',
                  value: 'en',
                  options: options,
                  onChanged: (_) => fail('external options never emit'),
                );
              },
            ),
          ),
        ),
      );
      update(() => options = []);
      await tester.pump();
      expect(find.text('Select Language'), findsOneWidget);
      update(
        () =>
            options = const [HikariChoiceOption(value: 'en', label: 'Renamed')],
      );
      await tester.pump();
      expect(find.text('Language: Renamed'), findsOneWidget);
    },
  );

  testWidgets(
    'keyboard opens, selects and restores trigger focus on selection and Escape',
    (tester) async {
      String? selected = 'en';
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: StatefulBuilder(
              builder: (context, update) => HikariChoiceMenu<String>(
                label: 'Language',
                value: selected,
                options: const [
                  HikariChoiceOption(value: 'en', label: 'English'),
                  HikariChoiceOption(value: 'vi', label: 'Vietnamese'),
                ],
                onChanged: (value) => update(() => selected = value),
              ),
            ),
          ),
        ),
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(selected, 'vi');
      final button = tester.widget<OutlinedButton>(find.byType(OutlinedButton));
      expect(button.focusNode!.hasFocus, isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pumpAndSettle();
      expect(button.focusNode!.hasFocus, isTrue);
      expect(find.widgetWithText(MenuItemButton, 'Vietnamese'), findsNothing);
    },
  );
  testWidgets('controlled selection, null All, and selection check', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    String? selected;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: StatefulBuilder(
            builder: (context, update) => HikariChoiceMenu<String>(
              label: 'Language',
              value: selected,
              icon: Icons.language_rounded,
              options: const [
                HikariChoiceOption<String>(
                  value: null,
                  label: 'All languages',
                  shortLabel: 'All',
                ),
                HikariChoiceOption<String>(value: 'en', label: 'EN'),
                HikariChoiceOption<String>(value: 'vi', label: 'VI'),
              ],
              onChanged: (value) => update(() => selected = value),
            ),
          ),
        ),
      ),
    );
    expect(find.text('Language: All'), findsOneWidget);
    await tester.tap(find.text('Language: All'));
    await tester.pumpAndSettle();
    final all = find.widgetWithText(MenuItemButton, 'All languages');
    final selectedSemantics = find
        .ancestor(
          of: all,
          matching: find.byWidgetPredicate(
            (widget) =>
                widget is Semantics && widget.properties.selected == true,
          ),
        )
        .first;
    expect(
      tester.getSemantics(selectedSemantics).flagsCollection.isSelected,
      Tristate.isTrue,
    );
    expect(
      find.descendant(of: all, matching: find.byIcon(Icons.check_rounded)),
      findsOneWidget,
    );
    await tester.tap(find.widgetWithText(MenuItemButton, 'VI'));
    await tester.pumpAndSettle();
    expect(selected, 'vi');
    expect(find.text('Language: VI'), findsOneWidget);
    await tester.tap(find.text('Language: VI'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(MenuItemButton, 'All languages'));
    await tester.pumpAndSettle();
    expect(selected, isNull);
    expect(find.text('Language: All'), findsOneWidget);
    expect(tester.takeException(), isNull);
    semantics.dispose();
  });

  testWidgets('large catalog menu is bounded and scrollable', (tester) async {
    tester.view.physicalSize = const Size(320, 700);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: HikariChoiceMenu<String>(
            label: 'Language',
            value: null,
            options: [
              const HikariChoiceOption<String>(value: null, label: 'All'),
              for (var i = 0; i < 30; i++)
                HikariChoiceOption<String>(value: '$i', label: 'L$i'),
            ],
            onChanged: (_) {},
          ),
        ),
      ),
    );
    await tester.tap(find.text('Language: All'));
    await tester.pumpAndSettle();
    final menu = tester.widget<MenuAnchor>(find.byType(MenuAnchor));
    expect(menu.style?.maximumSize?.resolve({})?.height, 320);
    await tester.ensureVisible(find.widgetWithText(MenuItemButton, 'L29'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('external value changes update the trigger without local state', (
    tester,
  ) async {
    String? value = 'en';
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: StatefulBuilder(
            builder: (context, update) => Column(
              children: [
                HikariChoiceMenu<String>(
                  label: 'Language',
                  value: value,
                  options: const [
                    HikariChoiceOption(value: 'en', label: 'English'),
                    HikariChoiceOption(value: 'vi', label: 'Vietnamese'),
                  ],
                  onChanged: (next) => update(() => value = next),
                ),
                TextButton(
                  onPressed: () => update(() => value = 'vi'),
                  child: const Text('External update'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    expect(find.text('Language: English'), findsOneWidget);
    await tester.tap(find.text('External update'));
    await tester.pump();
    expect(find.text('Language: Vietnamese'), findsOneWidget);
  });

  testWidgets('form owns required validation, error and saved value', (
    tester,
  ) async {
    final formKey = GlobalKey<FormState>();
    String? saved;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Form(
            key: formKey,
            child: FormField<String>(
              validator: (value) => value == null ? 'Choose a language.' : null,
              onSaved: (value) => saved = value,
              autovalidateMode: AutovalidateMode.onUserInteraction,
              builder: (field) => HikariChoiceMenu<String>(
                label: 'Language',
                value: field.value,
                options: const [
                  HikariChoiceOption(value: 'en', label: 'English'),
                  HikariChoiceOption(value: 'vi', label: 'Vietnamese'),
                ],
                onChanged: field.didChange,
                errorText: field.errorText,
                expanded: true,
              ),
            ),
          ),
        ),
      ),
    );
    expect(find.text('Select Language'), findsOneWidget);
    expect(formKey.currentState!.validate(), isFalse);
    await tester.pump();
    expect(find.text('Choose a language.'), findsOneWidget);
    await tester.tap(find.text('Select Language'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(MenuItemButton, 'Vietnamese'));
    await tester.pumpAndSettle();
    expect(find.text('Choose a language.'), findsNothing);
    expect(formKey.currentState!.validate(), isTrue);
    formKey.currentState!.save();
    expect(saved, 'vi');
    formKey.currentState!.reset();
    await tester.pump();
    expect(find.text('Select Language'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('disabled selection cannot emit changes', (tester) async {
    var calls = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HikariChoiceMenu<int>(
            label: 'View',
            value: 0,
            options: const [
              HikariChoiceOption(value: 0, label: 'Available'),
              HikariChoiceOption(value: 1, label: 'Installed'),
            ],
            enabled: false,
            onChanged: (_) => calls++,
          ),
        ),
      ),
    );
    final button = tester.widget<OutlinedButton>(find.byType(OutlinedButton));
    expect(button.onPressed, isNull);
    await tester.tap(find.text('View: Available'), warnIfMissed: false);
    await tester.pump();
    expect(calls, 0);
    expect(find.widgetWithText(MenuItemButton, 'Installed'), findsNothing);
  });
}
