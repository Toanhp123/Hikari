import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/infrastructure/local_media/archive_copy_pool.dart';

void main() {
  test(
    'coalesces copies, retains per reader, releases and rematerializes',
    () async {
      var copies = 0;
      final deleted = <String>[];
      final manager = ArchiveCopyPool(
        (_) async => 'copy-${++copies}',
        (path) async => deleted.add(path),
      );
      manager.retain('book');
      manager.retain('book');
      expect(
        await Future.wait([
          manager.read('book', (p) async => p),
          manager.read('book', (p) async => p),
        ]),
        ['copy-1', 'copy-1'],
      );
      await manager.release('book');
      expect(deleted, isEmpty);
      await manager.release('book');
      expect(deleted, ['copy-1']);
      expect(await manager.read('book', (p) async => p), 'copy-2');
      expect(deleted, ['copy-1', 'copy-2']);
      await manager.close();
    },
  );

  test(
    'release and shutdown wait for active read without deleting its copy',
    () async {
      final reading = Completer<void>();
      final done = Completer<void>();
      final deleted = <String>[];
      final manager = ArchiveCopyPool(
        (_) async => 'copy',
        (p) async => deleted.add(p),
      );
      manager.retain('book');
      final result = manager.read('book', (p) async {
        reading.complete();
        await done.future;
        return p;
      });
      await reading.future;
      await manager.release('book');
      var closed = false;
      final close = manager.close().then((_) => closed = true);
      expect(deleted, isEmpty);
      expect(closed, isFalse);
      done.complete();
      expect(await result, 'copy');
      await close;
      expect(deleted, ['copy']);
      expect(() => manager.retain('book'), throwsStateError);
    },
  );

  test('close attempts all deletions and retries failed cleanup', () async {
    final attempts = <String>[];
    var fail = true;
    final manager = ArchiveCopyPool((name) async => name, (path) async {
      attempts.add(path);
      if (path == 'a' && fail) throw StateError('delete failed');
    });
    for (final name in ['a', 'b']) {
      manager.retain(name);
      await manager.read(name, (path) async => path);
    }
    await expectLater(manager.close(), throwsStateError);
    expect(attempts, ['a', 'b', 'a']);
    fail = false;
    await manager.close();
    expect(attempts, ['a', 'b', 'a', 'a']);
  });

  test('copy and action failures do not poison subsequent reads', () async {
    var copies = 0;
    final manager = ArchiveCopyPool((_) async {
      if (++copies == 1) throw StateError('copy failed');
      return 'copy';
    }, (_) async {});
    manager.retain('book');
    await expectLater(manager.read('book', (p) async => p), throwsStateError);
    await expectLater(
      manager.read('book', (p) async => throw StateError('parse failed')),
      throwsStateError,
    );
    expect(await manager.read('book', (p) async => p), 'copy');
    expect(copies, 2);
    await manager.close();
  });

  test(
    'two unretained reads share copy and delete only after both finish',
    () async {
      final entered = Completer<void>();
      final resume = Completer<void>();
      var copies = 0;
      final deletes = <String>[];
      final pool = ArchiveCopyPool(
        (_) async => 'copy-${++copies}',
        (path) async => deletes.add(path),
      );
      final first = pool.read('book', (path) async {
        entered.complete();
        await resume.future;
        return path;
      });
      await entered.future;
      expect(await pool.read('book', (path) async => path), 'copy-1');
      expect(deletes, isEmpty);
      resume.complete();
      expect(await first, 'copy-1');
      expect(copies, 1);
      expect(deletes, ['copy-1']);
      await pool.close();
    },
  );

  test(
    'simultaneous close waits for an in-flight reader and joins shutdown',
    () async {
      final entered = Completer<void>();
      final resume = Completer<void>();
      final deletes = <String>[];
      final pool = ArchiveCopyPool(
        (_) async => 'copy',
        (path) async => deletes.add(path),
      );
      pool.retain('book');
      final read = pool.read('book', (path) async {
        entered.complete();
        await resume.future;
        return path;
      });
      await entered.future;
      var finished = false;
      final first = pool.close().then((_) => finished = true);
      final second = pool.close();
      await Future<void>.delayed(Duration.zero);
      expect(finished, isFalse);
      expect(deletes, isEmpty);
      expect(() => pool.retain('other'), throwsStateError);
      resume.complete();
      expect(await read, 'copy');
      await Future.wait([first, second]);
      expect(deletes, ['copy']);
    },
  );

  test('read failure survives a failed delete, retried at shutdown', () async {
    var deleteAttempts = 0;
    final pool = ArchiveCopyPool((_) async => 'copy', (_) async {
      if (++deleteAttempts == 1) throw StateError('delete error');
    });
    await expectLater(
      pool.read('book', (_) async => throw FormatException('bad archive')),
      throwsA(isA<FormatException>()),
    );
    expect(deleteAttempts, 1);
    await pool.close();
    expect(deleteAttempts, 2);
  });

  test(
    'read recovers a cache copy removed by Android while retained',
    () async {
      final available = <String>{};
      final deleted = <String>[];
      var copies = 0;
      final pool = ArchiveCopyPool(
        (_) async {
          final path = 'copy-${++copies}';
          available.add(path);
          return path;
        },
        (path) async {
          available.remove(path);
          deleted.add(path);
        },
        exists: (path) async => available.contains(path),
      );
      pool.retain('book');
      expect(await pool.read('book', (path) async => path), 'copy-1');
      available.remove('copy-1'); // Android evicted cacheDir while open.
      expect(
        await Future.wait([
          pool.read('book', (path) async => path),
          pool.read('book', (path) async => path),
        ]),
        ['copy-2', 'copy-2'],
      );
      expect(copies, 2);
      await pool.release('book');
      expect(deleted, ['copy-2']);
      await pool.close();
    },
  );

  test(
    'missing replacement copy fails without an unbounded retry loop',
    () async {
      var copies = 0;
      final pool = ArchiveCopyPool(
        (_) async => 'copy-${++copies}',
        (_) async {},
        exists: (_) async => false,
      );
      await expectLater(
        pool.read('book', (path) async => path),
        throwsStateError,
      );
      expect(copies, 2);
      await pool.close();
    },
  );

  test('close drains deletion started before entry removal', () async {
    final deleting = Completer<void>();
    final resume = Completer<void>();
    final pool = ArchiveCopyPool((_) async => 'copy', (_) async {
      deleting.complete();
      await resume.future;
    });
    pool.retain('book');
    await pool.read('book', (path) async => path);
    final release = pool.release('book');
    await deleting.future;
    var closed = false;
    final close = pool.close().then((_) => closed = true);
    await Future<void>.delayed(Duration.zero);
    expect(closed, isFalse);
    resume.complete();
    await Future.wait([release, close]);
    expect(closed, isTrue);
  });

  test(
    'shutdown waits for copy still materializing and then removes it',
    () async {
      final started = Completer<void>();
      final materialized = Completer<String>();
      final deleted = <String>[];
      final pool = ArchiveCopyPool((_) {
        started.complete();
        return materialized.future;
      }, (path) async => deleted.add(path));
      pool.retain('book');
      final read = pool.read('book', (path) async => path);
      await started.future;
      var closed = false;
      final close = pool.close().then((_) => closed = true);
      await Future<void>.delayed(Duration.zero);
      expect(closed, isFalse);
      materialized.complete('copy');
      expect(await read, 'copy');
      await close;
      expect(closed, isTrue);
      expect(deleted, ['copy']);
    },
  );
}
