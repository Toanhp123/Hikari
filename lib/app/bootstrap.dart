import 'package:flutter/widgets.dart';
import 'package:hikari/app/app.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/domain/settings/appearance_settings.dart';
import 'package:hikari/infrastructure/extensions/lnreader/lnreader_source_loader.dart';
import 'package:hikari/infrastructure/extensions/mihon/mihon_source_loader.dart';
import 'package:hikari/infrastructure/persistence/file_appearance_settings_repository.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:media_kit/media_kit.dart';

Future<void> bootstrap() async {
  WidgetsFlutterBinding.ensureInitialized();
  MediaKit.ensureInitialized();
  final database = UserDatabase();
  final extensionSources = <MediaSource>[];
  try {
    extensionSources.addAll(
      await MihonSourceLoader(database: database).loadSources(),
    );
  } catch (error, stackTrace) {
    debugPrint('Could not load Mihon extensions: $error');
    debugPrintStack(stackTrace: stackTrace);
  }
  try {
    extensionSources.addAll(await const LnReaderSourceLoader().loadSources());
  } catch (error, stackTrace) {
    debugPrint('Could not load novel extensions: $error');
    debugPrintStack(stackTrace: stackTrace);
  }
  try {
    final appearanceRepository = FileAppearanceSettingsRepository();
    AppearanceSettings appearance;
    try {
      appearance = await appearanceRepository.load();
    } catch (error, stackTrace) {
      debugPrint('Could not load appearance settings: $error');
      debugPrintStack(stackTrace: stackTrace);
      appearance = const AppearanceSettings();
    }
    runApp(
      HikariApp(
        initialAppearance: appearance,
        appearanceRepository: appearanceRepository,
        dependencies: AppDependencies.create(
          database: database,
          ownsDatabase: true,
          additionalSources: extensionSources,
        ),
      ),
    );
  } catch (_) {
    await database.close();
    rethrow;
  }
}
