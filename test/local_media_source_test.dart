import 'dart:io';

import 'package:archive/archive_io.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/local_media_scan_result.dart';

import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/infrastructure/local_media/bounded_archive.dart';
import 'package:hikari/domain/media/publication.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';
import 'package:hikari/infrastructure/local_media/local_artwork_ref.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('hikari/local_media');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final source = LocalMediaSource();
  const ref = SourceMediaRef(sourceId: SourceId.local, itemId: 'opaque');
  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  for (final failingMethod in ['selectedTree', 'children']) {
    test('scan snapshot normalizes access loss from $failingMethod', () async {
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == failingMethod) {
          throw PlatformException(code: 'access');
        }
        if (call.method == 'selectedTree') {
          return {'id': 'content://root', 'name': 'Books'};
        }
        fail('Unexpected ${call.method}');
      });
      await expectLater(
        source.scanSelectedRootSnapshot(),
        throwsA(isA<LocalMediaAccessException>()),
      );
    });
  }

  test('scan snapshot preserves non-access platform errors', () async {
    messenger.setMockMethodCallHandler(channel, (_) async {
      throw PlatformException(code: 'storage');
    });
    await expectLater(
      source.scanSelectedRootSnapshot(),
      throwsA(
        isA<PlatformException>().having((e) => e.code, 'code', 'storage'),
      ),
    );
  });

  test('CBZ reader shares one copy then releases and reopens', () async {
    final directory = Directory.systemTemp.createTempSync('hikari-cbz-');
    final fixture = File('${directory.path}/book.cbz');
    final encoder = ZipFileEncoder()..create(fixture.path);
    encoder.addArchiveFile(
      ArchiveFile.bytes('1.png', Uint8List.fromList([1, 2])),
    );
    encoder.addArchiveFile(
      ArchiveFile.bytes('2.png', Uint8List.fromList([3, 4])),
    );
    encoder.closeSync();
    var copies = 0;
    var deletions = 0;
    final local = LocalMediaSource();
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'materialize') {
        final copy = fixture.copySync('${directory.path}/copy-${++copies}');
        return {'path': copy.path};
      }
      if (call.method == 'deleteMaterialized') {
        final args = call.arguments as Map<Object?, Object?>;
        File(args['path']! as String).deleteSync();
        deletions++;
        return true;
      }
      fail('Unexpected ${call.method}');
    });
    final ref = SourceMediaRef(
      sourceId: SourceId.local,
      itemId: const LocalArchiveRef(locator: 'content://book').encode(),
    );
    final lease = local.acquireOpenLease(ref);
    expect(lease, isNotNull);
    final pages = await local.pages(ref);
    expect(await Future.wait(pages.map(local.readPage)), [
      [1, 2],
      [3, 4],
    ]);
    expect(copies, 1);
    expect(deletions, 0);
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'deleteMaterialized');
      return false;
    });
    await expectLater(lease!.release(), throwsStateError);
    messenger.setMockMethodCallHandler(channel, (call) async {
      if (call.method == 'materialize') {
        return {
          'path': fixture.copySync('${directory.path}/copy-${++copies}').path,
        };
      }
      final args = call.arguments as Map<Object?, Object?>;
      File(args['path']! as String).deleteSync();
      deletions++;
      return true;
    });
    expect(deletions, 0);
    final reopenedLease = local.acquireOpenLease(ref);
    expect(reopenedLease, isNotNull);
    expect(await local.readPage(pages.last), [3, 4]);
    expect(copies, 2);
    await local.close();
    expect(deletions, 2);
    directory.deleteSync(recursive: true);
  });

  test('archive identities exclude names, distinguish format and reject malformed refs', () {
    const cbz = LocalArchiveRef(locator: 'content://book');
    const epub = LocalArchiveRef(locator: 'content://book', format: 'epub');
    expect(cbz.encode(), isNot(epub.encode()));
    expect(LocalArchiveRef.tryDecode(cbz.encode())?.locator, 'content://book');
    expect(LocalArchiveRef.tryDecode('hikari-cbz:bad'), isNull);
    expect(
      () => const LocalArchiveRef(locator: 'file:///private').encode(),
      throwsFormatException,
    );
    expect(
      () => const LocalArchiveRef(
        locator: 'content://book',
        entry: '../private',
      ).encode(),
      throwsFormatException,
    );
  });

  test(
    'wrong source and malformed archive cannot reach platform reads',
    () async {
      messenger.setMockMethodCallHandler(
        channel,
        (_) async => fail('Unexpected platform call'),
      );
      await expectLater(
        source.pages(
          const SourceMediaRef(sourceId: SourceId('other'), itemId: 'folder'),
        ),
        throwsArgumentError,
      );
      await expectLater(
        source.readPage(
          const SourceMediaRef(
            sourceId: SourceId.local,
            itemId: 'hikari-cbz:bad',
          ),
        ),
        throwsFormatException,
      );
    },
  );

  test('local source exposes only implemented capabilities', () {
    expect(source, isA<MediaSource>());
    expect(source, isA<MangaPageSource>());
    expect(source, isA<NovelTextSource>());
    expect(source, isA<PublicationSource>());
    expect(source, isA<MediaOpenLeaseSource>());
    expect(source.id, SourceId.local);
    expect(source.name, isNotEmpty);
  });

  test('open lease is only created for archive-backed local media', () {
    expect(source.acquireOpenLease(ref), isNull);
  });

  test('publication capability owns only EPUB archive refs', () {
    final epub = LocalArchiveRef(
      locator: 'content://book',
      format: 'epub',
    ).encode();
    final epubResource = LocalArchiveRef(
      locator: 'content://book',
      format: 'epub',
      entry: 'OPS/chapter.xhtml',
    ).encode();
    expect(
      source.canOpenPublication(
        SourceMediaRef(sourceId: SourceId.local, itemId: epub),
      ),
      isTrue,
    );
    expect(
      source.canOpenPublication(
        const SourceMediaRef(sourceId: SourceId.local, itemId: 'plain.txt'),
      ),
      isFalse,
    );
    expect(
      source.canOpenPublication(
        SourceMediaRef(sourceId: const SourceId('remote'), itemId: epub),
      ),
      isFalse,
    );
    expect(
      source.canOpenPublication(
        SourceMediaRef(sourceId: SourceId.local, itemId: epubResource),
      ),
      isFalse,
    );
  });

  test('missing stored root is a normal null restore result', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'selectedTree');
      return null;
    });

    expect(await source.scanSelectedRoot(), isNull);
  });

  test('picker cancellation is a normal null result', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'pickTree');
      return null;
    });

    expect(await source.chooseRoot(), isFalse);
  });

  test('stored root scans without opening picker', () async {
    final calls = <String>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      if (call.method == 'selectedTree') {
        return {'id': 'tree', 'name': 'Selected folder'};
      }
      expect(call.method, 'children');
      expect(call.arguments, 'tree');
      return [
        {'id': 'opaque', 'name': 'test.mp4', 'isDirectory': false},
      ];
    });

    final items = await source.scanSelectedRoot();

    expect(items!.single.type, MediaType.anime);
    expect(items.single.source.itemId, 'opaque');
    expect(calls, ['selectedTree', 'children']);
  });

  test('explicit selection persists a root without scanning it yet', () async {
    final calls = <String>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      expect(call.method, 'pickTree');
      return {'id': 'new-tree', 'name': 'New folder'};
    });

    expect(await source.chooseRoot(), isTrue);
    expect(calls, ['pickTree']);
  });

  test(
    'recursive stored-root scan includes root and nested manga once',
    () async {
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'selectedTree') {
          return {'id': 'root', 'name': 'Root pages'};
        }
        expect(call.method, 'children');
        return call.arguments == 'root'
            ? [
                {'id': 'root/page', 'name': '1.jpg', 'isDirectory': false},
                {'id': 'nested', 'name': 'Nested pages', 'isDirectory': true},
              ]
            : [
                {'id': 'nested/page', 'name': '2.webp', 'isDirectory': false},
                {
                  'id': 'nested/video',
                  'name': 'clip.webm',
                  'isDirectory': false,
                },
              ];
      });

      final items = (await source.scanSelectedRoot())!;

      expect(
        items.where((m) => m.type == MediaType.manga).map((m) => m.title),
        ['Nested pages', 'Root pages'],
      );
      expect(items, hasLength(3));
    },
  );

  test('scan result snapshots copy their inputs', () {
    final media = <Media>[
      const Media(
        title: 'Book',
        type: MediaType.lightNovel,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'book'),
      ),
    ];
    final previews = <SourceMediaRef, SourceMediaRef>{};
    final scan = LocalMediaScanResult(
      rootName: 'Offline books',
      media: media,
      artwork: previews,
    );
    media.clear();
    previews[ref] = ref;
    expect(scan.media, hasLength(1));
    expect(scan.artwork, isEmpty);
    expect(() => scan.media.clear(), throwsUnsupportedError);
    expect(() => scan.artwork[ref] = ref, throwsUnsupportedError);
  });

  test('scan snapshot keeps folder label and image-only previews', () async {
    final calls = <String>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call.method);
      if (call.method == 'selectedTree') {
        return {'id': 'content://provider/root', 'name': 'My comics'};
      }
      if (call.method == 'children') {
        return [
          {
            'id': 'content://provider/10',
            'name': '10.jpg',
            'isDirectory': false,
          },
          {'id': 'content://provider/1', 'name': '1.jpg', 'isDirectory': false},
          {
            'id': 'content://provider/cover',
            'name': 'cover.png',
            'isDirectory': false,
          },
          {
            'id': 'content://provider/book',
            'name': 'volume.cbz',
            'isDirectory': false,
          },
        ];
      }
      if (call.method == 'read') {
        final args = call.arguments as Map<Object?, Object?>;
        expect(args['id'], 'content://provider/cover');
        expect(args['limit'], 8 * 1024 * 1024);
        return Uint8List.fromList([1, 2, 3]);
      }
      fail('Unexpected ${call.method}');
    });

    final snapshot = (await source.scanSelectedRootSnapshot())!;
    expect(snapshot.rootName, 'My comics');
    expect(snapshot.media, hasLength(2));
    final folder = snapshot.media.singleWhere(
      (item) => item.title == 'My comics',
    );
    final cbz = snapshot.media.singleWhere((item) => item.title == 'volume');
    expect(snapshot.artwork.containsKey(cbz.source), isFalse);
    final image = snapshot.artwork[folder.source]!;
    expect(LocalArtworkRef.tryDecode(image.itemId), 'content://provider/cover');
    expect(calls, ['selectedTree', 'children']);
    expect(await source.readArtwork(image), [1, 2, 3]);
    expect(calls.last, 'read');
  });

  test('artwork references reject unrelated and malformed locators', () async {
    expect(LocalArtworkRef.tryDecode('hikari-local-art:invalid!'), isNull);
    expect(LocalArtworkRef.tryEncode('file:///private/file.png'), isNull);
    expect(LocalArtworkRef.tryEncode('invalid-id'), isNull);
    messenger.setMockMethodCallHandler(
      channel,
      (_) async => fail('No native read expected'),
    );
    expect(await source.readArtwork(ref), isEmpty);
  });

  test('pages read on demand and sort numerically', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'children');
      return [
        for (final name in ['10.jpg', '1.JPG', '2.png', 'ignored.txt'])
          {'id': name, 'name': name, 'isDirectory': false},
      ];
    });
    expect((await source.pages(ref)).map((p) => p.itemId), [
      '1.JPG',
      '2.png',
      '10.jpg',
    ]);
  });

  test('malformed UTF-8 is replaced instead of throwing', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'read');
      return Uint8List.fromList([0x61, 0xff, 0x62]);
    });
    expect(await source.readText(ref), 'a�b');
  });

  test('stored-root failure propagates to feature recovery', () async {
    messenger.setMockMethodCallHandler(
      channel,
      (call) async => throw PlatformException(
        code: 'storage',
        message: 'Permission revoked',
      ),
    );

    expect(source.scanSelectedRoot, throwsA(isA<PlatformException>()));
  });
}
