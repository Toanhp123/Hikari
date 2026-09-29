import 'dart:io';

import 'package:archive/archive_io.dart';
import 'package:drift/native.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/infrastructure/local_media/classifier.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';

Media _media(String name) => classifyLocalEntries([
  LocalEntry(
    id: 'content://books/document/1',
    parentId: null,
    name: name,
    isDirectory: false,
  ),
]).single;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('hikari/local_media');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late Directory directory;
  late File fixture;
  late File databaseFile;
  late UserDatabase database;
  late LocalMediaSource source;
  var copies = 0;
  var deletions = 0;

  setUp(() async {
    directory = await Directory.systemTemp.createTemp(
      'hikari-archive-restart-',
    );
    fixture = File('${directory.path}/fixture.zip');
    databaseFile = File('${directory.path}/state.sqlite');
    database = UserDatabase(NativeDatabase(databaseFile));
    source = LocalMediaSource();
    copies = 0;
    deletions = 0;
    messenger.setMockMethodCallHandler(channel, (call) async {
      final arguments = call.arguments as Map<Object?, Object?>;
      switch (call.method) {
        case 'materialize':
          expect(arguments['id'], 'content://books/document/1');
          final copy = await fixture.copy('${directory.path}/copy-${++copies}');
          return {'path': copy.path};
        case 'deleteMaterialized':
          await File(arguments['path']! as String).delete();
          deletions++;
          return true;
        default:
          fail('Unexpected platform call ${call.method}');
      }
    });
  });
  tearDown(() async {
    await source.close();
    await database.close();
    messenger.setMockMethodCallHandler(channel, null);
    await directory.delete(recursive: true);
  });

  Future<void> restart() async {
    await source.close();
    await database.close();
    expect(deletions, copies);
    source = LocalMediaSource();
    database = UserDatabase(NativeDatabase(databaseFile));
  }

  test(
    'CBZ renamed display name retains Library and page progress after restart',
    () async {
      final archive = Archive()
        ..addFile(ArchiveFile.bytes('1.png', Uint8List.fromList([1, 2])))
        ..addFile(ArchiveFile.bytes('2.png', Uint8List.fromList([3, 4])));
      await fixture.writeAsBytes(ZipEncoder().encode(archive));
      final original = _media('Original.cbz');
      await source.retainArchive(original.source);
      final pages = await source.pages(original.source);
      expect(await source.readPage(pages[1]), [3, 4]);
      await SqliteLibraryRepository(database)
          .upsert(LibraryEntry(media: original, addedAt: DateTime.utc(2026)));
      await SqliteProgressRepository(database).save(
        MediaProgress(
          media: original.source,
          position: PagePosition(pageIndex: 1, pageCount: 2),
          completed: false,
          updatedAt: DateTime.utc(2026),
        ),
      );
      await restart();
      final renamed = _media('Renamed.cbz');
      expect(renamed.title, 'Renamed');
      expect(renamed.source, original.source);
      final library = SqliteLibraryRepository(database);
      expect(await library.contains(renamed.source), isTrue);
      final saved = (await library.loadAll()).single.media;
      expect(saved.title, 'Original');
      final progress = (await SqliteProgressRepository(database)
          .load(renamed.source))!;
      final position = progress.position as PagePosition;
      expect(position.pageIndex, 1);
      expect(position.pageCount, 2);
      expect(progress.completed, isFalse);
      await source.retainArchive(saved.source);
      final reopenedPages = await source.pages(saved.source);
      expect(reopenedPages, pages);
      expect(await source.readPage(reopenedPages[position.pageIndex]), [3, 4]);
      expect(copies, 2);
      await source.releaseArchive(saved.source);
      expect(deletions, 2);
    },
  );

  test('EPUB nonfirst document position and image resolve from saved Library after restart', () async {
    final image = Uint8List.fromList([137, 80, 78, 71, 13, 10]);
    final archive = Archive()
      ..addFile(ArchiveFile.string('mimetype', 'application/epub+zip'))
      ..addFile(
        ArchiveFile.string(
          'META-INF/container.xml',
          '''
<container><rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>''',
        ),
      )
      ..addFile(
        ArchiveFile.string(
          'OPS/package.opf',
          '''
<package><metadata><title>Restart Book</title></metadata><manifest>
<item id="one" href="one.xhtml" media-type="application/xhtml+xml"/>
<item id="two" href="two.xhtml" media-type="application/xhtml+xml"/>
<item id="image" href="images/p.png" media-type="image/png"/>
</manifest><spine><itemref idref="one"/><itemref idref="two"/></spine></package>''',
        ),
      )
      ..addFile(
        ArchiveFile.string(
          'OPS/one.xhtml',
          '<html><body>First section</body></html>',
        ),
      )
      ..addFile(
        ArchiveFile.string(
          'OPS/two.xhtml',
          '<html><body><p>Second section</p><img src="images/p.png"/></body></html>',
        ),
      )
      ..addFile(ArchiveFile.bytes('OPS/images/p.png', image));
    await fixture.writeAsBytes(ZipEncoder().encode(archive));
    final media = _media('Book.epub');
    await source.retainArchive(media.source);
    final publication = await source.publication(media.source);
    final resource = publication.spine[1].resource;
    final content = await source.readSection(media.source, resource);
    final imageRef = content.resources.values.single;
    expect(await source.readResource(imageRef), image);
    await SqliteLibraryRepository(database)
        .upsert(LibraryEntry(media: media, addedAt: DateTime.utc(2026)));
    await SqliteProgressRepository(database).save(
      MediaProgress(
        media: media.source,
        position: DocumentPosition(
          resource: resource,
          progression: .4,
          totalProgression: .7,
          locator: 'opaque-reader-locator',
        ),
        completed: false,
        updatedAt: DateTime.utc(2026),
      ),
    );
    await restart();
    final saved = (await SqliteLibraryRepository(
      database,
    ).loadAll()).single.media;
    expect(saved.source, media.source);
    final progress = (await SqliteProgressRepository(database)
        .load(saved.source))!;
    final position = progress.position as DocumentPosition;
    expect(position.resource, 'OPS/two.xhtml');
    expect(position.progression, .4);
    expect(position.totalProgression, .7);
    expect(position.locator, 'opaque-reader-locator');
    expect(progress.completed, isFalse);
    await source.retainArchive(saved.source);
    final reopened = await source.publication(saved.source);
    expect(reopened.spine[1].resource, position.resource);
    final restored = await source.readSection(saved.source, position.resource);
    expect(restored.html, contains('Second section'));
    expect(restored.resources.values.single, imageRef);
    expect(await source.readResource(restored.resources.values.single), image);
    expect(copies, 2);
    await source.releaseArchive(saved.source);
    expect(deletions, 2);
  });
}
