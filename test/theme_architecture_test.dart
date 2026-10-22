import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';
import 'package:hikari/core/ui/foundation/hikari_motion.dart';
import 'package:hikari/core/ui/foundation/hikari_size.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';

void main() {
  test('geometry tokens have one declaration in canonical foundation', () {
    for (final name in ['spacing', 'radius', 'motion', 'size', 'breakpoints']) {
      final canonical = 'lib/core/ui/foundation/hikari_$name.dart';
      expect(File(canonical).existsSync(), isTrue, reason: canonical);
      final source = File(canonical).readAsStringSync();
      expect(source, isNot(contains('package:hikari/app/')));
      expect(source, isNot(contains('package:hikari/features/')));
      final legacy = File('lib/app/theme/hikari_$name.dart');
      if (legacy.existsSync()) {
        expect(legacy.readAsStringSync(), isNot(contains('class Hikari')));
      }
    }
  });

  test(
    'window classification preserves boundaries and rejects invalid widths',
    () {
      for (final (width, expected) in <(double, HikariWidthClass)>[
        (0, HikariWidthClass.compact),
        (375, HikariWidthClass.compact),
        (599, HikariWidthClass.compact),
        (600, HikariWidthClass.medium),
        (601, HikariWidthClass.medium),
        (839, HikariWidthClass.medium),
        (840, HikariWidthClass.expanded),
        (841, HikariWidthClass.expanded),
        (1199, HikariWidthClass.expanded),
        (1200, HikariWidthClass.wide),
        (1440, HikariWidthClass.wide),
      ]) {
        expect(HikariBreakpoints.classify(width), expected);
      }
      for (final width in [
        double.nan,
        double.infinity,
        double.negativeInfinity,
        -1.0,
      ]) {
        expect(() => HikariBreakpoints.classify(width), throwsArgumentError);
      }
      expect(HikariSpacing.xxxl, 48);
      expect(HikariSize.touchTarget, 48);
      expect(HikariSize.fieldMinHeight, 48);
      expect(HikariSize.icon, 24);
      expect(HikariMotion.interaction, const Duration(milliseconds: 150));
      expect(HikariMotion.exit, const Duration(milliseconds: 200));
      expect(HikariMotion.emphasized, const Duration(milliseconds: 400));
    },
  );

  test('application UI does not reintroduce parallel palettes or themes', () {
    final uiFiles = Directory('lib')
        .listSync(recursive: true)
        .whereType<File>()
        .where((file) => file.path.endsWith('.dart'));

    for (final file in uiFiles) {
      final path = file.path.replaceAll('\\', '/');
      final source = file.readAsStringSync();
      expect(source, isNot(contains('HikariColors')), reason: path);
      expect(source, isNot(contains('context.hikariColors')), reason: path);

      if (path != 'lib/app/theme/hikari_theme.dart') {
        expect(source, isNot(matches(RegExp(r'\bThemeData\('))), reason: path);
        expect(
          source,
          isNot(contains('HikariStatusColors.dark')),
          reason: path,
        );
      }
      if (!path.startsWith('lib/app/theme/') &&
          path != 'lib/features/novel_reader/novel_reader_theme.dart') {
        if (path != 'lib/core/ui/foundation/hikari_color_roles.dart') {
          expect(source, isNot(contains('Color(0x')), reason: path);
        }
        if (path != 'lib/core/ui/foundation/hikari_typography.dart') {
          expect(
            source,
            isNot(matches(RegExp(r'fontSize:\s*\d'))),
            reason: path,
          );
        }
      }
    }
  });
}
