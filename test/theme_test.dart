import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/foundation/hikari_color_roles.dart';
import 'package:hikari/core/ui/foundation/hikari_motion.dart';
import 'package:hikari/core/ui/foundation/hikari_radius.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/foundation/hikari_typography.dart';

void main() {
  test(
    'status roles typography and context extension are foundation-owned',
    () {
      for (final name in ['color_roles', 'typography']) {
        expect(
          File('lib/core/ui/foundation/hikari_$name.dart').existsSync(),
          isTrue,
        );
      }
      expect(
        File('lib/app/theme/hikari_theme.dart').readAsStringSync(),
        isNot(contains('extension HikariThemeContext')),
      );
    },
  );

  test('status extension copy and interpolation preserve semantic pairs', () {
    const first = HikariStatusColors.dark;
    final second = first.copyWith(infoContainer: Colors.black);
    expect(second.warningContainer, first.warningContainer);
    expect(second.onInfoContainer, first.onInfoContainer);
    expect(first.lerp(second, 0).infoContainer, first.infoContainer);
    expect(first.lerp(second, 1).infoContainer, Colors.black);
    expect(
      first.lerp(second, .5).infoContainer,
      Color.lerp(first.infoContainer, Colors.black, .5),
    );
    expect(first.lerp(null, .5), same(first));
  });

  test('dark OLED and every raw accent preserve readable semantic pairs', () {
    double contrast(Color a, Color b) {
      final x = a.computeLuminance();
      final y = b.computeLuminance();
      return (x > y ? (x + .05) / (y + .05) : (y + .05) / (x + .05));
    }

    for (final oled in [false, true]) {
      for (final seed in [
        HikariAccentSeeds.defaultSeed,
        ...HikariAccentSeeds.presets.map((preset) => preset.$2),
      ]) {
        final theme = HikariTheme.darkTheme(oled: oled, accentColor: seed);
        final c = theme.colorScheme;
        final status = theme.extension<HikariStatusColors>()!;
        expect(
          c.primary,
          ColorScheme.fromSeed(
            seedColor: seed,
            brightness: Brightness.dark,
          ).primary,
        );
        for (final (foreground, background) in [
          (c.onSurface, c.surface),
          (c.onSurface, c.surfaceContainerHigh),
          (c.onSurfaceVariant, c.surfaceContainer),
          (c.onPrimary, c.primary),
          (c.onSecondaryContainer, c.secondaryContainer),
          (c.onError, c.error),
          (status.onWarningContainer, status.warningContainer),
          (status.onInfoContainer, status.infoContainer),
        ]) {
          expect(
            contrast(foreground, background),
            greaterThanOrEqualTo(4.5),
            reason: 'oled=$oled seed=$seed pair=$foreground/$background',
          );
        }
        expect(contrast(c.outline, c.surface), greaterThanOrEqualTo(3));
      }
    }
  });
  test('OLED preserves raised tonal surfaces and raw accent identity', () {
    const seed = Color(0xFF06B6D4);
    final dark = HikariTheme.darkTheme(accentColor: seed);
    final oled = HikariTheme.darkTheme(oled: true, accentColor: seed);
    expect(oled.colorScheme.surface, Colors.black);
    expect(oled.colorScheme.surfaceDim, Colors.black);
    expect(
      oled.colorScheme.surfaceContainer,
      dark.colorScheme.surfaceContainer,
    );
    expect(oled.colorScheme.surfaceContainer, isNot(Colors.black));
    expect(oled.colorScheme.primary, dark.colorScheme.primary);
    expect(oled.extension<HikariStatusColors>(), isNotNull);
  });
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
