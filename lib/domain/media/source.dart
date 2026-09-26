import 'dart:typed_data';

import 'package:hikari/domain/media/media.dart';

abstract interface class MediaSource {
  SourceId get id;
  String get name;
}

abstract interface class MangaPageSource implements MediaSource {
  Future<List<SourceMediaRef>> pages(SourceMediaRef media);
  Future<Uint8List> readPage(SourceMediaRef page);
}

abstract interface class NovelTextSource implements MediaSource {
  Future<String> readText(SourceMediaRef media);
}
