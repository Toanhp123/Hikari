import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:drift/drift.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/infrastructure/cache/cache_database.dart';

const sourceArtworkNamespace = 'source-artwork-v1';
const sourceArtworkBudgetBytes = 64 * 1024 * 1024;

final class DiskByteCache implements ByteCache {
  DiskByteCache({
    Future<Directory> Function()? cacheDirectory,
    Map<String, int> namespaceBudgets = const {
      sourceArtworkNamespace: sourceArtworkBudgetBytes,
    },
    Future<CacheDatabase> Function(String rootPath)? openDatabase,
    int Function()? clock,
    this._beforePublish,
    this._beforeIndexPublish,
    this._beforeReplacementCleanup,
  }) : _cacheDirectory = cacheDirectory ?? getApplicationCacheDirectory,
       _budgets = Map.unmodifiable(namespaceBudgets),
       _openDatabase = openDatabase ?? _defaultDatabase,
       _clock = clock ?? _now;

  final Future<Directory> Function() _cacheDirectory;
  final Map<String, int> _budgets;
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
      p.join((await _cacheDirectory()).path, 'hikari', 'cache-v1'),
    );
    await root.create(recursive: true);
    final database = await _openDatabase(root.path);
    _root = root;
    _database = database;
    try {
      await database.customSelect('SELECT 1').get();
      final entries = await database.select(database.cacheEntries).get();
      final indexed = <String>{};
      final namespaces = <String>{};
      for (final entry in entries) {
        indexed.add(_blobName(entry.keyHash, entry.digest));
        namespaces.add(entry.namespace);
      }
      for (final namespace in namespaces) {
        await _trim(database, namespace, _budgets[namespace] ?? 0);
      }
      await for (final entity in root.list(followLinks: false)) {
        if (entity is File &&
            (entity.path.endsWith('.tmp') ||
                (entity.path.endsWith('.blob') &&
                    !indexed.contains(p.basename(entity.path))))) {
          await entity.delete();
        }
      }
    } catch (_) {
      _acceptWrites = false;
      rethrow;
    }
  }

  String _keyHash(String namespace, String key) =>
      sha256.convert(utf8.encode(jsonEncode([1, namespace, key]))).toString();
  String _blobName(String keyHash, String digest) => '$keyHash-$digest.blob';
  String _blobPath(String keyHash, String digest) =>
      p.join(_root!.path, _blobName(keyHash, digest));

  Future<void> _deleteEntry(CacheDatabase database, CacheEntry row) async {
    try {
      await File(_blobPath(row.keyHash, row.digest)).delete();
    } on FileSystemException catch (error) {
      if (error.osError?.errorCode != 2) rethrow;
    }
    await (database.delete(database.cacheEntries)..where(
          (entry) =>
              entry.namespace.equals(row.namespace) &
              entry.keyHash.equals(row.keyHash),
        ))
        .go();
    _dirtyTouches.remove(row.keyHash);
  }

  @override
  Future<Uint8List?> read(String namespace, String key) =>
      _serialized(() async {
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
            await _deleteEntry(database, row);
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

  @override
  Future<void> write(String namespace, String key, Uint8List bytes) =>
      _serialized(() async {
        final budget = _budgets[namespace] ?? 0;
        if (_closed ||
            !_acceptWrites ||
            bytes.isEmpty ||
            budget <= 0 ||
            bytes.length > budget) {
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
          await _trim(database, namespace, budget);
        } catch (_) {
          _acceptWrites = false;
        }
      });

  Future<void> _flushTouches(CacheDatabase database) async {
    final touches = Map<String, int>.of(_dirtyTouches);
    for (final entry in touches.entries) {
      await (database.update(database.cacheEntries)
            ..where((row) => row.keyHash.equals(entry.key)))
          .write(CacheEntriesCompanion(accessedAt: Value(entry.value)));
      _dirtyTouches.remove(entry.key);
    }
  }

  Future<int> _namespaceSize(CacheDatabase database, String namespace) async {
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
    int budget,
  ) async {
    var total = await _namespaceSize(database, namespace);
    while (total > budget) {
      final rows =
          await (database.select(database.cacheEntries)
                ..where((row) => row.namespace.equals(namespace))
                ..orderBy([(row) => OrderingTerm.asc(row.accessedAt)])
                ..limit(32))
              .get();
      if (rows.isEmpty) return;
      for (final row in rows) {
        if (total <= budget) break;
        await _deleteEntry(database, row);
        total -= row.size;
      }
    }
  }

  Future<void> remove(String namespace, String key) => _serialized(() async {
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

  Future<void> clear(String namespace) => _serialized(() async {
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

  Future<int> size(String namespace) => _serialized(() async {
    if (_closed) return 0;
    await _ensureReady();
    return _namespaceSize(_database!, namespace);
  });

  @override
  Future<void> close() {
    if (_closing case final closing?) return closing;
    if (_closed) return Future.value();
    _closed = true;
    return _closing = _serialized(() async {
      try {
        if (_database != null) {
          try {
            await _flushTouches(_database!);
          } finally {
            await _database!.close();
          }
        }
      } finally {
        _database = null;
      }
    });
  }
}
