import 'dart:io';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/controls/hikari_button.dart';
import 'package:hikari/core/ui/controls/hikari_chip_row.dart';
import 'package:hikari/core/ui/controls/hikari_filter_chip.dart';
import 'package:hikari/core/ui/controls/hikari_icon_button.dart';

void main() {
  test('actions have canonical domain-free owners', () {
    for (final name in ['button', 'icon_button', 'filter_chip', 'chip_row']) {
      final file = File('lib/core/ui/controls/hikari_$name.dart');
      expect(file.existsSync(), isTrue, reason: file.path);
      expect(file.readAsStringSync(), isNot(contains('package:hikari/app/')));
    }
  });

  testWidgets('actions preserve Android targets at every size and 3x text', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(375, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    for (final scale in [1.0, 2.0, 3.0]) {
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: MediaQuery(
            data: MediaQueryData(textScaler: TextScaler.linear(scale)),
            child: Scaffold(
              body: SingleChildScrollView(
                child: Column(
                  children: [
                    for (final size in HikariButtonSize.values)
                      HikariButton(
                        label:
                            'Long translated action that must remain readable',
                        size: size,
                        onPressed: () {},
                      ),
                    HikariIconButton(
                      icon: const Icon(Icons.refresh),
                      tooltip: 'Refresh',
                      size: 24,
                      onPressed: () {},
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
      for (final element in find.byType(FilledButton).evaluate()) {
        final size = tester.getSize(find.byWidget(element.widget));
        expect(size.width, greaterThanOrEqualTo(48));
        expect(size.height, greaterThanOrEqualTo(48));
      }
      expect(
        tester.getSize(find.byType(IconButton)).shortestSide,
        greaterThanOrEqualTo(48),
      );
      for (final text in tester.widgetList<Text>(
        find.text('Long translated action that must remain readable'),
      )) {
        expect(
          text.maxLines,
          isNull,
          reason: 'Action label must reflow, not truncate',
        );
      }
      expect(tester.takeException(), isNull);
    }
  });

  testWidgets(
    'native action focus hover and keyboard activation remain available',
    (tester) async {
      var calls = 0;
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: Scaffold(
            body: HikariButton(label: 'Act', onPressed: () => calls++),
          ),
        ),
      );
      final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await mouse.addPointer(location: Offset.zero);
      await mouse.moveTo(tester.getCenter(find.byType(FilledButton)));
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      final buttonContext = tester.element(find.text('Act'));
      expect(Focus.of(buttonContext).hasFocus, isTrue);
      final theme = Theme.of(buttonContext);
      final overlay = theme.filledButtonTheme.style!.overlayColor;
      // Native defaults resolve focus/hover even when root style supplies no override.
      expect(
        overlay == null ||
            overlay.resolve({WidgetState.focused}) != Colors.transparent,
        isTrue,
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(calls, 1);
      await mouse.removePointer();
    },
  );

  testWidgets(
    'chip row preserves 8dp gaps and scrolls narrow local constraints',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: Scaffold(
            body: Center(
              child: SizedBox(
                width: 140,
                child: HikariChipRow(
                  children: [
                    HikariFilterChip(
                      label: 'First long filter',
                      selected: true,
                      onSelected: (_) {},
                    ),
                    HikariFilterChip(
                      label: 'Second long filter',
                      selected: false,
                      onSelected: (_) {},
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
      final chips = find.byType(FilterChip);
      expect(
        tester.getTopLeft(chips.at(1)).dx - tester.getTopRight(chips.at(0)).dx,
        8,
      );
      final before = tester.getTopLeft(chips.first).dx;
      await tester.drag(
        find.byType(SingleChildScrollView),
        const Offset(-180, 0),
      );
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(chips.first).dx, lessThan(before));
      expect(tester.takeException(), isNull);
    },
  );
}
