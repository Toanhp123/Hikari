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
    expect(darkColors.background, dark.colorScheme.surface);
    expect(darkColors.primary, dark.colorScheme.primary);
    expect(darkColors.secondary, dark.colorScheme.secondary);

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
    expect(colors.primary, customTheme.colorScheme.primary);
    expect(colors.primary, isNot(const Color(0xFF8B5CF6)));
  });

  test('HikariColors lerps smoothly', () {
    final start = HikariTheme.darkTheme().extension<HikariColors>()!;
    final end = HikariTheme.darkTheme(oled: true).extension<HikariColors>()!;
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

    final theme = HikariTheme.darkTheme();
    expect(resolvedColors.primary, theme.colorScheme.primary);
    expect(resolvedColors.badgeVideo, theme.colorScheme.primary);
    final statuses = theme.extension<HikariStatusColors>()!;
    expect(resolvedColors.badgeManga, statuses.onWarningContainer);
    expect(resolvedColors.badgeNovel, statuses.onInfoContainer);
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
    expect(HikariRadius.lg, 16.0);
    expect(HikariRadius.capsule, 24.0);
    expect(HikariRadius.md, 14.0);
    expect(HikariRadius.xl, 24.0);
    expect(HikariTypography.labelSmall.fontSize, 12.0);
    expect(HikariMotion.standard, const Duration(milliseconds: 300));
  });
}
