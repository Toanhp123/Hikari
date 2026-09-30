import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

void main() {
  test('HikariTheme builds dark and OLED themes with extensions', () {
    final dark = HikariTheme.darkTheme();
    expect(dark.brightness, Brightness.dark);
    final darkColors = dark.extension<HikariColors>();
    expect(darkColors, isNotNull);
    expect(darkColors!.isOled, isFalse);
    expect(darkColors.background, const Color(0xFF0B0F17));
    expect(darkColors.primary, const Color(0xFF8B5CF6));
    expect(darkColors.secondary, const Color(0xFFEC4899));

    final oled = HikariTheme.darkTheme(oled: true);
    expect(oled.brightness, Brightness.dark);
    final oledColors = oled.extension<HikariColors>();
    expect(oledColors, isNotNull);
    expect(oledColors!.isOled, isTrue);
    expect(oledColors.background, const Color(0xFF000000));
  });

  test('HikariTheme respects custom accent color', () {
    final customTheme = HikariTheme.darkTheme(
      accentColor: const Color(0xFF06B6D4),
    );
    final colors = customTheme.extension<HikariColors>()!;
    expect(colors.primary, const Color(0xFF06B6D4));
  });

  test('HikariColors lerps smoothly', () {
    const start = HikariColors.dark();
    const end = HikariColors.oled();
    final midway = start.lerp(end, 0.5);
    expect(
      midway.background,
      Color.lerp(start.background, end.background, 0.5),
    );
    expect(midway.primary, start.primary);
  });

  testWidgets('HikariThemeContext extension provides easy context access', (
    tester,
  ) async {
    late HikariColors resolvedColors;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Builder(
          builder: (context) {
            resolvedColors = context.hikariColors;
            return const SizedBox();
          },
        ),
      ),
    );

    expect(resolvedColors.primary, const Color(0xFF8B5CF6));
    expect(resolvedColors.badgeVideo, const Color(0xFFEC4899));
    expect(resolvedColors.badgeManga, const Color(0xFF06B6D4));
    expect(resolvedColors.badgeNovel, const Color(0xFFF59E0B));
  });

  test('HikariSpacing and HikariRadius tokens are correct', () {
    expect(HikariSpacing.xs, 4.0);
    expect(HikariSpacing.sm, 8.0);
    expect(HikariSpacing.md, 12.0);
    expect(HikariSpacing.lg, 16.0);
    expect(HikariSpacing.xl, 24.0);
    expect(HikariSpacing.xxl, 32.0);

    expect(HikariRadius.xs, 4.0);
    expect(HikariRadius.sm, 8.0);
    expect(HikariRadius.md, 12.0);
    expect(HikariRadius.lg, 16.0);
    expect(HikariRadius.capsule, 24.0);
  });
}
