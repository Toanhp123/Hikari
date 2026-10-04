import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/application/sources/read_source_artwork.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/infrastructure/cache/cache_database.dart';
import 'package:hikari/infrastructure/cache/disk_byte_cache.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';

void main() {
  test('routes artwork to registered source capability', () async {
    final source = _ArtworkSource();
    final reader = ReadSourceArtwork(SourceRegistry([source]));
    final bytes = await reader.execute(_art('test:artwork', 'cover'));
    expect(bytes, [1, 2, 3]);
    expect(source.reads, 1);
  });

  test('uses cache only for registered artwork sources', () async {
    final source = _ArtworkSource();
    final cache = _MemoryCache();
    final reader = ReadSourceArtwork(SourceRegistry([source]), cache: cache);
    final artwork = _art('test:artwork', 'cover');
    expect(await reader.execute(artwork), [1, 2, 3]);
    expect(await reader.execute(artwork), [1, 2, 3]);
    expect(source.reads, 1);
    expect(cache.reads, 2);
    expect(
      await ReadSourceArtwork(
        SourceRegistry([_PlainSource()]),
        cache: cache,
      ).execute(_art('test:plain', 'cover')),
      isNull,
    );
  });

  test(
    'coalesces requests, retries failures and skips empty cache writes',
    () async {
      final source = _ArtworkSource();
      final gate = Completer<Uint8List>();
      source.gate = gate;
      final reader = ReadSourceArtwork(
        SourceRegistry([source]),
        cache: _MemoryCache(),
      );
      final artwork = _art('test:artwork', 'cover');
      final first = reader.execute(artwork);
      final second = reader.execute(artwork);
      await source.started.future;
      expect(source.reads, 1);
      gate.complete(Uint8List.fromList([1, 2, 3]));
      expect(await first, [1, 2, 3]);
      expect(await second, [1, 2, 3]);

      source.failNext = true;
      final retryReader = ReadSourceArtwork(SourceRegistry([source]));
      await expectLater(retryReader.execute(artwork), throwsStateError);
      expect(await retryReader.execute(artwork), [1, 2, 3]);
      source.bytes = Uint8List(0);
      final emptyReader = ReadSourceArtwork(SourceRegistry([source]));
      expect(await emptyReader.execute(artwork), isNull);
      expect(await emptyReader.execute(artwork), isNull);
      expect(source.reads, 5);
    },
  );

  test('cache read failure fails open', () async {
    final source = _ArtworkSource();
    final cache = _MemoryCache()..failRead = true;
    final artwork = _art('test:artwork', 'read-failure');
    expect(
      await ReadSourceArtwork(
        SourceRegistry([source]),
        cache: cache,
      ).execute(artwork),
      [1, 2, 3],
    );
    expect(source.reads, 1);
  });

  test('cache write failure returns successful source bytes', () async {
    final source = _ArtworkSource();
    final cache = _MemoryCache()..failWrite = true;
    expect(
      await ReadSourceArtwork(
        SourceRegistry([source]),
        cache: cache,
      ).execute(_art('test:artwork', 'write-failure')),
      [1, 2, 3],
    );
    expect(source.reads, 1);
  });

  test('provider failures propagate when cache read fails', () async {
    final source = _ArtworkSource()..failNext = true;
    final cache = _MemoryCache()..failRead = true;
    await expectLater(
      ReadSourceArtwork(
        SourceRegistry([source]),
        cache: cache,
      ).execute(_art('test:artwork', 'provider-failure')),
      throwsStateError,
    );
    expect(source.reads, 1);
  });

  test('empty source result is not cached', () async {
    final source = _ArtworkSource()..bytes = Uint8List(0);
    final cache = _MemoryCache();
    final reader = ReadSourceArtwork(SourceRegistry([source]), cache: cache);
    final artwork = _art('test:artwork', 'empty');
    expect(await reader.execute(artwork), isNull);
    expect(await reader.execute(artwork), isNull);
    expect(source.reads, 2);
    expect(cache.writes, 0);
  });

  test(
    'late provider completion returns bytes without reopening cache',
    () async {
      final source = _ArtworkSource();
      final gate = Completer<Uint8List>();
      source.gate = gate;
      final root = await Directory.systemTemp.createTemp('hikari-late-');
      addTearDown(() => root.delete(recursive: true));
      var opens = 0;
      final cache = DiskByteCache(
        cacheBaseDirectory: () async => root,
        openDatabase: (path) async {
          opens++;
          return CacheDatabase(NativeDatabase(File('$path/index.sqlite')));
        },
      );
      addTearDown(cache.close);
      final reader = ReadSourceArtwork(SourceRegistry([source]), cache: cache);
      final load = reader.execute(_art('test:artwork', 'late'));
      await source.started.future;
      await cache.close();
      gate.complete(Uint8List.fromList([4]));
      expect(await load, [4]);
      expect(opens, 1);
      expect(
        await root
            .list(recursive: true)
            .where((file) => file.path.endsWith('.blob'))
            .length,
        0,
      );
    },
  );

  test('cache key uses unambiguous stable JSON tuple', () {
    expect(ReadSourceArtwork.cacheKey(_art('a:b', 'c')), '["a:b","c"]');
    expect(ReadSourceArtwork.cacheKey(_art('a', 'b:c')), '["a","b:c"]');
  });

  test(
    'composed reader reuses disk cache across dependency recreation',
    () async {
      final root = await Directory.systemTemp.createTemp('hikari-reader-');
      addTearDown(() => root.delete(recursive: true));
      CacheDatabase openIndex(String path) =>
          CacheDatabase(NativeDatabase(File('$path/index.sqlite')));
      DiskByteCache newCache() => DiskByteCache(
        cacheBaseDirectory: () async => root,
        namespaceByteBudgets: const {ReadSourceArtwork.cacheNamespace: 64},
        openDatabase: (path) async => openIndex(path),
      );
      final artwork = _art('test:artwork', 'restart');
      final firstSource = _ArtworkSource();
      final firstDb = UserDatabase(NativeDatabase.memory());
      final firstCache = newCache();
      final firstDependencies = AppDependencies.create(
        database: firstDb,
        ownsDatabase: true,
        cache: firstCache,
        ownsCache: true,
        additionalSources: [firstSource],
      );
      addTearDown(firstDependencies.dispose);
      addTearDown(firstCache.close);
      expect(await firstDependencies.readSourceArtwork.execute(artwork), [
        1,
        2,
        3,
      ]);
      await firstDependencies.dispose();

      final secondSource = _ArtworkSource()..bytes = Uint8List.fromList([9]);
      final secondDb = UserDatabase(NativeDatabase.memory());
      final secondCache = newCache();
      final secondDependencies = AppDependencies.create(
        database: secondDb,
        ownsDatabase: true,
        cache: secondCache,
        ownsCache: true,
        additionalSources: [secondSource],
      );
      addTearDown(secondDependencies.dispose);
      expect(await secondDependencies.readSourceArtwork.execute(artwork), [
        1,
        2,
        3,
      ]);
      expect(secondSource.reads, 0);
    },
  );

  testWidgets('SourceArtwork remount reuses composed disk cache', (
    tester,
  ) async {
    final root = Directory.systemTemp.createTempSync('hikari-widget-');
    addTearDown(() => root.delete(recursive: true));
    final cache = DiskByteCache(
      cacheBaseDirectory: () async => root,
      namespaceByteBudgets: const {ReadSourceArtwork.cacheNamespace: 64},
      openDatabase: (path) async =>
          CacheDatabase(NativeDatabase(File('$path/index.sqlite'))),
    );
    addTearDown(cache.close);
    final source = _ArtworkSource();
    final reader = ReadSourceArtwork(SourceRegistry([source]), cache: cache);
    final artwork = _art('test:artwork', 'remount');
    Widget view() => MaterialApp(
      home: SourceArtwork(
        key: const ValueKey('cover'),
        resource: artwork,
        read: reader.execute,
        builder: (_, bytes, loading) =>
            Text(loading ? 'loading' : '${bytes?.length ?? 0}'),
      ),
    );

    await tester.runAsync(() async {
      await tester.pumpWidget(view());
      await reader.execute(artwork);
    });
    await tester.pump();
    expect(find.text('3'), findsOneWidget);
    expect(source.reads, 1);
    await tester.pumpWidget(const MaterialApp(home: SizedBox.shrink()));
    await tester.runAsync(() async {
      await tester.pumpWidget(view());
      await reader.execute(artwork);
    });
    await tester.pump();
    expect(find.text('3'), findsOneWidget);
    expect(source.reads, 1);
  });

  testWidgets('SourceArtwork displays data without decoding image', (
    tester,
  ) async {
    final reader = ReadSourceArtwork(SourceRegistry([_ArtworkSource()]));
    await tester.pumpWidget(
      MaterialApp(
        home: SourceArtwork(
          resource: _art('test:artwork', 'bytes'),
          read: reader.execute,
          builder: (_, bytes, loading) =>
              Text(loading ? 'loading' : '${bytes?.length}'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('3'), findsOneWidget);
  });

  test('returns null without artwork capability', () async {
    final reader = ReadSourceArtwork(SourceRegistry([_PlainSource()]));
    expect(await reader.execute(_art('test:plain', 'cover')), isNull);
  });
}

SourceMediaRef _art(String source, String item) =>
    SourceMediaRef(sourceId: SourceId(source), itemId: item);

final class _ArtworkSource implements ArtworkSource {
  int reads = 0;
  Uint8List bytes = Uint8List.fromList([1, 2, 3]);
  Completer<Uint8List>? gate;
  final started = Completer<void>();
  bool failNext = false;

  @override
  SourceId get id => const SourceId('test:artwork');
  @override
  String get name => 'Artwork source';

  @override
  Future<Uint8List> readArtwork(SourceMediaRef artwork) async {
    reads++;
    if (!started.isCompleted) started.complete();
    if (failNext) {
      failNext = false;
      throw StateError('provider failure');
    }
    final pending = gate;
    gate = null;
    return pending?.future ?? bytes;
  }
}

final class _MemoryCache implements ByteCache {
  final values = <String, Uint8List>{};
  int reads = 0;
  int writes = 0;
  bool failRead = false;
  bool failWrite = false;
  bool closed = false;
  String _key(String namespace, String key) => '$namespace/$key';

  @override
  Future<Uint8List?> read(String namespace, String key) async {
    reads++;
    if (failRead) throw StateError('cache read failure');
    return values[_key(namespace, key)];
  }

  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {
    if (closed) return;
    writes++;
    if (failWrite) throw StateError('cache write failure');
    values[_key(namespace, key)] = bytes;
  }

  @override
  Future<void> close() async {
    await pendingWrites;
    closed = true;
  }

  Future<void> pendingWrites = Future.value();
}

final class _PlainSource implements MediaSource {
  @override
  SourceId get id => const SourceId('test:plain');
  @override
  String get name => 'Plain source';
}
