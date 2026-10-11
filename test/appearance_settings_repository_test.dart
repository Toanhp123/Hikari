import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/settings/appearance_settings.dart';
import 'package:hikari/infrastructure/persistence/file_appearance_settings_repository.dart';

void main() {
  late Directory temp;
  late FileAppearanceSettingsRepository store;

  setUp(() async {
    temp = await Directory.systemTemp.createTemp('hikari-appearance-');
    store = FileAppearanceSettingsRepository(directory: temp);
  });
  tearDown(() async {
    await temp.delete(recursive: true);
  });

  test('defaults and round trips OLED and nullable accent', () async {
    final defaults = await store.load();
    expect(defaults.isOled, false);
    expect(defaults.accentArgb, isNull);

    await store.save(
      const AppearanceSettings(isOled: true, accentArgb: 0xFF06B6D4),
    );
    var restored = await store.load();
    expect(restored.isOled, true);
    expect(restored.accentArgb, 0xFF06B6D4);

    await store.save(const AppearanceSettings());
    restored = await store.load();
    expect(restored.isOled, false);
    expect(restored.accentArgb, isNull);
  });

  test(
    'rejects a malformed preferences document rather than guessing',
    () async {
      await File('${temp.path}/appearance_settings.json')
          .writeAsString('{invalid json');
      expect(store.load(), throwsFormatException);
    },
  );
}
