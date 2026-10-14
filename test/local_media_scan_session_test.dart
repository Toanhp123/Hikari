import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/local_media_scan_result.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('hikari/local_media');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late LocalMediaSource source;
  late String root;
  late int queries;
  late int validations;
  late List<String> cancelled;
  late Future<Object?> Function(String) children;
  Object? picked;
  Future<Object?> Function()? resolveRoot;
  Future<Object?> Function()? pickRoot;

  Map<String, Object> row(String id, {bool directory = false}) => {
    'id': id,
    'name': directory ? id : '$id.mp4',
    'isDirectory': directory,
  };

  setUp(() {
    source = LocalMediaSource();
    root = 'root';
    queries = validations = 0;
    cancelled = [];
    picked = null;
    resolveRoot = null;
    pickRoot = null;
    children = (_) async => [row('video')];
    messenger.setMockMethodCallHandler(channel, (call) async {
      switch (call.method) {
        case 'selectedTree':
          validations++;
          return resolveRoot != null
              ? resolveRoot!()
              : {'id': root, 'name': root};
        case 'pickTree':
          return pickRoot != null ? pickRoot!() : picked;
        case 'cancelScan':
          cancelled.add(call.arguments as String);
          return null;
        case 'children': // Baseline path, so regressions fail on behavior.
          queries++;
          return children(call.arguments as String);
        case 'scanChildren':
          queries++;
          final args = call.arguments as Map;
          return {
            'rows': await children(args['id'] as String),
            'queueMicros': 0,
            'queryMicros': 0,
          };
        default:
          fail('Discovery must not read/materialize: ${call.method}');
      }
    });
  });
  tearDown(() async {
    await source.close();
    messenger.setMockMethodCallHandler(channel, null);
  });

  test('search joins full snapshot, then revalidates and reuses; refresh enumerates', () async {
    final gate = Completer<Object?>();
    children = (_) => gate.future;
    final snapshot = source.scanSelectedRootSnapshot();
    await Future<void>.delayed(Duration.zero);
    final search = source.scanSelectedRoot();
    await Future<void>.delayed(Duration.zero);
    expect(queries, 1);
    gate.complete([row('video')]);
    expect((await snapshot)!.media, await search);
    expect((await source.scanSelectedRoot())!.length, 1);
    expect(queries, 1);
    expect(validations, 3);
    await source.scanSelectedRootSnapshot();
    expect(queries, 2);
  });

  test(
    'root replacement cancels obsolete traversal and never publishes it',
    () async {
      final gate = Completer<Object?>();
      children = (id) =>
          id == 'root' ? gate.future : Future.value([row('new')]);
      final old = source.scanSelectedRootSnapshot();
      final rejected = expectLater(
        old,
        throwsA(isA<LocalMediaScanCancelledException>()),
      );
      await Future<void>.delayed(Duration.zero);
      root = 'replacement';
      final replacement = await source.scanSelectedRoot();
      expect(replacement!.single.source.itemId, 'new');
      gate.complete([row('old-child', directory: true)]);
      await rejected;
      expect(cancelled, hasLength(1));
      expect(queries, 2);
      expect((await source.scanSelectedRoot())!.single.source.itemId, 'new');
      expect(queries, 2);
    },
  );

  test('picker cancel keeps completed cache; picker cancels active traversal distinctly', () async {
    await source.scanSelectedRoot();
    expect(await source.chooseRoot(), isFalse);
    await source.scanSelectedRoot();
    expect(queries, 1);
    final gate = Completer<Object?>();
    children = (_) => gate.future;
    final refresh = source.scanSelectedRootSnapshot();
    final rejected = expectLater(
      refresh,
      throwsA(isA<LocalMediaScanCancelledException>()),
    );
    await Future<void>.delayed(Duration.zero);
    expect(await source.chooseRoot(), isFalse);
    gate.complete([row('partial')]);
    await rejected;
    expect(cancelled, hasLength(1));
    expect((await source.scanSelectedRoot())!.single.source.itemId, 'video');
  });

  test(
    'committed picker invalidates snapshot even before next root validation',
    () async {
      await source.scanSelectedRoot();
      picked = {'id': 'next', 'name': 'Next'};
      expect(await source.chooseRoot(), isTrue);
      root = 'next';
      children = (_) async => [row('next-video')];
      expect(
        (await source.scanSelectedRoot())!.single.source.itemId,
        'next-video',
      );
      expect(queries, 2);
    },
  );

  test(
    'transient refresh failure retains completed cache, access loss clears it',
    () async {
      await source.scanSelectedRoot();
      children = (_) async => throw PlatformException(code: 'storage');
      await expectLater(
        source.scanSelectedRootSnapshot(),
        throwsA(isA<PlatformException>()),
      );
      expect((await source.scanSelectedRoot())!.single.source.itemId, 'video');
      children = (_) async => throw PlatformException(code: 'access');
      await expectLater(
        source.scanSelectedRootSnapshot(),
        throwsA(isA<LocalMediaAccessException>()),
      );
      children = (_) async => [row('recovered')];
      expect(
        (await source.scanSelectedRoot())!.single.source.itemId,
        'recovered',
      );
    },
  );

  test(
    'null query and malformed references never cache partial data',
    () async {
      for (final rows in [
        null,
        [row('')],
      ]) {
        children = (_) async => rows;
        await expectLater(
          source.scanSelectedRoot(),
          throwsA(isA<StateError>()),
        );
      }
      children = (_) async => [row('complete')];
      expect(
        (await source.scanSelectedRoot())!.single.source.itemId,
        'complete',
      );
      expect(queries, 3);
    },
  );

  test(
    'duplicate rows and directory cycles terminate without duplicate media',
    () async {
      children = (id) async => id == 'root'
          ? [
              row('nested', directory: true),
              row('nested', directory: true),
              row('video'),
              row('video'),
            ]
          : [row('root', directory: true), row('video')];
      expect(await source.scanSelectedRoot(), hasLength(1));
      expect(queries, 2);
    },
  );

  test('shared image preserves each parent folder classification', () async {
    children = (id) async => id == 'root'
        ? [row('a', directory: true), row('b', directory: true)]
        : [
            {'id': 'shared', 'name': '1.jpg', 'isDirectory': false},
          ];
    expect(
      (await source.scanSelectedRoot())!.map((m) => m.source.itemId),
      unorderedEquals(['a', 'b']),
    );
    expect(queries, 3);
  });

  test(
    'total cap includes root; exact 50,000 succeeds without eager reads',
    () async {
      children = (_) async => List.generate(50000, (i) => row('v$i'));
      await expectLater(source.scanSelectedRoot(), throwsStateError);
      children = (_) async => List.generate(49999, (i) => row('v$i'));
      expect(await source.scanSelectedRoot(), hasLength(49999));
      expect(queries, 2);
    },
  );

  test(
    'shared access failure reaches every joiner as access, not cancellation',
    () async {
      final gate = Completer<Object?>();
      children = (_) => gate.future;
      final first = source.scanSelectedRootSnapshot();
      final a = expectLater(first, throwsA(isA<LocalMediaAccessException>()));
      await Future<void>.delayed(Duration.zero);
      final second = source.scanSelectedRoot();
      final b = expectLater(second, throwsA(isA<LocalMediaAccessException>()));
      await Future<void>.delayed(Duration.zero);
      gate.completeError(PlatformException(code: 'access'));
      await Future.wait([a, b]);
      expect(queries, 1);
    },
  );

  test(
    'delayed old resolver cannot republish after picker; search waits picker',
    () async {
      await source.scanSelectedRoot();
      final resolver = Completer<Object?>();
      resolveRoot = () => resolver.future;
      final old = source.scanSelectedRoot();
      final rejected = expectLater(
        old,
        throwsA(isA<LocalMediaScanCancelledException>()),
      );
      await Future<void>.delayed(Duration.zero);
      final picker = Completer<Object?>();
      pickRoot = () => picker.future;
      final choice = source.chooseRoot();
      final search = source.scanSelectedRoot();
      await Future<void>.delayed(Duration.zero);
      expect(validations, 2);
      root = 'next';
      resolveRoot = null;
      children = (_) async => [row('next-video')];
      picker.complete({'id': root, 'name': root});
      expect(await choice, isTrue);
      expect((await search)!.single.source.itemId, 'next-video');
      resolver.complete({'id': 'root', 'name': 'old'});
      await rejected;
      expect(
        (await source.scanSelectedRoot())!.single.source.itemId,
        'next-video',
      );
      expect(queries, 2);
    },
  );

  test(
    'deep tree uses iterative traversal and one query per directory',
    () async {
      children = (id) async {
        final depth = id == 'root' ? 0 : int.parse(id.substring(1));
        return depth == 1500
            ? [row('video')]
            : [row('d${depth + 1}', directory: true)];
      };
      expect(await source.scanSelectedRoot(), hasLength(1));
      expect(queries, 1501);
    },
  );

  test('close cancels blocked scan before another directory query', () async {
    final gate = Completer<Object?>();
    children = (_) => gate.future;
    final scan = source.scanSelectedRoot();
    final rejected = expectLater(
      scan,
      throwsA(isA<LocalMediaScanCancelledException>()),
    );
    await Future<void>.delayed(Duration.zero);
    await source.close();
    gate.complete([row('nested', directory: true)]);
    await rejected;
    expect(queries, 1);
    expect(cancelled, hasLength(1));
  });
}
