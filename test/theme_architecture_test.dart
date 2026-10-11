import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
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
        expect(source, isNot(contains('Color(0x')), reason: path);
        expect(source, isNot(matches(RegExp(r'fontSize:\s*\d'))), reason: path);
      }
    }
  });
}
