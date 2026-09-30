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
    final rows = await channel.invokeListMethod<Object?>('listSources') ?? [];
    return rows.map((row) {
      final map = Map<String, dynamic>.from(row! as Map);
      return LnReaderNovelSource(
        map['id'] as String,
        map['name'] as String,
        channel,
        site: map['site'] as String?,
      );
    }).toList();
  }
}
