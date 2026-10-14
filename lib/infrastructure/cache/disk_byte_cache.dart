import 'dart:convert';
import 'dart:developer' as developer;
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/core/cache/cache_maintenance.dart';
import 'package:hikari/infrastructure/cache/cache_database.dart';

/// A single cache-queue operation. All durations use a monotonic stopwatch.
///
/// `bytes` is the returned byte count on a hit or the submitted write size.
/// `hit` is only set for reads. Keys and source references are never recorded.
final class CacheIoSample {
  const CacheIoSample({
    required this.operation,
    required this.queueWaitMicros,
    required this.executionMicros,
    required this.hashMicros,
    required this.trimMicros,
    required this.bytes,
    required this.failed,
    this.hit,
  });

  final String operation;
  final int queueWaitMicros;
  final int executionMicros;
  final int hashMicros;
  final int trimMicros;
  final int bytes;
  final bool failed;
  final bool? hit;

  Map<String, Object> toTimelineArguments() => {
    'queueWaitUs': queueWaitMicros,
    'executionUs': executionMicros,
    'hashUs': hashMicros,
    'trimUs': trimMicros,
    'bytes': bytes,
    'failed': failed,
    'hit': ?hit,
  };
}

final class _CacheIoTiming {
  int hashMicros = 0;
  int trimMicros = 0;
  int bytes = 0;
  bool failed = false;
  bool? hit;
}

final class DiskByteCache implements ByteCache, CacheMaintenance {
  DiskByteCache({
    Future<Directory> Function()? cacheBaseDirectory,
    Map<String, int> namespaceByteBudgets = const {},
    Future<CacheDatabase> Function(String rootPath)? openDatabase,
    int Function()? clock,
    this._beforePublish,
    this._beforeIndexPublish,
    this._beforeReplacementCleanup,
    this.onIoSample,
  }) : _isTestDefaultCache =
           cacheBaseDirectory == null &&
           Platform.environment['FLUTTER_TEST'] == 'true',
       _cacheBaseDirectory = cacheBaseDirectory ?? _defaultCacheDirectory,
       _byteBudgets = Map.unmodifiable(namespaceByteBudgets),
       _openDatabase = openDatabase ?? _defaultDatabase,
       _clock = clock ?? _now;

  final bool _isTestDefaultCache;
  final Future<Directory> Function() _cacheBaseDirectory;
  final Map<String, int> _byteBudgets;
  final Future<CacheDatabase> Function(String rootPath) _openDatabase;
  final int Function() _clock;
  final Future<void> Function()? _beforePublish;
  final Future<void> Function()? _beforeIndexPublish;
  final Future<void> Function()? _beforeReplacementCleanup;

  /// Optional observer for profiling/tests. Observer failures never affect I/O.
  final void Function(CacheIoSample)? onIoSample;

  /// Opt into DevTools async timeline events in a profile build with
  /// --dart-define=HIKARI_CACHE_TRACE=true. No persistent logs are written.
  static const _traceTimeline = bool.fromEnvironment('HIKARI_CACHE_TRACE');
  Future<void> _tail = Future.value();
  Future<void>? _initializing;
  CacheDatabase? _database;
  Directory? _root;
  bool _closed = false;
  bool _shutdownRequested = false;
  Future<void>? _closing;
  bool _acceptWrites = true;
  final Map<String, int> _dirtyTouches = {};

  static int _now() => DateTime.now().microsecondsSinceEpoch;

  static Future<Directory> _defaultCacheDirectory() async {
    if (Platform.environment['FLUTTER_TEST'] == 'true') {
      return Directory.systemTemp.createTempSync('hikari-test-cache-');
    }
    return getApplicationCacheDirectory();
  }

  static Future<CacheDatabase> _defaultDatabase(String rootPath) async {
    final dbFile = File(p.join(rootPath, 'index.sqlite'));
    if (Platform.environment['FLUTTER_TEST'] == 'true') {
      return CacheDatabase(NativeDatabase(dbFile));
    }
    return CacheDatabase.at(dbFile.path);
  }

