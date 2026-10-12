/// Optional maintenance capability for reconstructible cache entries.
///
/// This is deliberately separate from ByteCache: callers that only read or
/// write bytes need not support management, and no durable user data belongs
/// to this contract.
abstract interface class CacheMaintenance {
  /// Indexed byte sizes for the requested namespaces, in one consistent read.
  Future<Map<String, int>> sizes(Iterable<String> namespaces);

  /// Evict only the requested namespaces, serialized with cache reads/writes.
  ///
  /// Failures must propagate: callers must not report success after partial
  /// deletion. New reads/downloads may populate the cache after this returns.
  Future<void> clearNamespaces(Iterable<String> namespaces);
}
