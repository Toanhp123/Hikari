import 'dart:async';

/// Source-owned temporary copies. Reads hold a lease even if their reader exits.
final class ArchiveCopyPool {
  ArchiveCopyPool(this.materialize, this.delete);

  final Future<String> Function(String) materialize;
  final Future<void> Function(String) delete;
  final _entries = <String, _Copy>{};
  bool _closed = false;
  final _failedDeletes = <String>{};

  _Copy _entry(String locator) {
    if (_closed) throw StateError('Local archive source is closed.');
    return _entries.putIfAbsent(locator, _Copy.new);
  }

  void retain(String locator) => _entry(locator).owners++;

  Future<T> read<T>(String locator, Future<T> Function(String) action) async {
    final entry = _entry(locator);
    entry.readers++;
    final pending = entry.path ??= Future.sync(() => materialize(locator));
    try {
      late final String path;
      try {
        path = await pending;
      } catch (_) {
        if (identical(entry.path, pending)) entry.path = null;
        rethrow;
      }
      return await action(path);
    } finally {
      entry.readers--;
      await _releaseUnused(locator, entry);
    }
  }

  Future<void> release(String locator) async {
    final entry = _entries[locator];
    if (entry == null) return;
    if (entry.owners > 0) entry.owners--;
    await _releaseUnused(locator, entry);
  }

  Future<void> _releaseUnused(String locator, _Copy entry) async {
    if (entry.owners != 0 || entry.readers != 0 || entry.releasing) return;
    entry.releasing = true;
    if (identical(_entries[locator], entry)) _entries.remove(locator);
    final path = entry.path;
    try {
      if (path != null) await _delete(await path);
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

  Future<void> close() async {
    _closed = true;
    final entries = _entries.entries.toList();
    final retry = _failedDeletes.toList();
    for (final entry in entries) {
      entry.value.owners = 0;
    }
    await Future.wait([
      for (final path in retry) _delete(path),
      for (final entry in entries)
        () async {
          await _releaseUnused(entry.key, entry.value);
          await entry.value.released.future;
        }(),
    ]);
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
