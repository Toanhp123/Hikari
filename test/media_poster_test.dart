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

  testWidgets('poster owns a transparent ink surface above opaque artwork', (
    tester,
  ) async {
    var taps = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 160,
              height: 300,
              child: MediaPoster(title: 'Tap target', onTap: () => taps++),
            ),
          ),
        ),
      ),
    );
    final poster = find.byType(MediaPoster);
    expect(
      find.descendant(of: poster, matching: find.byType(MergeSemantics)),
      findsOneWidget,
    );
    final materials = find.descendant(
      of: poster,
      matching: find.byType(Material),
    );
    expect(
      tester
          .widgetList<Material>(materials)
          .where((surface) => surface.type == MaterialType.transparency),
      hasLength(1),
    );
    final artworkRect = tester.getRect(
      find.descendant(of: poster, matching: find.byType(AspectRatio)).first,
    );
    await tester.tapAt(artworkRect.topLeft + const Offset(8, 8));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Tap target'));
    await tester.pumpAndSettle();
    expect(taps, 2);
  });

  testWidgets('poster image decode width follows physical pixel density', (
    tester,
  ) async {
    final results = <int?>[];
    for (final dpr in [1.0, 3.0]) {
      tester.view.devicePixelRatio = dpr;
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: const Scaffold(
            body: SizedBox(
              width: 160,
              height: 300,
              child: MediaPoster(
                title: 'Artwork',
                imageUrl: 'https://example.invalid/poster.png',
              ),
            ),
          ),
        ),
      );
      final posterImage = tester.widget<Image>(
        find
            .descendant(
              of: find.byType(MediaPoster),
              matching: find.byType(Image),
            )
            .first,
      );
      results.add((posterImage.image as ResizeImage).width);
    }
    addTearDown(tester.view.resetDevicePixelRatio);
    expect(results[0], isNotNull);
    expect(results[1], greaterThan(results[0]!));
  });

  testWidgets('compact grid poster accommodates scaled title and subtitle', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2)),
          child: Scaffold(
            body: GridView.builder(
              gridDelegate: mediaPosterGridDelegate,
              itemCount: 1,
              itemBuilder: (_, _) => const MediaPoster(
                title: 'A very long name for a small poster',
                subtitle: 'Source subtitle',
              ),
            ),
          ),
        ),
      ),
    );
    expect(tester.takeException(), isNull);
    expect(find.text('A very long name for a small poster'), findsOneWidget);
  });
}
