import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/extensions/lnreader/lnreader_novel_source.dart';

class LnReaderSourceLoader {
  const LnReaderSourceLoader({
    this.channel = const MethodChannel('hikari/lnreader'),
  });
  final MethodChannel channel;
  Future<List<MediaSource>> loadSources() async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return [];
    final rows = await channel.invokeListMethod<Object?>('listSources');
    if (rows == null || rows.length > 1000) {
      throw const FormatException('Invalid Novel source snapshot');
    }
    final ids = <String>{};
    return rows
        .map((row) {
          if (row is! Map ||
              row.length != 3 ||
              !row.keys.toSet().containsAll({'id', 'name', 'site'})) {
            throw const FormatException('Invalid Novel source descriptor');
          }
          final id = row['id'], name = row['name'], site = row['site'];
          final uri = site is String ? Uri.tryParse(site) : null;
          if (id is! String ||
              !RegExp(r'^[A-Za-z0-9_-]{1,128}$').hasMatch(id) ||
              !ids.add(id) ||
              name is! String ||
              name.trim().isEmpty ||
              name.length > 256 ||
              name.contains('\u0000') ||
              site is! String ||
              site.length > 4096 ||
              uri == null ||
              uri.scheme != 'https' ||
              uri.host.isEmpty ||
              uri.userInfo.isNotEmpty ||
              uri.port != 443 ||
              uri.hasFragment) {
            throw const FormatException('Invalid Novel source identity');
          }
          return LnReaderNovelSource(id, name, channel, site: site);
        })
        .toList(growable: false);
  }
}
