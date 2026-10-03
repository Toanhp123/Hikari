import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/sources/read_source_artwork.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';

void main() {
  test('routes artwork to the registered source capability', () async {
    final source = _ArtworkSource();
    final reader = ReadSourceArtwork(SourceRegistry([source]));
    const artwork = SourceMediaRef(
      sourceId: SourceId('test:artwork'),
      itemId: 'cover',
    );

    final bytes = await reader.execute(artwork);

    expect(bytes, [1, 2, 3]);
    expect(source.reads, 1);
  });

  test(
    'returns null when the registered source has no artwork capability',
    () async {
      final reader = ReadSourceArtwork(SourceRegistry([_PlainSource()]));

      final bytes = await reader.execute(
        const SourceMediaRef(sourceId: SourceId('test:plain'), itemId: 'cover'),
      );

      expect(bytes, isNull);
    },
  );
}

final class _ArtworkSource implements ArtworkSource {
  int reads = 0;

  @override
  SourceId get id => const SourceId('test:artwork');

  @override
  String get name => 'Artwork source';

  @override
  Future<Uint8List> readArtwork(SourceMediaRef artwork) async {
    reads++;
    return Uint8List.fromList([1, 2, 3]);
  }
}

final class _PlainSource implements MediaSource {
  @override
  SourceId get id => const SourceId('test:plain');

  @override
  String get name => 'Plain source';
}
