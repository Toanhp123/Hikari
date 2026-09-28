import 'dart:io';

import 'package:archive/archive_io.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/infrastructure/local_media/bounded_archive.dart';
import 'package:hikari/infrastructure/local_media/comic_info.dart';

void main() {
  test('rejects unsafe archive paths and duplicate names', () {
    final archive = Archive();
    archive.addFile(ArchiveFile.string('../page.jpg', 'x'));
    final file = File(
      '${Directory.systemTemp.path}/unsafe-${DateTime.now().microsecondsSinceEpoch}.zip',
    );
    final encoder = ZipFileEncoder()..create(file.path);
    for (final entry in archive) {
      encoder.addArchiveFile(entry);
    }
    encoder.closeSync();
    expect(
      () => BoundedArchive.open(file.path),
      throwsA(isA<FormatException>()),
    );
    file.deleteSync();
  });

  test('reads legal entries lazily with CRC validation', () {
    final file = File(
      '${Directory.systemTemp.path}/legal-${DateTime.now().microsecondsSinceEpoch}.zip',
    );
    final encoder = ZipFileEncoder()..create(file.path);
    final source = File('${file.path}.source')..writeAsBytesSync([1, 2, 3]);
    final secondSource = File('${file.path}.second')..writeAsBytesSync([4, 5]);
    encoder.addFileSync(source, '10.jpg');
    encoder.addFileSync(secondSource, '11.jpg');
    encoder.close();
    final zip = BoundedArchive.open(file.path);
    expect(zip.names, ['10.jpg', '11.jpg']);
    expect(zip.readEntry('10.jpg'), [1, 2, 3]);
    expect(zip.readEntry('11.jpg'), [4, 5]);
    expect(zip.readEntry('10.jpg'), [1, 2, 3]);
    zip.close();
    source.deleteSync();
    secondSource.deleteSync();
    file.deleteSync();
  });

  test('parses ComicInfo metadata and page order', () {
    final info = ComicInfo.parse(
      '''<ComicInfo><Title>Book</Title><Series>Series</Series><Number>2</Number><Writer>A, B</Writer><Genre>Action, Drama</Genre><Manga>YesAndRightToLeft</Manga><CommunityRating>4.5</CommunityRating><Pages><Page Image="1" Type="FrontCover"/><Page Image="0" Type="Story"/></Pages></ComicInfo>''',
    );
    expect(info.title, 'Book');
    expect(info.series, 'Series');
    expect(info.number, 2);
    expect(info.authors, ['A', 'B']);
    expect(info.rightToLeft, isTrue);
    expect(info.cover, 1);
    expect(info.pageOrder.map((page) => page.image), [1, 0]);
  });
}
