import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/layout/hikari_adaptive_grid.dart';
import 'package:hikari/core/ui/media/hikari_artwork.dart';
import 'package:hikari/core/ui/media/hikari_card_metrics.dart';
import 'package:hikari/core/ui/media/hikari_poster_card.dart';

void main() {
  test(
    'Preview media API and reproducible measurement protocol documented',
    () {
      final file = File('docs/ui/components/media.md');
      expect(file.existsSync(), isTrue);
      final docs = file.readAsStringSync();
      for (final contract in [
        'Owner:',
        'Preview',
        'decode',
        '5000',
        'NOT MEASURED',
        'source-artwork-v1',
      ]) {
        expect(docs, contains(contract));
      }
      final barrel = File('lib/core/ui/hikari_ui.dart').readAsStringSync();
      for (final api in [
        'HikariArtwork',
        'HikariPosterCard',
        'HikariLandscapeCard',
        'HikariMediaProgress',
        'HikariAdaptiveGrid',
        'HikariHorizontalShelf',
        'HikariChapterRow',
        'HikariAspectRatios',
      ]) {
        expect(barrel, contains(api));
      }
      expect(barrel, isNot(contains('hikari_media_card_body.dart')));
    },
  );
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
    testWidgets('5000 missing covers stay lazy at $width with 3x RTL', (
      tester,
    ) async {
      await tester.binding.setSurfaceSize(Size(width, 400));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      var builds = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: MediaQuery(
            data: const MediaQueryData(textScaler: TextScaler.linear(3)),
            child: Directionality(
              textDirection: TextDirection.rtl,
              child: Scaffold(
                body: CustomScrollView(
                  slivers: [
                    HikariAdaptiveGrid.sliver(
                      itemCount: 5000,
                      itemKeyBuilder: (i) => ValueKey(i),
                      itemExtentBuilder: (context, width) => hikariCardExtent(
                        context,
                        width: width,
                        hasSubtitle: true,
                      ),
                      itemBuilder: (_, i) {
                        builds++;
                        return HikariPosterCard(
                          title: 'Hành trình qua vùng đất العربية $i',
                          subtitle: 'Nguồn rất dài العربية',
                          artwork: const HikariArtwork(),
                          progress: .5,
                          badge: const Badge(label: Text('MANGA')),
                        );
                      },
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
      expect(builds, inExclusiveRange(0, 100));
      expect(tester.takeException(), isNull);
      await tester.drag(find.byType(CustomScrollView), const Offset(0, -700));
      await tester.pumpAndSettle();
      expect(builds, lessThan(150));
      expect(tester.takeException(), isNull);
    });
  }
}
