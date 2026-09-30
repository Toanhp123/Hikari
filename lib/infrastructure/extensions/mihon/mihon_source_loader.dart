import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/extensions/mihon/mihon_extension_gateway.dart';
import 'package:hikari/infrastructure/extensions/mihon/mihon_manga_source.dart';

final class MihonSourceLoader {
  const MihonSourceLoader({
    this.gateway = const MethodChannelMihonExtensionGateway(),
    required this.database,
  });

  final MihonExtensionGateway gateway;
  final UserDatabase database;

  Future<List<MediaSource>> loadSources() async {
    final descriptors = await gateway.listSources();
    return descriptors
        .map<MediaSource>(
          (descriptor) => MihonMangaSource(
            descriptor: descriptor,
            gateway: gateway,
            database: database,
          ),
        )
        .toList(growable: false);
  }
}
