import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/layout/hikari_horizontal_shelf.dart';
import 'package:hikari/core/ui/media/hikari_poster_card.dart';

void main() {
  testWidgets(
    'stable shelf keys retain state and caller controllers survive replacement',
    (tester) async {
      var items = ['a', 'b', 'c'];
      final first = ScrollController();
      final second = ScrollController();
      addTearDown(first.dispose);
      addTearDown(second.dispose);
      Widget view(ScrollController? controller) => MaterialApp(
        home: Scaffold(
          body: HikariHorizontalShelf(
            controller: controller,
            itemCount: items.length,
            itemKeyBuilder: (i) => ValueKey(items[i]),
            itemBuilder: (_, i) =>
                TextField(key: ValueKey('field-${items[i]}')),
          ),
        ),
      );
      await tester.pumpWidget(view(first));
      final b = find.byKey(const ValueKey('field-b'));
      await tester.enterText(b, 'retained');
      final original = tester.state(b);
      items = ['b', 'c'];
      await tester.pumpWidget(view(first));
      expect(tester.state(b), same(original));
      expect(find.text('retained'), findsOneWidget);
      await tester.pumpWidget(view(second));
      expect(first.hasClients, isFalse);
      expect(second.hasClients, isTrue);
      await tester.pumpWidget(view(null));
      expect(second.hasClients, isFalse);
      await tester.pumpWidget(const SizedBox());
      // A disposed caller controller rejects listeners; both remain usable.
      void listener() {}
      first.addListener(listener);
      first.removeListener(listener);
      second.addListener(listener);
      second.removeListener(listener);
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets('RTL keyboard honors reduced motion without animated frames', (
    tester,
  ) async {
    final controller = ScrollController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(disableAnimations: true),
          child: Directionality(
            textDirection: TextDirection.rtl,
            child: Scaffold(
              body: HikariHorizontalShelf(
                controller: controller,
                itemCount: 50,
                itemBuilder: (_, i) => Text('عنصر $i'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowLeft);
    await tester.pump();
    expect(controller.offset, greaterThan(0));
    expect(controller.position.isScrollingNotifier.value, isFalse);
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
    await tester.pump();
    expect(controller.offset, 0);
    expect(tester.takeException(), isNull);
  });
  testWidgets('5000 cards stay lazy, scale and share header gutters', (
    tester,
  ) async {
    var builds = 0;
    final controller = ScrollController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(3)),
          child: Scaffold(
            body: SingleChildScrollView(
              child: HikariHorizontalShelf(
                controller: controller,
                header: const Text('Shelf'),
                itemCount: 5000,
                itemKeyBuilder: (i) => ValueKey(i),
                itemBuilder: (_, i) {
                  builds++;
                  return HikariPosterCard(
                    title: 'Hành trình العربية $i',
                    subtitle: 'Nguồn',
                    artwork: const SizedBox(),
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
    expect(builds, inExclusiveRange(0, 30));
    expect(tester.takeException(), isNull);
    expect(
      tester.getTopLeft(find.text('Shelf')).dx,
      tester.getTopLeft(find.byType(HikariPosterCard).first).dx,
    );
    await tester.sendEventToBinding(
      PointerScrollEvent(
        position: tester.getCenter(find.byType(ListView)),
        scrollDelta: const Offset(180, 0),
      ),
    );
    await tester.pumpAndSettle();
    expect(controller.offset, greaterThan(0));
  });
  testWidgets(
    'keyboard scrolls shelf without trapping Tab or vertical parent',
    (tester) async {
      final controller = ScrollController();
      final parent = ScrollController();
      final after = FocusNode();
      addTearDown(controller.dispose);
      addTearDown(parent.dispose);
      addTearDown(after.dispose);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SingleChildScrollView(
              controller: parent,
              child: Column(
                children: [
                  HikariHorizontalShelf(
                    controller: controller,
                    itemCount: 5000,
                    itemBuilder: (_, i) => Text('item $i'),
                  ),
                  TextButton(
                    focusNode: after,
                    onPressed: () {},
                    child: const Text('After'),
                  ),
                  const SizedBox(height: 2000),
                ],
              ),
            ),
          ),
        ),
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.sendKeyEvent(LogicalKeyboardKey.arrowRight);
      await tester.pumpAndSettle();
      expect(controller.offset, greaterThan(0));
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pumpAndSettle();
      expect(after.hasFocus, isTrue);
      await tester.drag(find.byType(ListView), const Offset(0, -200));
      await tester.pumpAndSettle();
      expect(parent.offset, greaterThan(0));
      expect(tester.takeException(), isNull);
    },
  );
}
