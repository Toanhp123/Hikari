import 'package:flutter/widgets.dart';
import 'package:hikari/app/app.dart';
import 'package:media_kit/media_kit.dart';

void bootstrap() {
  WidgetsFlutterBinding.ensureInitialized();
  MediaKit.ensureInitialized();
  runApp(const HikariApp());
}
