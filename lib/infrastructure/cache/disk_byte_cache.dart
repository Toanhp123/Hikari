import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:drift/drift.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/infrastructure/cache/cache_database.dart';

final class DiskByteCache implements ByteCache {
  DiskByteCache({
    Future<Directory> Function()? cacheBaseDirectory,
    Map<String, int> namespaceByteBudgets = const {},
    Future<CacheDatabase> Function(String rootPath)? openDatabase,
    int Function()? clock,
    this._beforePublish,
    this._beforeIndexPublish,
    this._beforeReplacementCleanup,
  }) : _cacheBaseDirectory = cacheBaseDirectory ?? getApplicationCacheDirectory,
       _byteBudgets = Map.unmodifiable(namespaceByteBudgets),
       _openDatabase = openDatabase ?? _defaultDatabase,
       _clock = clock ?? _now;

  final Future<Directory> Function() _cacheBaseDirectory;
  final Map<String, int> _byteBudgets;
  final Future<CacheDatabase> Function(String rootPath) _openDatabase;
  final int Function() _clock;
  final Future<void> Function()? _beforePublish;
  final Future<void> Function()? _beforeIndexPublish;
  final Future<void> Function()? _beforeReplacementCleanup;
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

  static Future<CacheDatabase> _defaultDatabase(String rootPath) async =>
      CacheDatabase.at(p.join(rootPath, 'index.sqlite'));

  Future<T> _serialized<T>(Future<T> Function() operation) {
    final result = _tail.then((_) => operation());
    _tail = result.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return result;
  }

  Future<void> _ensureReady() => _initializing ??= _initialize();

  Future<void> _initialize() async {
    if (_closed) throw StateError('Cache is closed');
    final root = Directory(
      p.join((await _cacheBaseDirectory()).path, 'hikari', 'cache-v1'),
    );
    await root.create(recursive: true);
    _root = root;

    try {
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
      if (await root.exists()) {
        await root.delete(recursive: true);
      }
      await root.create(recursive: true);
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

    await for (final entity in root.list(followLinks: false)) {
      if (entity is! File) continue;
      final name = p.basename(entity.path);
      if (entity.path.endsWith('.tmp') ||
          (entity.path.endsWith('.blob') && !indexedBlobs.contains(name))) {
        await entity.delete();
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
    return _serialized(() async {
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
        if (bytes.length != row.size ||
            sha256.convert(bytes).toString() != row.digest) {
          await _deleteEntry(database, row);
          return null;
        }
        _dirtyTouches[hash] = _clock();
        return bytes;
      } catch (_) {
        _acceptWrites = false;
        return null;
      }
    });
  }

  @override
  Future<void> write(String namespace, String key, Uint8List bytes) {
    if (_shutdownRequested) return Future.value();
    return _serialized(() async {
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
        final digest = sha256.convert(bytes).toString();
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
              _acceptWrites = false;
              return;
            }
          }
        }
        _dirtyTouches[hash] = _clock();
        await _trim(database, namespace, byteBudget);
      } catch (_) {
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
    int byteBudget,
  ) async {
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
  }

  Future<void> remove(String namespace, String key) {
    if (_shutdownRequested) return Future.value();
    return _serialized(() async {
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

  Future<void> clear(String namespace) {
    if (_shutdownRequested) return Future.value();
    return _serialized(() async {
      if (_closed) return;
      try {
        await _ensureReady();
        while (true) {
          final rows =
              await (_database!.select(_database!.cacheEntries)
                    ..where((row) => row.namespace.equals(namespace))
                    ..limit(32))
                  .get();
          if (rows.isEmpty) break;
          for (final row in rows) {
            await _deleteEntry(_database!, row);
          }
        }
      } catch (_) {
        _acceptWrites = false;
      }
    });
  }

  Future<int> sizeBytes(String namespace) {
    if (_shutdownRequested) return Future.value(0);
    return _serialized(() async {
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
    final closing = _serialized(() async {
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
