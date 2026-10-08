import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

void main() {
  test('HikariTheme uses a single Material 3 scheme and status extension', () {
    final theme = HikariTheme.darkTheme();
    expect(theme.brightness, Brightness.dark);
    expect(theme.extension<HikariStatusColors>(), isNotNull);
    expect(theme.scaffoldBackgroundColor, theme.colorScheme.surface);
    expect(theme.colorScheme.primary, isNotNull);

    final oled = HikariTheme.darkTheme(oled: true);
    expect(oled.colorScheme.surface, Colors.black);
    expect(oled.colorScheme.surfaceContainerLowest, Colors.black);
  });

  test('custom accent changes scheme without changing surface tokens', () {
    final custom = HikariTheme.darkTheme(accentColor: const Color(0xFF06B6D4));
    final standard = HikariTheme.darkTheme();
    expect(custom.colorScheme.primary, isNot(standard.colorScheme.primary));
    expect(custom.colorScheme.surface, standard.colorScheme.surface);
    expect(custom.extension<HikariStatusColors>(), isNotNull);
  });

  testWidgets('custom semantic status roles resolve from the canonical theme', (
    tester,
  ) async {
    late HikariStatusColors resolved;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Builder(
          builder: (context) {
            resolved = context.hikariStatusColors;
            return const SizedBox.shrink();
          },
        ),
      ),
    );
    expect(resolved.onInfoContainer, HikariStatusColors.dark.onInfoContainer);
  });

  testWidgets('custom semantic status roles require the canonical root theme', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            context.hikariStatusColors;
            return const SizedBox.shrink();
          },
        ),
      ),
    );
    expect(tester.takeException(), isA<StateError>());
  });

  test('Hikari geometry and typography tokens remain centralized', () {
    expect(HikariSpacing.xs, 4.0);
    expect(HikariSpacing.sm, 8.0);
    expect(HikariSpacing.md, 12.0);
    expect(HikariSpacing.lg, 16.0);
    expect(HikariSpacing.xl, 24.0);
    expect(HikariSpacing.xxl, 32.0);

    expect(HikariRadius.xs, 4.0);
    expect(HikariRadius.sm, 8.0);
    expect(HikariRadius.lg, 16.0);
    expect(HikariRadius.pill, isA<StadiumBorder>());
    expect(HikariRadius.md, 14.0);
    expect(HikariRadius.xl, 24.0);
    expect(HikariTypography.textTheme.labelSmall!.fontSize, 12.0);
    expect(HikariMotion.standard, const Duration(milliseconds: 300));
  });
}
