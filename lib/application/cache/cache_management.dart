import 'package:hikari/core/cache/cache_maintenance.dart';
import 'package:hikari/core/cache/cache_usage.dart';

/// Management of explicitly registered, reconstructible cache namespaces.
///
/// The native HTTP cache, decoded images, SAF documents, local archive copies
/// and all durable user state are intentionally outside this capability.
final class CacheManagement {
  CacheManagement(this._cache, {required Map<String, String> namespaces})
    : _namespaces = Map.unmodifiable(namespaces) {
    if (_namespaces.entries.any(
      (entry) => entry.key.trim().isEmpty || entry.value.trim().isEmpty,
    )) {
      throw ArgumentError.value(
        namespaces,
        'namespaces',
        'Empty cache ID or label',
      );
    }
    if (_namespaces.values.toSet().length != _namespaces.length) {
      throw ArgumentError.value(
        namespaces,
        'namespaces',
        'Duplicate cache labels',
      );
    }
  }

  final CacheMaintenance _cache;
  final Map<String, String> _namespaces;

  Future<CacheUsage> usage() async {
    final sizes = await _cache.sizes(_namespaces.keys);
    return CacheUsage(
      Map.unmodifiable({
        for (final category in _namespaces.entries)
          category.value: sizes[category.key] ?? 0,
      }),
    );
  }

  Future<void> clear() => _cache.clearNamespaces(_namespaces.keys);
}
