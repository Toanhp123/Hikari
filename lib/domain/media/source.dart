import 'dart:typed_data';

import 'package:hikari/domain/media/media.dart';

abstract interface class MediaSource {
  SourceId get id;
  String get name;
}

abstract interface class MediaSourceAvailability implements MediaSource {
  bool get isAvailable;
}

abstract interface class DirectVideoSource implements MediaSource {
  String playbackLocator(SourceMediaRef media);
}

abstract interface class ArtworkSource implements MediaSource {
  Future<Uint8List> readArtwork(SourceMediaRef artwork);
}
