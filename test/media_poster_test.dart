import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';

void main() {
  testWidgets('MediaPoster renders title, subtitle, badge, and handles tap', (
    tester,
  ) async {
    var tapped = false;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: MediaPoster(
            title: 'Frieren: Beyond Journey\'s End',
            subtitle: 'Episode 28',
            badgeText: 'ANIME',
            badgeColor: const Color(0xFFEC4899),
            progress: 0.85,
            onTap: () => tapped = true,
          ),
        ),
      ),
    );

    expect(find.text('Frieren: Beyond Journey\'s End'), findsOneWidget);
    expect(find.text('Episode 28'), findsOneWidget);
    expect(find.text('ANIME'), findsOneWidget);

    await tester.tap(find.byType(MediaPoster));
    await tester.pumpAndSettle();
    expect(tapped, isTrue);

    // Title is positioned below the 2:3 artwork container, not overlaid on it
    final artworkBottom = tester.getBottomLeft(find.byType(AspectRatio)).dy;
    final titleTop = tester
        .getTopLeft(find.text('Frieren: Beyond Journey\'s End'))
        .dy;
    expect(titleTop, greaterThanOrEqualTo(artworkBottom));
  });

  testWidgets('custom poster badge uses readable opaque foreground', (
    tester,
  ) async {
    for (final fill in [
      const Color(0xFFFAB387),
      const Color(0xFF89DCEB),
      const Color(0xFF453026),
      const Color(0xFF1E3547),
    ]) {
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: Scaffold(
            body: SizedBox(
              width: 148,
              height: 280,
              child: MediaPoster(
                title: 'Poster',
                badgeText: 'MANGA',
                badgeColor: fill,
              ),
            ),
          ),
        ),
      );
      final badge = find
          .ancestor(of: find.text('MANGA'), matching: find.byType(DecoratedBox))
          .first;
      final background =
          (tester.widget<DecoratedBox>(badge).decoration as BoxDecoration)
              .color!;
      final foreground = tester.widget<Text>(find.text('MANGA')).style!.color!;
      expect(background, fill);
      final lighter =
          foreground.computeLuminance() > background.computeLuminance()
          ? foreground.computeLuminance()
          : background.computeLuminance();
      final darker =
          foreground.computeLuminance() < background.computeLuminance()
          ? foreground.computeLuminance()
          : background.computeLuminance();
      expect((lighter + 0.05) / (darker + 0.05), greaterThanOrEqualTo(4.5));
    }
  });

  testWidgets('poster title reserve follows nonlinear font metrics', (
    tester,
  ) async {
    final heights = <double>[];
    for (final scale in [1.0, 2.0]) {
      var measured = 0.0;
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: MediaQuery(
            data: MediaQueryData(textScaler: TextScaler.linear(scale)),
            child: Builder(
              builder: (context) {
                measured = mediaPosterTitleReserveHeight(context);
                return const Scaffold(body: Text('Measured'));
              },
            ),
          ),
        ),
      );
      heights.add(measured);
    }
    expect(heights, hasLength(2));
    expect(heights[1], greaterThan(heights[0]));
  });
}
