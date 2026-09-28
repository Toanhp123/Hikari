import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/mihon/mihon_extension_gateway.dart';
import 'package:hikari/infrastructure/mihon/mihon_manga_source.dart';

final class MihonExtensionRuntime {
  const MihonExtensionRuntime({
    this.gateway = const MethodChannelMihonExtensionGateway(),
  });

  final MihonExtensionGateway gateway;

  Future<List<MediaSource>> loadSources() async {
    final descriptors = await gateway.listSources();
    return descriptors
        .map<MediaSource>(
          (descriptor) =>
              MihonMangaSource(descriptor: descriptor, gateway: gateway),
        )
        .toList(growable: false);
  }
}
