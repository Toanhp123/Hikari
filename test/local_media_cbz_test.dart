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
    encoder.closeSync();
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

  test('rejects encrypted central-directory entries before reading pages', () {
    final file = File(
      '${Directory.systemTemp.path}/encrypted-${DateTime.now().microsecondsSinceEpoch}.cbz',
    );
    final encoder = ZipFileEncoder()..create(file.path);
    encoder.addArchiveFile(ArchiveFile.string('1.jpg', 'page'));
    encoder.closeSync();
    final bytes = file.readAsBytesSync();
    for (var i = 0; i + 10 < bytes.length; i++) {
      if (bytes[i] == 0x50 &&
          bytes[i + 1] == 0x4b &&
          bytes[i + 2] == 0x01 &&
          bytes[i + 3] == 0x02) {
        bytes[i + 8] |= 1;
        break;
      }
    }
    file.writeAsBytesSync(bytes);
    expect(() => BoundedArchive.open(file.path), throwsFormatException);
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
