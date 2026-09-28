import 'dart:io';
import 'dart:typed_data';
import 'package:archive/archive_io.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/infrastructure/reading/epub_publication.dart';

void main() {
  test('parses EPUB metadata spine nav and reads resources lazily', () async {
    final file = _epubFile(nav: true, stylesheet: true, unicode: true);
    final epub = EpubPublication.open(file.path);
    try {
      final publication = await epub.publication(
        const SourceMediaRef(sourceId: SourceId.local, itemId: 'book'),
      );
      expect(publication.metadata.title, 'Book');
      expect(publication.metadata.authors, ['Author']);
      expect(publication.spine.map((section) => section.resource), ['OPS/ch1.xhtml']);
      expect(publication.toc.single.label, 'Chapter 1');
      expect(publication.toc.single.fragment, 'start');

      final content = await epub.readSection(
        const SourceMediaRef(sourceId: SourceId.local, itemId: 'book'),
        'OPS/ch1.xhtml',
      );
      expect(content.html, contains('章一'));
      expect(content.html, contains('color: #123'));
      expect(content.html, contains('font-size: 18px'));
      expect(content.html, isNot(contains('url(')));
      expect(content.html, isNot(contains('@import')));
      expect(content.html, isNot(contains('<script')));
      expect(publication.resources.any((resource) => resource.mediaType == 'text/css'), isTrue);
      final stylesheet = publication.resources.firstWhere((resource) => resource.mediaType == 'text/css');
      final cssRef = const SourceMediaRef(sourceId: SourceId.local, itemId: 'OPS/styles/book.css');
      expect(
        await epub.readResource(cssRef),
        'h1 { color: #123; font-size: 18px; background: url(https://evil.test); } @import url(https://evil.test);'.codeUnits,
      );
      expect(stylesheet.resource, cssRef.itemId);
      expect(content.resources, hasLength(2));
      final resource = await epub.readResource(
        content.resources.values.firstWhere(
          (ref) => ref.itemId == 'OPS/images/p.png',
        ),
      );
      expect(resource, [137, 80, 78, 71]);
      expect(
        await epub.readResource(
          content.resources.values.firstWhere(
            (ref) => ref.itemId == 'OPS/images/p.png',
          ),
        ),
        resource,
      );
    } finally {
      epub.close();
      file.deleteSync();
    }
  });

  test('supports NCX fallback and valid parent-relative links', () async {
    final file = _epubFile(nav: false);
    final epub = EpubPublication.open(file.path);
    try {
      final publication = await epub.publication(
        const SourceMediaRef(sourceId: SourceId.local, itemId: 'book'),
      );
      expect(publication.toc.single.label, 'Chapter 1');
      expect(publication.toc.single.resource, 'OPS/ch1.xhtml');
    } finally {
      epub.close();
      file.deleteSync();
    }
  });

  test('rejects encrypted EPUBs and unsafe links', () {
    final encrypted = _epubFile(nav: true, encrypted: true);
    expect(
      () => EpubPublication.open(encrypted.path),
      throwsA(isA<FormatException>()),
    );
    encrypted.deleteSync();

    final unsafe = _epubFile(nav: true, unsafeLink: true);
    final epub = EpubPublication.open(unsafe.path);
    try {
      expect(
        () => epub.readSection(
          const SourceMediaRef(sourceId: SourceId.local, itemId: 'book'),
          'OPS/ch1.xhtml',
        ),
        throwsA(isA<FormatException>()),
      );
    } finally {
      epub.close();
      unsafe.deleteSync();
    }
  });
}

File _epubFile({
  required bool nav,
  bool encrypted = false,
  bool unsafeLink = false,
  bool stylesheet = false,
  bool unicode = false,
}) {
  final directory = Directory.systemTemp;
  final path = '${directory.path}/hikari-${DateTime.now().microsecondsSinceEpoch}.epub';
  final file = File(path);
  final archive = Archive();
  archive.addFile(ArchiveFile.string('mimetype', 'application/epub+zip'));
  archive.addFile(ArchiveFile.string('META-INF/container.xml', '''
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>
'''));
  if (encrypted) {
    archive.addFile(ArchiveFile.string('META-INF/encryption.xml', '<encryption/>'));
  }
  archive.addFile(ArchiveFile.string('OPS/package.opf', '''
<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Book</dc:title><dc:creator>Author</dc:creator><dc:language>en</dc:language>
  </metadata>
  <manifest>
    <item id="chapter" href="ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="image" href="images/p.png" media-type="image/png"/>
    ${stylesheet ? '<item id="style" href="styles/book.css" media-type="text/css"/>' : ''}
    ${nav ? '<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>' : '<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>'}
  </manifest>
  <spine toc="ncx"><itemref idref="chapter"/></spine>
</package>
'''));
  archive.addFile(ArchiveFile.string('OPS/ch1.xhtml', '''
<html><head><title>Chapter</title><link rel="stylesheet" href="styles/book.css"/></head><body><h1 id="start">${unicode ? '章一' : 'Chapter 1'}</h1>
<script>bad()</script><img src="images/p.png"/><a href="../outside.xhtml">bad</a></body></html>
'''.replaceFirst('<a href="../outside.xhtml">bad</a>', unsafeLink ? '<a href="../../outside.xhtml">bad</a>' : '<a href="#start">ok</a>')));
  if (stylesheet) {
    archive.addFile(ArchiveFile.string(
      'OPS/styles/book.css',
      'h1 { color: #123; font-size: 18px; background: url(https://evil.test); } @import url(https://evil.test);',
    ));
  }
  archive.addFile(ArchiveFile.string('OPS/nav.xhtml', '<html><body><nav><ol><li><a href="ch1.xhtml#start">Chapter 1</a></li></ol></nav></body></html>'));
  archive.addFile(ArchiveFile.string('OPS/toc.ncx', '<ncx><navMap><navPoint><navLabel><text>Chapter 1</text></navLabel><content src="ch1.xhtml#start"/></navPoint></navMap></ncx>'));
  archive.addFile(ArchiveFile.bytes('OPS/images/p.png', Uint8List.fromList([137, 80, 78, 71])));
  final encoder = ZipFileEncoder()..create(file.path);
  for (final entry in archive) {
    encoder.addArchiveFile(entry);
  }
  encoder.closeSync();
  return file;
}