  Future<T> _serialized<T>(
    String operation,
    Future<T> Function(_CacheIoTiming? timing) work,
  ) {
    if (onIoSample == null && !_traceTimeline) {
      final result = _tail.then((_) => work(null));
      _tail = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
      return result;
    }

    final elapsed = Stopwatch()..start();
    final task = _traceTimeline
        ? (developer.TimelineTask()..start('Hikari.Cache.$operation'))
        : null;
    final result = _tail.then((_) async {
      final queueWaitMicros = elapsed.elapsedMicroseconds;
      final timing = _CacheIoTiming();
      try {
        return await work(timing);
      } catch (_) {
        timing.failed = true;
        rethrow;
      } finally {
        final sample = CacheIoSample(
          operation: operation,
          queueWaitMicros: queueWaitMicros,
          executionMicros: elapsed.elapsedMicroseconds - queueWaitMicros,
          hashMicros: timing.hashMicros,
          trimMicros: timing.trimMicros,
          bytes: timing.bytes,
          hit: timing.hit,
          failed: timing.failed,
        );
        // Instrumentation is never allowed to break reader/cache semantics.
        try {
          onIoSample?.call(sample);
        } catch (_) {}
        try {
          task?.finish(arguments: sample.toTimelineArguments());
        } catch (_) {}
      }
    });
    _tail = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return result;
  }

  String _digest(Uint8List bytes, _CacheIoTiming? timing) {
    if (timing == null) return sha256.convert(bytes).toString();
    final elapsed = Stopwatch()..start();
    try {
      return sha256.convert(bytes).toString();
    } finally {
      timing.hashMicros += elapsed.elapsedMicroseconds;
    }
  }

  Future<void> _ensureReady() =>
      _initializing ??= _initialize().catchError((Object error) {
        _initializing = null;
        throw error;
      });

  Future<void> _initialize() async {
    if (_closed) throw StateError('Cache is closed');
    try {
      final baseDir = await _cacheBaseDirectory();
      final root = Directory(p.join(baseDir.path, 'hikari', 'cache-v1'));
      root.createSync(recursive: true);
      _root = root;

      final database = await _openDatabaseWithRecovery(root);
      _database = database;
      final namespaces = await _reconcile(database, root);
      for (final namespace in namespaces) {
        await _trim(database, namespace, _byteBudgets[namespace] ?? 0);
      }
    } catch (_) {
      _acceptWrites = false;
      rethrow;
    }
  }

  Future<CacheDatabase> _openDatabaseWithRecovery(Directory root) async {
    try {
      return await _openValidatedDatabase(root);
    } catch (_) {
      if (root.existsSync()) {
        root.deleteSync(recursive: true);
      }
      root.createSync(recursive: true);
      return _openValidatedDatabase(root);
    }
  }

  Future<CacheDatabase> _openValidatedDatabase(Directory root) async {
    final database = await _openDatabase(root.path);
    try {
      await (database.select(database.cacheEntries)..limit(1)).get();
      return database;
    } catch (_) {
      try {
        await database.close();
      } catch (_) {
        // Initialization recovery is best effort; the original failure wins.
      }
      rethrow;
    }
  }

  Future<Set<String>> _reconcile(CacheDatabase database, Directory root) async {
    final indexedBlobs = <String>{};
    final namespaces = <String>{};
    final entries = await database.select(database.cacheEntries).get();
    for (final entry in entries) {
      final blobName = _blobName(entry.keyHash, entry.digest);
      if (await File(p.join(root.path, blobName)).exists()) {
        indexedBlobs.add(blobName);
        namespaces.add(entry.namespace);
      } else {
        await _deleteMetadataEntry(database, entry);
      }
    }

    for (final entity in root.listSync(followLinks: false)) {
      if (entity is! File) continue;
      final name = p.basename(entity.path);
      if (entity.path.endsWith('.tmp') ||
          (entity.path.endsWith('.blob') && !indexedBlobs.contains(name))) {
        try {
          entity.deleteSync();
        } catch (_) {}
      }
    }
    return namespaces;
  }

  String _keyHash(String namespace, String key) =>
      sha256.convert(utf8.encode(jsonEncode([1, namespace, key]))).toString();
  String _blobName(String keyHash, String digest) => '$keyHash-$digest.blob';
  String _blobPath(String keyHash, String digest) =>
      p.join(_root!.path, _blobName(keyHash, digest));

  Future<void> _deleteMetadataEntry(
    CacheDatabase database,
    CacheEntry row,
  ) async {
    await (database.delete(database.cacheEntries)..where(
          (entry) =>
              entry.namespace.equals(row.namespace) &
              entry.keyHash.equals(row.keyHash),
        ))
        .go();
    _dirtyTouches.remove(row.keyHash);
  }

