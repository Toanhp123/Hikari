import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/layout/hikari_adaptive_grid.dart';
import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/media/hikari_card_metrics.dart';
import 'package:hikari/core/ui/media/hikari_poster_card.dart';

void main() {
  test('grid defaults to foundation bound and rejects invalid geometry', () {
    final grid = HikariAdaptiveGrid.builder(
      itemCount: 0,
      itemBuilder: (_, _) => const SizedBox(),
      itemExtentBuilder: (_, _) => 100,
    );
    expect(grid.maxItemWidth, HikariBreakpoints.posterGridMaxExtent);
    for (final width in [0.0, -1.0, double.nan, double.infinity]) {
      expect(
        () => HikariAdaptiveGrid.builder(
          itemCount: 1,
          maxItemWidth: width,
          itemBuilder: (_, _) => const SizedBox(),
          itemExtentBuilder: (_, _) => 100,
        ),
        throwsAssertionError,
      );
    }
  });
  for (final sliver in [false, true]) {
    testWidgets('sliver=$sliver hides items when padding exhausts local width', (
      tester,
    ) async {
      var builds = 0;
      var keyCalls = 0;
      var extentCalls = 0;
      for (final width in <double>[
        HikariSpacing.lg,
        HikariSpacing.lg * 2,
        375,
      ]) {
        builds = 0;
        keyCalls = 0;
        extentCalls = 0;
        final grid = sliver
            ? HikariAdaptiveGrid.sliver(
                itemCount: 100,
                itemKeyBuilder: (index) {
                  keyCalls++;
                  return ValueKey(index);
                },
                itemExtentBuilder: (_, itemWidth) {
                  extentCalls++;
                  return itemWidth * 1.5 + 50;
                },
                itemBuilder: (_, index) {
                  builds++;
                  return Text('item $index');
                },
              )
            : HikariAdaptiveGrid.builder(
                itemCount: 100,
                itemKeyBuilder: (index) {
                  keyCalls++;
                  return ValueKey(index);
                },
                itemExtentBuilder: (_, itemWidth) {
                  extentCalls++;
                  return itemWidth * 1.5 + 50;
                },
                itemBuilder: (_, index) {
                  builds++;
                  return Text('item $index');
                },
              );
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: Align(
                alignment: Alignment.topLeft,
                child: SizedBox(
                  width: width,
                  height: 400,
                  child: sliver ? CustomScrollView(slivers: [grid]) : grid,
                ),
              ),
            ),
          ),
        );
        expect(tester.takeException(), isNull);
        if (width <= HikariSpacing.lg * 2) {
          expect(builds, 0);
          expect(keyCalls, 0);
          expect(extentCalls, 0);
          expect(find.byType(SliverGrid), findsNothing);
          expect(find.byType(GridView), findsNothing);
        } else {
          expect(builds, greaterThan(0));
          expect(keyCalls, greaterThan(0));
          expect(extentCalls, greaterThan(0));
          if (sliver) {
            expect(find.byType(SliverGrid), findsOneWidget);
          } else {
            expect(find.byType(GridView), findsOneWidget);
          }
        }
      }
    });
  }

  testWidgets('real cards reserve scaled text within nested local width', (
    tester,
  ) async {
    final controller = ScrollController();
    addTearDown(controller.dispose);
    for (final width in [
      375.0,
      599.0,
      600.0,
      601.0,
      839.0,
      840.0,
      841.0,
      1200.0,
      1440.0,
    ]) {
      await tester.binding.setSurfaceSize(Size(1440, 400));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(
        MaterialApp(
          home: MediaQuery(
            data: const MediaQueryData(textScaler: TextScaler.linear(3)),
            child: Directionality(
              textDirection: TextDirection.rtl,
              child: Scaffold(
                body: SizedBox(
                  width: width,
                  child: HikariAdaptiveGrid.builder(
                    controller: controller,
                    itemCount: 5000,
                    itemExtentBuilder: (context, width) => hikariCardExtent(
                      context,
                      width: width,
                      hasSubtitle: true,
                    ),
                    itemBuilder: (_, i) => HikariPosterCard(
                      title: 'Hành trình dài العربية $i',
                      subtitle: 'Nguồn truyện العربية',
                      artwork: const SizedBox(),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      expect(tester.takeException(), isNull);
      final grid = tester.widget<GridView>(find.byType(GridView));
      final delegate =
          grid.gridDelegate as SliverGridDelegateWithFixedCrossAxisCount;
      expect(delegate.crossAxisCount, greaterThanOrEqualTo(2));
      expect(delegate.mainAxisExtent, greaterThan(300));
      final before = controller.offset;
      controller.jumpTo(200);
      await tester.pump();
      expect(controller.offset, 200);
      controller.jumpTo(before);
    }
  });
  for (final sliver in [false, true]) {
    testWidgets(
      'grid sliver=$sliver stays lazy at 5000 entries and local widths',
      (tester) async {
        var builds = 0;
        for (final width in <double>[
          0,
          375,
          599,
          600,
          601,
          839,
          840,
          841,
          1200,
          1440,
        ]) {
          builds = 0;
          final grid = sliver
              ? HikariAdaptiveGrid.sliver(
                  itemCount: 5000,
                  itemKeyBuilder: (i) => ValueKey(i),
                  itemExtentBuilder: (_, width) => width * 1.5 + 100,
                  itemBuilder: (_, i) {
                    builds++;
                    return Text('item $i');
                  },
                )
              : HikariAdaptiveGrid.builder(
                  itemCount: 5000,
                  itemKeyBuilder: (i) => ValueKey(i),
                  itemExtentBuilder: (_, width) => width * 1.5 + 100,
                  itemBuilder: (_, i) {
                    builds++;
                    return Text('item $i');
                  },
                );
          await tester.pumpWidget(
            MaterialApp(
              home: Scaffold(
                body: Align(
                  alignment: Alignment.topLeft,
                  child: SizedBox(
                    width: width,
                    height: 400,
                    child: sliver ? CustomScrollView(slivers: [grid]) : grid,
                  ),
                ),
              ),
            ),
          );
          expect(builds, lessThan(100));
          if (width > 0) expect(builds, greaterThan(0));
          expect(tester.takeException(), isNull);
        }
      },
    );
  }
  testWidgets('stable keys remap live state on reorder and filtering', (
    tester,
  ) async {
    var items = ['a', 'b', 'c'];
    final states = <String, State>{};
    Widget view() => MaterialApp(
      home: Scaffold(
        body: HikariAdaptiveGrid.builder(
          itemCount: items.length,
          itemKeyBuilder: (i) => ValueKey(items[i]),
          itemExtentBuilder: (_, _) => 200,
          itemBuilder: (_, i) => _Probe(id: items[i], states: states),
        ),
      ),
    );
    await tester.pumpWidget(view());
    expect(states['b'], isNotNull);
    final original = states['b'];
    items = ['b', 'c'];
    await tester.pumpWidget(view());
    expect(states['b'], same(original));
    expect(find.text('a'), findsNothing);
    expect(tester.takeException(), isNull);
  });
}

class _Probe extends StatefulWidget {
  const _Probe({required this.id, required this.states});
  final String id;
  final Map<String, State> states;
  @override
  State<_Probe> createState() => _ProbeState();
}

class _ProbeState extends State<_Probe> {
  @override
  Widget build(BuildContext context) {
    widget.states[widget.id] = this;
    return Text(widget.id);
  }
}
