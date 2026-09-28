import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/reading.dart';
import 'package:hikari/domain/media/source.dart';

void main() {
  test('manga metadata keeps normalized fields and opaque cover reference', () {
    const cover = SourceMediaRef(
      sourceId: SourceId('mihon:42'),
      itemId: 'mihon-art-v1:cover',
    );
    const metadata = MediaMetadata(
      title: 'Example',
      cover: cover,
      summary: 'Summary',
      authors: ['Author'],
      artists: ['Artist'],
      genres: ['Action'],
      status: PublicationStatus.ongoing,
      rawStatus: 'Publishing',
      rating: 8.5,
    );

    expect(metadata.title, 'Example');
    expect(metadata.cover, cover);
    expect(metadata.status, PublicationStatus.ongoing);
    expect(metadata.rawStatus, 'Publishing');
    expect(metadata.rating, 8.5);
  });

  test('search page validates page and exposes chapter metadata', () {
    const media = Media(
      title: 'Example',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId('mihon:42'), itemId: 'series'),
    );
    const preview = MangaPreview(media: media);
    const page = MangaSearchPage(results: [preview], hasNextPage: true, page: 1);
    const chapter = MangaChapter(
      title: 'Chapter 1',
      source: SourceMediaRef(
        sourceId: SourceId('mihon:42'),
        itemId: 'chapter',
      ),
      chapterNumber: 1,
      dateUpload: 123456789,
    );

    expect(page.page, 1);
    expect(page.hasNextPage, isTrue);
    expect(page.results.single.media, media);
    expect(chapter.chapterNumber, 1);
    expect(chapter.dateUpload, 123456789);
  });

  test('artwork source owns cover byte loading', () async {
    final source = _ArtworkSource();
    final bytes = await source.readArtwork(
      const SourceMediaRef(sourceId: SourceId('fake'), itemId: 'cover'),
    );
    expect(bytes, [1, 2, 3]);
  });
}

final class _ArtworkSource implements ArtworkSource {
  @override
  SourceId get id => const SourceId('fake');

  @override
  String get name => 'Fake';

  @override
  Future<Uint8List> readArtwork(SourceMediaRef artwork) async =>
      Uint8List.fromList([1, 2, 3]);
}
