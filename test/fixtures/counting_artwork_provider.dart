import 'dart:async';
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/painting.dart';

/// Counts actual provider loads and codec requests, not widget rebuilds.
class CountingArtworkProvider extends ImageProvider<CountingArtworkProvider> {
  CountingArtworkProvider(this.bytes);
  final Future<Uint8List> bytes;
  int loads = 0;
  int decodes = 0;

  @override
  Future<CountingArtworkProvider> obtainKey(ImageConfiguration configuration) =>
      SynchronousFuture(this);

  @override
  ImageStreamCompleter loadImage(
    CountingArtworkProvider key,
    ImageDecoderCallback decode,
  ) {
    loads++;
    return MultiFrameImageStreamCompleter(
      codec: bytes.then((bytes) async {
        final buffer = await ui.ImmutableBuffer.fromUint8List(bytes);
        decodes++;
        return decode(buffer);
      }),
      scale: 1,
    );
  }
}
