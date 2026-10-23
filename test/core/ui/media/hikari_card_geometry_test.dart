import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/foundation/hikari_aspect_ratios.dart';
import 'package:hikari/core/ui/media/hikari_card_metrics.dart';
import 'package:hikari/core/ui/media/hikari_poster_card.dart';

void main() {
  test('named ratios describe artwork not whole card', () {
    expect(HikariAspectRatios.poster, 2 / 3);
    expect(HikariAspectRatios.landscape, 16 / 9);
    expect(HikariAspectRatios.square, 1);
  });
  for (final scale in <TextScaler>[
    TextScaler.noScaling,
    TextScaler.linear(2),
    TextScaler.linear(3),
    const _NonlinearScaler(),
  ]) {
    for (final direction in TextDirection.values) {
      testWidgets(
        'card geometry $scale $direction reserves title and subtitle',
        (tester) async {
          await tester.pumpWidget(
            MaterialApp(
              theme: HikariTheme.darkTheme(),
              home: MediaQuery(
                data: MediaQueryData(textScaler: scale),
                child: Directionality(
                  textDirection: direction,
                  child: Scaffold(
                    body: SingleChildScrollView(
                      child: Builder(
                        builder: (context) => SizedBox(
                          width: 140,
                          height: hikariCardExtent(
                            context,
                            width: 140,
                            hasSubtitle: true,
                          ),
                          child: const HikariPosterCard(
                            title: 'Hành trình dài qua những vùng đất xa lạ العربية',
                            subtitle: 'Nguồn truyện rất dài العربية',
                            artwork: SizedBox(),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          );
          expect(tester.takeException(), isNull);
          expect(
            tester.getSize(find.byType(AspectRatio)).aspectRatio,
            closeTo(2 / 3, .001),
          );
          final subtitle = tester.getRect(
            find.text('Nguồn truyện rất dài العربية'),
          );
          final card = tester.getRect(find.byType(HikariPosterCard));
          expect(subtitle.bottom, lessThanOrEqualTo(card.bottom + .01));
        },
      );
    }
  }
}

class _NonlinearScaler extends TextScaler {
  const _NonlinearScaler();
  @override
  double scale(double fontSize) => fontSize * (fontSize < 14 ? 3 : 2);
  @override
  double get textScaleFactor => 3;
}
