import 'dart:convert';
import 'dart:io';

import 'package:hikari/domain/settings/appearance_settings.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';

/// Small private preferences file; no database migration required.
final class FileAppearanceSettingsRepository
    implements AppearanceSettingsRepository {
  FileAppearanceSettingsRepository({this._directory});

  final Directory? _directory;

  Future<File> _file() async {
    final directory = _directory ?? await getApplicationSupportDirectory();
    return File(path.join(directory.path, 'appearance_settings.json'));
  }

  @override
  Future<AppearanceSettings> load() async {
    final file = await _file();
    if (!await file.exists()) return const AppearanceSettings();
    final json = jsonDecode(await file.readAsString());
    if (json is! Map<String, dynamic>) {
      throw const FormatException('Invalid appearance settings');
    }
    final oled = json['isOled'];
    final accent = json['accentArgb'];
    if (oled is! bool || (accent != null && accent is! int)) {
      throw const FormatException('Invalid appearance setting types');
    }
    return AppearanceSettings(isOled: oled, accentArgb: accent as int?);
  }

  @override
  Future<void> save(AppearanceSettings settings) async {
    final file = await _file();
    await file.parent.create(recursive: true);
    await file.writeAsString(
      jsonEncode({
        'isOled': settings.isOled,
        'accentArgb': settings.accentArgb,
      }),
      flush: true,
    );
  }
}
