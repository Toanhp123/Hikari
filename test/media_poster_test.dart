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
  });
}
