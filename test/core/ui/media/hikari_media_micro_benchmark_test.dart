import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/hikari_ui.dart';

import '../../../fixtures/counting_artwork_provider.dart';

void main() {
  testWidgets('replayable 5000-card host layout and decode micro-benchmark', (
    tester,
  ) async {
    var builds = 0;
    final controller = ScrollController();
    addTearDown(controller.dispose);
    final provider = CountingArtworkProvider(
      Future.value(
        base64Decode(
          'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGOQNJvzHwADQgHrKgED3AAAAABJRU5ErkJggg==',
        ),
      ),
    );
    final watch = Stopwatch()..start();
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: HikariAdaptiveGrid.builder(
            controller: controller,
            itemCount: 5000,
            itemKeyBuilder: (i) => ValueKey(i),
            itemExtentBuilder: (context, width) =>
                hikariCardExtent(context, width: width),
            itemBuilder: (_, i) {
              builds++;
              return HikariPosterCard(
                title: 'Fixture $i',
                artwork: HikariArtwork(image: provider),
              );
            },
          ),
        ),
      ),
    );
    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 30));
    });
    await tester.pumpAndSettle();
    final initialBuilds = builds;
    final initialMicros = watch.elapsedMicroseconds;
    controller.jumpTo(1200);
    await tester.pumpAndSettle();
    controller.jumpTo(0);
    await tester.pumpAndSettle();
    watch.stop();
    expect(initialBuilds, inExclusiveRange(0, 100));
    expect(builds, lessThan(150));
    expect(provider.loads, 1);
    expect(provider.decodes, 1);
    expect(tester.takeException(), isNull);
    // Host diagnostics only: never a physical frame/memory gate.
    debugPrint(
      'MEDIA_BENCH ${jsonEncode({'items': 5000, 'viewport': '800x600', 'textScale': 1, 'initialBuilds': initialBuilds, 'totalBuilds': builds, 'providerLoads': provider.loads, 'codecRequests': provider.decodes, 'initialHostMicros': initialMicros, 'totalHostMicros': watch.elapsedMicroseconds})}',
    );
  });
}
