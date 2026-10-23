import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/media/hikari_poster_card.dart';
import 'package:hikari/core/ui/media/hikari_landscape_card.dart';
import 'package:hikari/core/ui/media/hikari_media_progress.dart';

void main() {
  testWidgets(
    'canonical poster preserves badge, subtitle and tap target at 2x text',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(375, 600));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      var taps = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: MediaQuery(
            data: const MediaQueryData(textScaler: TextScaler.linear(2)),
            child: Scaffold(
              body: Center(
                child: SizedBox(
                  width: 160,
                  child: HikariPosterCard(
                    title: 'A very long name for a small poster',
                    subtitle: 'Source subtitle',
                    badge: const Text('MANGA'),
                    artwork: const ColoredBox(color: Colors.blue),
                    onTap: () => taps++,
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      expect(find.text('MANGA'), findsOneWidget);
      expect(find.text('Source subtitle'), findsOneWidget);
      await tester.tap(find.byType(HikariPosterCard));
      await tester.pump();
      expect(taps, 1);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'source-unique caller Hero tags pair thumbnails and detail routes',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: Row(
                children: [
                  for (final source in ['source-a', 'source-b'])
                    SizedBox(
                      width: 140,
                      child: HikariPosterCard(
                        title: 'Same title',
                        heroTag: '$source:item',
                        artwork: const SizedBox(),
                        onTap: () => Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) => Scaffold(
                              body: SizedBox(
                                width: 240,
                                child: HikariPosterCard(
                                  title: 'Same title',
                                  heroTag: '$source:item',
                                  artwork: const SizedBox(),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      );
      expect(
        tester.widgetList<Hero>(find.byType(Hero)).map((h) => h.tag).toSet(),
        hasLength(2),
      );
      await tester.tap(find.byType(HikariPosterCard).first);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(tester.widget<Hero>(find.byType(Hero)).tag, 'source-a:item');
      final context = tester.element(find.byType(HikariPosterCard));
      Navigator.of(context).pop();
      await tester.pumpAndSettle();
      expect(find.byType(HikariPosterCard), findsNWidgets(2));
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'poster and landscape reserve artwork ratio independently of text',
    (tester) async {
      for (final landscape in [false, true]) {
        final card = landscape
            ? const HikariLandscapeCard(
                title: 'Full long media title',
                subtitle: 'Source',
                artwork: ColoredBox(color: Colors.red),
              )
            : const HikariPosterCard(
                title: 'Full long media title',
                subtitle: 'Source',
                artwork: ColoredBox(color: Colors.red),
              );
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: Align(
                alignment: Alignment.topLeft,
                child: SizedBox(width: 180, child: card),
              ),
            ),
          ),
        );
        final ratio = tester.widget<AspectRatio>(find.byType(AspectRatio));
        expect(ratio.aspectRatio, landscape ? 16 / 9 : 2 / 3);
        expect(
          tester.widget<Text>(find.text('Full long media title')).maxLines,
          2,
        );
        expect(
          tester.getTopLeft(find.text('Full long media title')).dy,
          greaterThanOrEqualTo(
            tester.getBottomLeft(find.byType(AspectRatio)).dy,
          ),
        );
        expect(tester.takeException(), isNull);
      }
    },
  );

  testWidgets(
    'poster exposes full title once and keyboard activates callback',
    (tester) async {
      var taps = 0;
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SizedBox(
              width: 180,
              child: HikariPosterCard(
                title: 'Tên truyện rất dài đầy đủ',
                artwork: const Text('Decorative cover'),
                onTap: () => taps++,
              ),
            ),
          ),
        ),
      );
      expect(
        find.bySemanticsLabel('Tên truyện rất dài đầy đủ'),
        findsOneWidget,
      );
      expect(find.bySemanticsLabel('Decorative cover'), findsNothing);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(taps, 1);
      semantics.dispose();
    },
  );

  testWidgets('progress clamps invalid values without nonfinite geometry', (
    tester,
  ) async {
    for (final value in [
      -1.0,
      0.5,
      2.0,
      double.nan,
      double.infinity,
      double.negativeInfinity,
    ]) {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SizedBox(
              width: 100,
              child: HikariMediaProgress(progress: value),
            ),
          ),
        ),
      );
      final bar = tester.widget<LinearProgressIndicator>(
        find.byType(LinearProgressIndicator),
      );
      expect(bar.value!.isFinite, isTrue);
      expect(bar.value, inInclusiveRange(0, 1));
      expect(tester.takeException(), isNull);
    }
  });
}
