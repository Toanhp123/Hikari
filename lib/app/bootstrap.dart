import 'package:flutter/widgets.dart';
import 'package:hikari/app/app.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/mihon/mihon_extension_runtime.dart';
import 'package:media_kit/media_kit.dart';

Future<void> bootstrap() async {
  WidgetsFlutterBinding.ensureInitialized();
  MediaKit.ensureInitialized();

  var extensionSources = const <MediaSource>[];
  try {
    extensionSources = await const MihonExtensionRuntime().loadSources();
  } catch (error, stackTrace) {
    debugPrint('Could not load Mihon extensions: $error');
    debugPrintStack(stackTrace: stackTrace);
  }

  runApp(
    HikariApp(
      dependencies: AppDependencies.create(additionalSources: extensionSources),
    ),
  );
}
