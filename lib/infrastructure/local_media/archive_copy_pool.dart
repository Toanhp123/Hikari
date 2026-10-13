import 'dart:async';

/// Source-owned temporary archive copies. Owners and in-flight reads protect
/// files independently; failed deletes remain pending for shutdown retry.
final class ArchiveCopyPool {
  ArchiveCopyPool(
    this.materialize,
    this.delete, {
    Future<bool> Function(String)? exists,
  }) : exists = exists ?? _assumePresent;

  final Future<String> Function(String) materialize;
  final Future<void> Function(String) delete;
  final Future<bool> Function(String) exists;

  static Future<bool> _assumePresent(String _) async => true;
  final _entries = <String, _Copy>{};
  final _cleanups = <Future<void>>{};
  final _failedDeletes = <String>{};
  Future<void>? _closing;
  bool _closed = false;

  _Copy _entry(String locator) {
    if (_closed) throw StateError('Local archive source is closed.');
    return _entries.putIfAbsent(locator, _Copy.new);
  }

  void retain(String locator) => _entry(locator).owners++;

  Future<T> read<T>(String locator, Future<T> Function(String) action) async {
    final entry = _entry(locator);
    entry.readers++;
    try {
      return await action(await _availablePath(locator, entry));
    } finally {
      entry.readers--;
      // Cleanup must not replace a successful read or mask its original error.
      // Failed deletes are retained and retried on close.
      await _releaseUnused(locator, entry);
    }
  }

  Future<String> _availablePath(String locator, _Copy entry) async {
    var retriedMissing = false;
    while (true) {
      final pending = entry.path ??= Future.sync(() => materialize(locator));
      late final String path;
      try {
        path = await pending;
      } catch (_) {
        if (identical(entry.path, pending)) entry.path = null;
        rethrow;
      }
      if (await exists(path)) return path;
      // Android is allowed to evict cacheDir files even while the app runs.
      // Invalidate by identity so parallel reads join one replacement copy.
      if (identical(entry.path, pending)) entry.path = null;
      if (retriedMissing) {
        throw StateError('Materialized archive was removed from cache.');
      }
      retriedMissing = true;
    }
  }

  Future<void> release(String locator) async {
    final entry = _entries[locator];
    if (entry == null) return;
    if (entry.owners > 0) entry.owners--;
    await _releaseUnused(locator, entry);
  }

  Future<void> _releaseUnused(String locator, _Copy entry) {
    if (entry.owners != 0 || entry.readers != 0 || entry.releasing) {
      return Future<void>.value();
    }
    entry.releasing = true;
    if (identical(_entries[locator], entry)) _entries.remove(locator);
    final cleanup = _cleanup(entry);
    _cleanups.add(cleanup);
    // Handle both completion paths without creating an unobserved failing Future.
    cleanup.then(
      (_) => _cleanups.remove(cleanup),
      onError: (Object _, StackTrace _) => _cleanups.remove(cleanup),
    );
    return cleanup;
  }

  Future<void> _cleanup(_Copy entry) async {
    try {
      final pending = entry.path;
      if (pending != null) {
        try {
          await _delete(await pending);
        } catch (_) {
          // Materialization failure belongs to the original read. Failed
          // deletions have already been recorded for retry on shutdown.
        }
      }
    } finally {
      if (!entry.released.isCompleted) entry.released.complete();
    }
  }

  Future<void> _delete(String path) async {
    try {
      await delete(path);
      _failedDeletes.remove(path);
    } catch (_) {
      _failedDeletes.add(path);
      rethrow;
    }
  }

  Future<void> close() {
    _closed = true;
    return _closing ??= _close().whenComplete(() => _closing = null);
  }

  Future<void> _close() async {
    final entries = _entries.entries.toList();
    for (final entry in entries) {
      entry.value.owners = 0;
    }
    // Active reads can finish after shutdown begins. Their finalizers start
    // cleanup, which must be drained even after removal from _entries.
    await Future.wait([
      for (final entry in entries)
        () async {
          await _releaseUnused(entry.key, entry.value);
          await entry.value.released.future;
        }(),
    ]);
    while (_cleanups.isNotEmpty) {
      await Future.wait(_cleanups.toList());
    }
    for (final path in _failedDeletes.toList()) {
      try {
        await _delete(path);
      } catch (_) {
        // Retry remains possible on a later close (or native next startup).
      }
    }
    if (_failedDeletes.isNotEmpty) {
      throw StateError('Local archive cleanup failed; shutdown can retry.');
    }
  }
}

final class _Copy {
  Future<String>? path;
  int owners = 0;
  int readers = 0;
  bool releasing = false;
  final released = Completer<void>();
}
