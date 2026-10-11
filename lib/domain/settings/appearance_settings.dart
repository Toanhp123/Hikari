/// Appearance state independent of Flutter widget and persistence APIs.
final class AppearanceSettings {
  const AppearanceSettings({this.isOled = false, this.accentArgb});

  final bool isOled;

  /// Null selects Hikari's canonical default accent seed.
  final int? accentArgb;
}

abstract interface class AppearanceSettingsRepository {
  Future<AppearanceSettings> load();
  Future<void> save(AppearanceSettings settings);
}
