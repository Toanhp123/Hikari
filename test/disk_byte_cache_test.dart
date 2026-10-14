import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/infrastructure/cache/cache_database.dart';
import 'package:hikari/infrastructure/cache/disk_byte_cache.dart';
import 'package:path/path.dart' as p;

void main() {
  late Directory root;
  setUp(
    () async => root = await Directory.systemTemp.createTemp('hikari-cache-'),
  );
  tearDown(() async => root.delete(recursive: true));

  test('production database opener persists across cache recreation', () async {
    final cache = DiskByteCache(
      cacheBaseDirectory: () async => root,
      namespaceByteBudgets: const {'art': 64},
    );
    addTearDown(cache.close);
    await cache.write('art', 'cover', Uint8List.fromList([7]));
    expect(await cache.read('art', 'cover'), [7]);
    await cache.close();
    final reopened = DiskByteCache(
      cacheBaseDirectory: () async => root,
      namespaceByteBudgets: const {'art': 64},
    );
    addTearDown(reopened.close);
    expect(await reopened.read('art', 'cover'), [7]);
  });

  test('persists opaque keys across reopen and isolates namespaces', () async {
    final cache = _cache(root, {'art': 64, 'other': 64});
    addTearDown(cache.close);
    await cache.write('art', 'private/title', Uint8List.fromList([1, 2, 3]));
    await cache.write('other', 'private/title', Uint8List.fromList([4]));
    expect(await cache.read('art', 'private/title'), [1, 2, 3]);
    expect(await cache.sizeBytes('art'), 3);
    await cache.close();

    final reopened = _cache(root, {'art': 64, 'other': 64});
    addTearDown(reopened.close);
    expect(await reopened.read('art', 'private/title'), [1, 2, 3]);
    expect(await reopened.read('other', 'private/title'), [4]);
    final paths = (await root.list(recursive: true).toList())
        .whereType<File>()
        .map((file) => file.path)
        .join();
    expect(paths, isNot(contains('private')));
    await reopened.close();
  });

  test('enforces byte LRU per namespace and rejects oversize writes', () async {
    final cache = _cache(root, {'small': 4, 'other': 8});
    addTearDown(cache.close);
    await cache.write('small', 'one', Uint8List.fromList([1, 2]));
    await cache.read('small', 'one');
    await cache.write('other', 'other', Uint8List.fromList([8, 8, 8, 8]));
    await cache.write('small', 'two', Uint8List.fromList([3, 4, 5]));
    expect(await cache.read('small', 'one'), isNull);
    expect(await cache.read('small', 'two'), [3, 4, 5]);
    expect(await cache.sizeBytes('small'), 3);
    expect(await cache.sizeBytes('other'), 4);
    await cache.write('small', 'oversize', Uint8List.fromList([1, 2, 3, 4, 5]));
    expect(await cache.read('small', 'oversize'), isNull);
    await cache.close();
  });

  test('startup drops missing metadata before applying byte LRU', () async {
    final initial = _cache(root, {'art': 128});
    addTearDown(initial.close);
    await initial.write('art', 'old-valid', Uint8List(40));
    await initial.write('art', 'new-missing', Uint8List(40));
    await initial.close();

    final dir = Directory('${root.path}/hikari/cache-v1');
    final missingHash = _keyHash('art', 'new-missing');
    final missingBlob =
        (await dir
                .list()
                .where(
                  (entity) => p.basename(entity.path).startsWith(missingHash),
                )
                .first)
            as File;
    await missingBlob.delete();

    final reopened = _cache(root, {'art': 40});
    addTearDown(reopened.close);
    expect(await reopened.read('art', 'old-valid'), Uint8List(40));
    expect(await reopened.read('art', 'new-missing'), isNull);
    expect(await reopened.sizeBytes('art'), 40);
  });

  test('startup removes abandoned temp and orphan files', () async {
    final cache = _cache(root, {'art': 64});
    addTearDown(cache.close);
    await cache.write('art', 'cover', Uint8List.fromList([1]));
    await cache.close();
    final dir = Directory('${root.path}/hikari/cache-v1');
    await File('${dir.path}/abandoned.tmp').writeAsString('partial');
    await File('${dir.path}/orphan.blob').writeAsString('orphan');
    final reopened = _cache(root, {'art': 64});
    addTearDown(reopened.close);
    expect(await reopened.read('art', 'cover'), [1]);
    expect(await File('${dir.path}/abandoned.tmp').exists(), isFalse);
    expect(await File('${dir.path}/orphan.blob').exists(), isFalse);
    await reopened.close();
  });

  test('failed publish refuses writes until new instance recovers', () async {
    final broken = _cache(root, {
      'art': 64,
    }, beforePublish: () async => throw StateError('disk'));
    addTearDown(broken.close);
    await broken.write('art', 'cover', Uint8List.fromList([1]));
    expect(await broken.read('art', 'cover'), isNull);
    await broken.clear('art');
    await broken.write('art', 'still-refused', Uint8List.fromList([3]));
    expect(await broken.read('art', 'still-refused'), isNull);
    await broken.close();
    final recovered = _cache(root, {'art': 64});
    addTearDown(recovered.close);
    await recovered.write('art', 'cover', Uint8List.fromList([2]));
    expect(await recovered.read('art', 'cover'), [2]);
    await recovered.close();
  });

  test(
    'failed index publish leaves orphan and latches writes until recovery',
    () async {
      var failPublish = true;
      final cache = _cache(
        root,
        {'art': 64},
        beforeIndexPublish: () async {
          if (failPublish) throw StateError('index unavailable');
        },
      );
      addTearDown(cache.close);
      await cache.write('art', 'key', Uint8List.fromList([1]));
      failPublish = false;
      await cache.clear('unrelated');
      await cache.write('art', 'other', Uint8List.fromList([2]));
      expect(await cache.read('art', 'other'), isNull);
      await cache.close();

      failPublish = false;
      final recovered = _cache(root, {'art': 64});
      addTearDown(recovered.close);
      expect(await recovered.read('art', 'key'), isNull);
      await recovered.write('art', 'other', Uint8List.fromList([2]));
      expect(await recovered.read('art', 'other'), [2]);
      await recovered.close();
    },
  );

  test(
    'replacement cleanup failure preserves new index and latches writes',
    () async {
      var failCleanup = false;
      final cache = _cache(
        root,
        {'art': 64},
        beforeReplacementCleanup: () async {
          if (failCleanup) throw StateError('cleanup denied');
        },
      );
      addTearDown(cache.close);
      await cache.write('art', 'key', Uint8List.fromList([1]));
      failCleanup = true;
      await cache.write('art', 'key', Uint8List.fromList([2]));
      expect(await cache.read('art', 'key'), [2]);
      await cache.write('art', 'other', Uint8List.fromList([3]));
      expect(await cache.read('art', 'other'), isNull);
      await cache.close();
      failCleanup = false;
      final recovered = _cache(root, {'art': 64});
      addTearDown(recovered.close);
      expect(await recovered.read('art', 'key'), [2]);
      expect(await recovered.read('art', 'other'), isNull);
      await recovered.close();
    },
  );

  test('remove and clear isolate namespaces and repair missing blob', () async {
    final cache = _cache(root, {'art': 64, 'other': 64});
    addTearDown(cache.close);
    await cache.write('art', 'one', Uint8List.fromList([1]));
    await cache.write('art', 'two', Uint8List.fromList([2]));
    await cache.write('other', 'one', Uint8List.fromList([3]));
    final keyHash = _keyHash('art', 'one');
    final dir = Directory('${root.path}/hikari/cache-v1');
    final target =
        (await dir
                .list()
                .where(
                  (entity) => entity.path.startsWith(
                    '${dir.path}${Platform.pathSeparator}$keyHash-',
                  ),
                )
                .first)
            as File;
    await target.delete();
    expect(await cache.read('art', 'one'), isNull);
    await cache.remove('art', 'two');
    await cache.clear('art');
    expect(await cache.sizeBytes('art'), 0);
    expect(await cache.read('other', 'one'), [3]);
    await cache.close();
  });

  test(
    'LRU retains recently read entry rather than newest insertion',
    () async {
      var tick = 0;
      final cache = DiskByteCache(
        cacheBaseDirectory: () async => root,
        namespaceByteBudgets: {'art': 2},
        clock: () => ++tick,
        openDatabase: (path) async =>
            CacheDatabase(NativeDatabase(File('$path/index.sqlite'))),
      );
      addTearDown(cache.close);
      await cache.write('art', 'a', Uint8List.fromList([1]));
      await cache.write('art', 'b', Uint8List.fromList([2]));
      expect(await cache.read('art', 'a'), [1]);
      await cache.write('art', 'c', Uint8List.fromList([3]));
      expect(await cache.read('art', 'b'), isNull);
      expect(await cache.read('art', 'a'), [1]);
      expect(await cache.read('art', 'c'), [3]);
    },
  );

  test('concurrent close drains active publication and stays closed', () async {
    final started = Completer<void>();
    final release = Completer<void>();
    final cache = _cache(
      root,
      {'art': 64},
      beforePublish: () async {
        started.complete();
        await release.future;
      },
    );
    addTearDown(cache.close);
    final write = cache.write('art', 'key', Uint8List.fromList([1]));
    await started.future;
    final firstClose = cache.close();
    expect(identical(firstClose, cache.close()), isTrue);
    var closed = false;
    firstClose.then((_) => closed = true);
    await Future<void>.delayed(Duration.zero);
    expect(closed, isFalse);
    release.complete();
    await write;
    await firstClose;
    expect(await cache.read('art', 'key'), isNull);
    final reopened = _cache(root, {'art': 64});
    addTearDown(reopened.close);
    expect(await reopened.read('art', 'key'), [1]);
  });

  test('close admits synchronous pre-microtask write', () async {
    final cache = _cache(root, {'art': 64});
    final write = cache.write('art', 'sync', Uint8List.fromList([7]));
    final closing = cache.close();
    await Future.wait([write, closing]);
    final reopened = _cache(root, {'art': 64});
    addTearDown(reopened.close);
    expect(await reopened.read('art', 'sync'), [7]);
  });

  test('close drains admitted queued writes and rejects later calls', () async {
    final started = Completer<void>();
    final release = Completer<void>();
    final cache = _cache(
      root,
      {'art': 64},
      beforePublish: () async {
        if (!started.isCompleted) {
          started.complete();
          await release.future;
        }
      },
    );
    final first = cache.write('art', 'first', Uint8List.fromList([1]));
    await started.future;
    final second = cache.write('art', 'second', Uint8List.fromList([2]));
    final closing = cache.close();
    final later = cache.write('art', 'later', Uint8List.fromList([3]));
    release.complete();
    await Future.wait([first, second, closing, later]);

    final reopened = _cache(root, {'art': 64});
    addTearDown(reopened.close);
    expect(await reopened.read('art', 'first'), [1]);
    expect(await reopened.read('art', 'second'), [2]);
    expect(await reopened.read('art', 'later'), isNull);
  });

  test('close succeeds when dirty-touch flush fails', () async {
    late CacheDatabase database;
    final cache = DiskByteCache(
      cacheBaseDirectory: () async => root,
      namespaceByteBudgets: const {'art': 64},
      openDatabase: (path) async =>
          database = CacheDatabase(NativeDatabase(File('$path/index.sqlite'))),
    );
    await cache.write('art', 'key', Uint8List.fromList([1]));
    expect(await cache.read('art', 'key'), [1]);
    await database.customStatement('''
      CREATE TRIGGER reject_touch BEFORE UPDATE ON cache_entries
      BEGIN SELECT RAISE(FAIL, 'touch unavailable'); END
    ''');
    await expectLater(cache.close(), completes);
    await expectLater(cache.close(), completes);
  });

  test('failed database close can be retried', () async {
    var failClose = true;
    late _CloseFailsOnceDatabase database;
    final cache = DiskByteCache(
      cacheBaseDirectory: () async => root,
      namespaceByteBudgets: const {'art': 64},
      openDatabase: (path) async => database = _CloseFailsOnceDatabase(
        File('$path/index.sqlite'),
        shouldFail: () => failClose,
      ),
    );
    await cache.write('art', 'key', Uint8List.fromList([1]));

    await expectLater(cache.close(), throwsStateError);
    failClose = false;
    await cache.close();
    expect(database.closeCount, 2);
  });

  test('corrupt index resets once and becomes writable again', () async {
    final dir = await Directory('${root.path}/hikari/cache-v1')
        .create(recursive: true);
    await File('${dir.path}/index.sqlite')
        .writeAsString('not a SQLite database');
    final cache = _cache(root, {'art': 64});
    addTearDown(cache.close);

    expect(await cache.read('art', 'key'), isNull);
    await cache.write('art', 'key', Uint8List.fromList([1]));
    expect(await cache.read('art', 'key'), [1]);
    expect(await cache.sizeBytes('art'), 1);

    await cache.close();
    final reopened = _cache(root, {'art': 64});
    addTearDown(reopened.close);
    expect(await reopened.read('art', 'key'), [1]);
  });

  test('temporary file write failure refuses further admissions', () async {
    final cache = DiskByteCache(
      cacheBaseDirectory: () async => root,
      namespaceByteBudgets: {'art': 64},
      clock: () => 1,
      openDatabase: (path) async =>
          CacheDatabase(NativeDatabase(File('$path/index.sqlite'))),
    );
    addTearDown(cache.close);
    expect(await cache.read('art', 'key'), isNull);
    final bytes = Uint8List.fromList([1]);
    final digest = sha256.convert(bytes);
    final collision = await Directory(
      '${root.path}/hikari/cache-v1/${_keyHash('art', 'key')}-$digest.blob.1.tmp',
    ).create();
    await cache.write('art', 'key', bytes);
    await collision.delete();
    await cache.write('art', 'next', bytes);
    expect(await cache.read('art', 'next'), isNull);
    expect(await cache.sizeBytes('art'), 0);
  });

  test('metadata query failure latches writes after query recovers', () async {
    late CacheDatabase database;
    final cache = DiskByteCache(
      cacheBaseDirectory: () async => root,
      namespaceByteBudgets: {'art': 64},
      openDatabase: (path) async =>
          database = CacheDatabase(NativeDatabase(File('$path/index.sqlite'))),
    );
    addTearDown(cache.close);
    expect(await cache.read('art', 'key'), isNull);
    await database.customStatement(
      'ALTER TABLE cache_entries RENAME TO unavailable_entries',
    );
    expect(await cache.read('art', 'key'), isNull);
    await database.customStatement(
      'ALTER TABLE unavailable_entries RENAME TO cache_entries',
    );
    await cache.clear('unrelated');
    await cache.write('art', 'key', Uint8List.fromList([1]));
    expect(await cache.read('art', 'key'), isNull);
    expect(await cache.sizeBytes('art'), 0);
  });

  test('maintenance clear waits for writes and isolates namespaces', () async {
    final started = Completer<void>();
    final release = Completer<void>();
    final cache = _cache(
      root,
      {'art': 64, 'pages': 64, 'other': 64},
      beforePublish: () async {
        if (!started.isCompleted) {
          started.complete();
          await release.future;
        }
      },
    );
    addTearDown(cache.close);
    final writing = cache.write('art', 'cover', Uint8List(2));
    await started.future;
    final clearing = cache.clearNamespaces(['art', 'pages']);
    release.complete();
    await writing;
    await clearing;
    expect(await cache.sizes(['art', 'pages', 'other']), {
      'art': 0,
      'pages': 0,
      'other': 0,
    });
    await cache.write('other', 'keep', Uint8List(3));
    await cache.clearNamespaces(['art', 'pages']);
    expect(await cache.read('other', 'keep'), Uint8List(3));
  });

  test('maintenance failure does not report success', () async {
    final cache = _cache(root, {'art': 64});
    addTearDown(cache.close);
    await cache.write('art', 'cover', Uint8List.fromList([7]));
    final dir = Directory(p.join(root.path, 'hikari', 'cache-v1'));
    final blob =
        (await dir.list().where((e) => e.path.endsWith('.blob')).first) as File;
    await blob.delete();
    await Directory(blob.path).create();
    await expectLater(
      cache.clearNamespaces(['art']),
      throwsA(isA<FileSystemException>()),
    );
    // This is not reported as a successful clear and metadata remains indexed.
    expect(await cache.sizes(['art']), {'art': 1});
  });

  test('profiling reports hits, misses, hash and trim without keys', () async {
    final samples = <CacheIoSample>[];
    final cache = _cache(root, {'art': 4}, onIoSample: samples.add);
    addTearDown(cache.close);

    expect(await cache.read('art', 'private/title'), isNull);
    await cache.write('art', 'private/title', Uint8List.fromList([1, 2]));
    expect(await cache.read('art', 'private/title'), [1, 2]);
    await cache.write('art', 'another/private/title', Uint8List(4));
    expect(await cache.read('art', 'private/title'), isNull);

    expect(samples.map((sample) => sample.operation), [
      'read',
      'write',
      'read',
      'write',
      'read',
    ]);
    expect(
      samples
          .where((sample) => sample.operation == 'read')
          .map((sample) => sample.hit),
      [false, true, false],
    );
    expect(samples[1].bytes, 2);
    expect(samples[2].bytes, 2);
    expect(samples[3].bytes, 4);
    for (final sample in samples) {
      expect(sample.queueWaitMicros, greaterThanOrEqualTo(0));
      expect(sample.executionMicros, greaterThanOrEqualTo(0));
      expect(sample.hashMicros, greaterThanOrEqualTo(0));
      expect(sample.trimMicros, greaterThanOrEqualTo(0));
      expect(sample.failed, isFalse);
      expect(sample.toTimelineArguments().keys, isNot(contains('key')));
      expect(
        sample.toTimelineArguments().toString(),
        isNot(contains('private')),
      );
    }
    expect(samples[0].bytes, 0);
  });

  test('profiling separates queued wait from execution', () async {
    final publishing = Completer<void>();
    final release = Completer<void>();
    final samples = <CacheIoSample>[];
    final cache = _cache(
      root,
      {'art': 64},
      onIoSample: samples.add,
      beforePublish: () async {
        publishing.complete();
        await release.future;
      },
    );
    addTearDown(cache.close);

    final write = cache.write('art', 'first', Uint8List.fromList([1]));
    await publishing.future;
    final read = cache.read('art', 'first');
    final clear = cache.clearNamespaces(['art']);
    await Future<void>.delayed(const Duration(milliseconds: 10));
    expect(samples, isEmpty);
    release.complete();
    await write;
    expect(await read, [1]);
    await clear;

    expect(samples.map((sample) => sample.operation), [
      'write',
      'read',
      'clearNamespaces',
    ]);
    expect(samples[1].queueWaitMicros, greaterThan(0));
    expect(samples[2].queueWaitMicros, greaterThan(0));
    expect(samples[1].hit, isTrue);
    expect(await cache.read('art', 'first'), isNull);
  });

  test(
    'profiling does not change fail-open or maintenance error behavior',
    () async {
      final samples = <CacheIoSample>[];
      final cache = _cache(
        root,
        {'art': 64},
        onIoSample: samples.add,
        beforePublish: () async => throw StateError('disk'),
      );
      addTearDown(cache.close);
      await cache.write('art', 'key', Uint8List.fromList([1]));
      expect(samples.single.failed, isTrue);
      expect(await cache.read('art', 'key'), isNull);
      expect(samples.last.hit, isFalse);
      await cache.close();

      // Even a broken observer must not turn a successful write into failure.
      final unobserved = _cache(root, {
        'art': 64,
      }, onIoSample: (_) => throw StateError('observer failed'));
      addTearDown(unobserved.close);
      await unobserved.write('art', 'works', Uint8List.fromList([9]));
      expect(await unobserved.read('art', 'works'), [9]);
      await unobserved.clearNamespaces(['art']);
      expect(await unobserved.read('art', 'works'), isNull);
    },
  );

  test('profiling preserves clear failure propagation', () async {
    final samples = <CacheIoSample>[];
    final cache = _cache(root, {'art': 64}, onIoSample: samples.add);
    addTearDown(cache.close);
    await cache.write('art', 'cover', Uint8List.fromList([7]));

    final dir = Directory(p.join(root.path, 'hikari', 'cache-v1'));
    final blob =
        (await dir.list().where((entry) => entry.path.endsWith('.blob')).first)
            as File;
    await blob.delete();
    await Directory(blob.path).create();
    await expectLater(
      cache.clearNamespaces(['art']),
      throwsA(isA<FileSystemException>()),
    );
    expect(samples.last.operation, 'clearNamespaces');
    expect(samples.last.failed, isTrue);
    expect(await cache.sizes(['art']), {'art': 1});
  });

  test('corrupt blob becomes miss', () async {
    final cache = _cache(root, {'art': 64});
    addTearDown(cache.close);
    await cache.write('art', 'key', Uint8List.fromList([1, 2]));
    final keyHash = _keyHash('art', 'key');
    final dir = Directory('${root.path}/hikari/cache-v1');
    final file =
        (await dir
                .list()
                .where((entity) => entity.path.contains(keyHash))
                .first)
            as File;
    await file.writeAsBytes([9, 9]);
    expect(await cache.read('art', 'key'), isNull);
    await cache.close();
  });
}

String _keyHash(String namespace, String key) =>
    sha256.convert(utf8.encode(jsonEncode([1, namespace, key]))).toString();

final class _CloseFailsOnceDatabase extends CacheDatabase {
  _CloseFailsOnceDatabase(File file, {required this.shouldFail})
    : super(NativeDatabase(file));

  final bool Function() shouldFail;
  int closeCount = 0;

  @override
  Future<void> close() async {
    closeCount++;
    if (shouldFail()) throw StateError('close failed');
    await super.close();
  }
}

DiskByteCache _cache(
  Directory root,
  Map<String, int> budgets, {
  Future<void> Function()? beforePublish,
  Future<void> Function()? beforeIndexPublish,
  Future<void> Function()? beforeReplacementCleanup,
  void Function(CacheIoSample)? onIoSample,
}) => DiskByteCache(
  cacheBaseDirectory: () async => root,
  namespaceByteBudgets: budgets,
  openDatabase: (path) async =>
      CacheDatabase(NativeDatabase(File('$path/index.sqlite'))),
  beforePublish: beforePublish,
  beforeIndexPublish: beforeIndexPublish,
  beforeReplacementCleanup: beforeReplacementCleanup,
  onIoSample: onIoSample,
);
