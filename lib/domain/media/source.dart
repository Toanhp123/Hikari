import 'dart:typed_data';

import 'package:hikari/domain/media/media.dart';

abstract interface class MediaSource {
  SourceId get id;
  String get name;
}

abstract interface class MediaSourceAvailability implements MediaSource {
  bool get isAvailable;
}

abstract interface class MediaSourcePresentation implements MediaSource {
  String get displayName;
  String? get languageCode;
  String? get presentationGroupId;
}

String? normalizeSourceLanguageCode(String? value) {
  final normalized = value?.trim().toLowerCase().replaceAll('_', '-');
  return normalized == null || normalized.isEmpty ? null : normalized;
}

/// A source-owned resource kept alive while opened media is in use.
abstract interface class MediaOpenLease {
  Future<void> release();
}

/// Optional capability for sources that need an explicit opened-media lifetime.
abstract interface class MediaOpenLeaseSource implements MediaSource {
  MediaOpenLease? acquireOpenLease(SourceMediaRef media);
}

abstract interface class DirectVideoSource implements MediaSource {
  String playbackLocator(SourceMediaRef media);
}

abstract interface class ArtworkSource implements MediaSource {
  Future<Uint8List> readArtwork(SourceMediaRef artwork);
}