  Future<void> _deleteEntry(CacheDatabase database, CacheEntry row) async {
    try {
      await File(_blobPath(row.keyHash, row.digest)).delete();
    } on FileSystemException catch (error) {
      if (error.osError?.errorCode != 2) rethrow;
    }
    await _deleteMetadataEntry(database, row);
  }

  @override
  Future<Uint8List?> read(String namespace, String key) {
    if (_shutdownRequested) return Future.value(null);
    return _serialized('read', (timing) async {
      timing?.hit = false;
      if (_closed) return null;
      try {
        await _ensureReady();
        final database = _database!;
        final hash = _keyHash(namespace, key);
        final row =
            await (database.select(database.cacheEntries)..where(
                  (row) =>
                      row.namespace.equals(namespace) &
                      row.keyHash.equals(hash),
                ))
                .getSingleOrNull();
        if (row == null) return null;
        final file = File(_blobPath(row.keyHash, row.digest));
        if (!await file.exists()) {
          await _deleteMetadataEntry(database, row);
          return null;
        }
        final bytes = await file.readAsBytes();
        if (bytes.length != row.size || _digest(bytes, timing) != row.digest) {
          await _deleteEntry(database, row);
          return null;
        }
        _dirtyTouches[hash] = _clock();
        if (timing != null) {
          timing.hit = true;
          timing.bytes = bytes.length;
        }
        return bytes;
      } catch (_) {
        if (timing != null) timing.failed = true;
        _acceptWrites = false;
        return null;
      }
    });
  }

  @override
  Future<void> write(String namespace, String key, Uint8List bytes) {
    if (_shutdownRequested) return Future.value();
    return _serialized('write', (timing) async {
      timing?.bytes = bytes.length;
      final byteBudget = _byteBudgets[namespace] ?? 0;
      if (_closed ||
          !_acceptWrites ||
          bytes.isEmpty ||
          byteBudget <= 0 ||
          bytes.length > byteBudget) {
        return;
      }
      try {
        await _ensureReady();
        final database = _database!;
        await _flushTouches(database);
        final hash = _keyHash(namespace, key);
        final digest = _digest(bytes, timing);
        final finalFile = File(_blobPath(hash, digest));
        final old =
            await (database.select(database.cacheEntries)..where(
                  (row) =>
                      row.namespace.equals(namespace) &
                      row.keyHash.equals(hash),
                ))
                .getSingleOrNull();
        if (!await finalFile.exists()) {
          final temp = File('${finalFile.path}.${_clock()}.tmp');
          try {
            await temp.writeAsBytes(bytes, flush: true);
            if (_beforePublish != null) await _beforePublish();
            await temp.rename(finalFile.path);
          } finally {
            if (await temp.exists()) await temp.delete();
          }
        }
        if (_beforeIndexPublish != null) await _beforeIndexPublish();
        await database
            .into(database.cacheEntries)
            .insertOnConflictUpdate(
              CacheEntriesCompanion.insert(
                namespace: namespace,
                keyHash: hash,
                digest: digest,
                size: bytes.length,
                accessedAt: _clock(),
              ),
            );
        if (old != null && old.digest != digest) {
          try {
            if (_beforeReplacementCleanup != null) {
              await _beforeReplacementCleanup();
            }
            await File(_blobPath(old.keyHash, old.digest)).delete();
          } on FileSystemException catch (error) {
            if (error.osError?.errorCode != 2) {
              if (timing != null) timing.failed = true;
              _acceptWrites = false;
              return;
            }
          }
        }
        _dirtyTouches[hash] = _clock();
        await _trim(database, namespace, byteBudget, timing: timing);
      } catch (_) {
        if (timing != null) timing.failed = true;
        _acceptWrites = false;
      }
    });
  }

  Future<void> _flushTouches(CacheDatabase database) async {
    final touches = Map<String, int>.of(_dirtyTouches);
    for (final entry in touches.entries) {
      await (database.update(database.cacheEntries)
            ..where((row) => row.keyHash.equals(entry.key)))
          .write(CacheEntriesCompanion(accessedAt: Value(entry.value)));
      _dirtyTouches.remove(entry.key);
    }
  }

  Future<int> _namespaceSizeBytes(
    CacheDatabase database,
    String namespace,
  ) async {
    final result = await database
        .customSelect(
          'SELECT COALESCE(SUM(size), 0) AS total FROM cache_entries WHERE namespace = ?',
          variables: [Variable.withString(namespace)],
        )
        .getSingle();
    return result.read<int>('total');
  }

