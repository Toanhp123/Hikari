import 'package:flutter/services.dart';

import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/domain/media/media.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('hikari/local_media');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final source = LocalMediaSource();
  const ref = SourceMediaRef(sourceId: SourceId.local, itemId: 'opaque');
  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

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
    expect(await source.text(ref), 'a�b');
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
