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
    expect(attempts, ['a', 'b']);
    fail = false;
    await manager.close();
    expect(attempts, ['a', 'b', 'a']);
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
}