  Future<void> _trim(
    CacheDatabase database,
    String namespace,
    int byteBudget, {
    _CacheIoTiming? timing,
  }) async {
    final elapsed = timing == null ? null : (Stopwatch()..start());
    try {
      var total = await _namespaceSizeBytes(database, namespace);
      while (total > byteBudget) {
        final rows =
            await (database.select(database.cacheEntries)
                  ..where((row) => row.namespace.equals(namespace))
                  ..orderBy([(row) => OrderingTerm.asc(row.accessedAt)])
                  ..limit(32))
                .get();
        if (rows.isEmpty) return;
        for (final row in rows) {
          if (total <= byteBudget) break;
          await _deleteEntry(database, row);
          total -= row.size;
        }
      }
    } finally {
      if (timing != null) timing.trimMicros += elapsed!.elapsedMicroseconds;
    }
  }

  Future<void> remove(String namespace, String key) {
    if (_shutdownRequested) return Future.value();
    return _serialized('remove', (timing) async {
      if (_closed) return;
      try {
        await _ensureReady();
        final hash = _keyHash(namespace, key);
        final row =
            await (_database!.select(_database!.cacheEntries)..where(
                  (entry) =>
                      entry.namespace.equals(namespace) &
                      entry.keyHash.equals(hash),
                ))
                .getSingleOrNull();
        if (row != null) await _deleteEntry(_database!, row);
        _dirtyTouches.remove(hash);
      } catch (_) {
        _acceptWrites = false;
      }
    });
  }

  Future<void> _clearNamespace(CacheDatabase database, String namespace) async {
    while (true) {
      final rows =
          await (database.select(database.cacheEntries)
                ..where((row) => row.namespace.equals(namespace))
                ..limit(32))
              .get();
      if (rows.isEmpty) break;
      for (final row in rows) {
        await _deleteEntry(database, row);
      }
    }
  }

  @override
  Future<Map<String, int>> sizes(Iterable<String> namespaces) {
    final requested = namespaces.toSet();
    if (_shutdownRequested) return Future.error(StateError('Cache is closed'));
    return _serialized('sizes', (timing) async {
      if (_closed) throw StateError('Cache is closed');
      try {
        await _ensureReady();
        return {
          for (final namespace in requested)
            namespace: await _namespaceSizeBytes(_database!, namespace),
        };
      } catch (_) {
        _acceptWrites = false;
        rethrow;
      }
    });
  }

  @override
  Future<void> clearNamespaces(Iterable<String> namespaces) {
    final requested = namespaces.toSet();
    if (_shutdownRequested) return Future.error(StateError('Cache is closed'));
    return _serialized('clearNamespaces', (timing) async {
      if (_closed) throw StateError('Cache is closed');
      try {
        await _ensureReady();
        // One queue operation: no cache read or write can interleave between
        // namespaces while an explicit user clear is in progress.
        for (final namespace in requested) {
          await _clearNamespace(_database!, namespace);
        }
      } catch (_) {
        _acceptWrites = false;
        rethrow;
      }
    });
  }

  Future<void> clear(String namespace) {
    if (_shutdownRequested) return Future.value();
    return _serialized('clear', (timing) async {
      if (_closed) return;
      try {
        await _ensureReady();
        await _clearNamespace(_database!, namespace);
      } catch (_) {
        _acceptWrites = false;
      }
    });
  }

  Future<int> sizeBytes(String namespace) {
    if (_shutdownRequested) return Future.value(0);
    return _serialized('sizeBytes', (timing) async {
      if (_closed) return 0;
      try {
        await _ensureReady();
        return await _namespaceSizeBytes(_database!, namespace);
      } catch (_) {
        _acceptWrites = false;
        return 0;
      }
    });
  }

  @override
  Future<void> close() {
    if (_closing case final closing?) return closing;
    if (_closed) return Future.value();
    _shutdownRequested = true;
    final closing = _serialized('close', (timing) async {
      final database = _database;
      if (database != null) {
        try {
          await _flushTouches(database);
        } catch (_) {
          // Cache metadata is reconstructible; close still releases the index.
        }
        await database.close();
        _database = null;
      }
      if (_isTestDefaultCache && _root != null) {
        try {
          if (_root!.existsSync()) {
            _root!.deleteSync(recursive: true);
          }
        } catch (_) {}
      }
      _closed = true;
    });
    return _closing = closing.then<void>(
      (_) {},
      onError: (Object error, StackTrace stackTrace) {
        _closing = null;
        Error.throwWithStackTrace(error, stackTrace);
      },
    );
  }
}
