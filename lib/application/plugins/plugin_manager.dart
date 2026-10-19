import 'package:hikari/domain/plugins/plugins.dart';

/// Repository/inventory policy only. No installation or mutable source registry.
final class PluginManager {
  PluginManager._(this._store, this._reader, this._inventory, this._state);
  final PluginStateStore _store;
  final PluginRepositoryReader _reader;
  final PluginInventory _inventory;
  PluginState _state;
  Future<void> _tail = Future.value();
  PluginState get state => _state;
  bool _inventoryFresh = false;

  /// False means persisted inventory may be stale; native actions still require
  /// their independent identity/consent checks, and updates cannot be inferred.
  bool get inventoryFresh => _inventoryFresh;

  static Future<PluginManager> open({
    required PluginStateStore store,
    required PluginRepositoryReader reader,
    required PluginInventory inventory,
    bool allowUnavailableInventory = false,
  }) async {
    final loaded = await store.load();
    final manager = PluginManager._(store, reader, inventory, loaded);
    // The app's management session may be opened for repository browsing when
    // one native family is unavailable. Do not persist or claim an incomplete
    // inventory; guard update candidates until successful reconciliation.
    List<InstalledPlugin> installed;
    try {
      installed = await inventory.readInstalled();
    } catch (_) {
      if (!allowUnavailableInventory) rethrow;
      // Mark historical Dart operations interrupted in memory only. Do not
      // persist this incomplete native inventory or infer any native outcome.
      manager._state = PluginState(
        repositories: loaded.repositories,
        installed: loaded.installed,
        owners: loaded.owners,
        approvals: loaded.approvals,
        operations: loaded.operations.map(
          (operation) => PendingPluginOperation(
            operation.id,
            operation.operation,
            PluginOperationStatus.interrupted,
          ),
        ),
      );
      return manager;
    }
    // Reconcile actual inventory; interrupted native work is never replayed.
    await manager._publish(
      installed: installed,
      operations: loaded.operations.map(
        (o) => PendingPluginOperation(
          o.id,
          o.operation,
          PluginOperationStatus.interrupted,
        ),
      ),
    );
    manager._inventoryFresh = true;
    return manager;
  }

  // ponytail: one app-owned queue; per-repository queues only if measured refresh latency warrants it.
  Future<T> _serial<T>(Future<T> Function() action) {
    final next = _tail.then((_) => action());
    _tail = next.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return next;
  }

  Future<void> _publish({
    Iterable<PluginRepository>? repositories,
    Iterable<InstalledPlugin>? installed,
    Map<PluginId, RepositoryId>? owners,
    Iterable<PluginApproval>? approvals,
    Iterable<PendingPluginOperation>? operations,
  }) async {
    final next = PluginState(
      repositories: repositories ?? state.repositories,
      installed: installed ?? state.installed,
      owners: owners ?? state.owners,
      approvals: approvals ?? state.approvals,
      operations: operations ?? state.operations,
    );
    await _store.save(next);
    _state = next;
  }

  Future<PluginRepository> addRepository(
    String url,
    PluginEcosystem ecosystem,
  ) => _serial(() async {
    final repository = PluginRepository(RepositoryId(url), ecosystem);
    if (state.repositories.any((r) => r.id == repository.id)) {
      throw StateError('Repository already added');
    }
    await _publish(repositories: [...state.repositories, repository]);
    return repository;
  });

  Future<void> removeRepository(RepositoryId id) => _serial(() async {
    _repository(id);
    // Ownership, consent and installed packages survive removal. No fallback owner.
    await _publish(repositories: state.repositories.where((r) => r.id != id));
  });

  Future<void> refresh(RepositoryId id) => _serial(() async {
    final old = _repository(id);
    PluginRepository next;
    try {
      next = PluginRepository(
        id,
        old.ecosystem,
        snapshot: await _reader.read(old),
      );
    } catch (_) {
      // Do not persist exception strings: transport errors may contain credentials.
      next = PluginRepository(
        id,
        old.ecosystem,
        snapshot: old.snapshot,
        refreshError: 'Repository refresh failed',
      );
    }
    await _publish(
      repositories: state.repositories.map((r) => r.id == id ? next : r),
    );
  });

  PluginRepository _repository(RepositoryId id) =>
      state.repositories.firstWhere(
        (r) => r.id == id,
        orElse: () => throw StateError('Repository not found'),
      );

  List<PluginOffer> offersFor(PluginId id) => List.unmodifiable(
    state.repositories
        .expand((r) => r.snapshot?.offers ?? <PluginOffer>[])
        .where((o) => o.id == id),
  );

  Future<void> selectOwner(PluginId id, RepositoryId repositoryId) =>
      _serial(() async {
        if (!offersFor(id).any((o) => o.repositoryId == repositoryId)) {
          throw StateError('No offer from selected repository');
        }
        await _publish(owners: {...state.owners, id: repositoryId});
      });

  Future<void> approve(PluginApproval approval) => _serial(() async {
    if (state.owners[approval.id] != approval.repositoryId) {
      throw StateError('Consent requires explicit selected owner');
    }
    final repo = _repository(approval.repositoryId);
    if (!offersFor(approval.id).any((o) => o.repositoryId == repo.id)) {
      throw StateError('Offer not found');
    }
    if (approval.kind == PluginApprovalKind.signer) {
      throw StateError('APK signer approval requires native inspection');
    }
    await _publish(
      approvals: [
        ...state.approvals.where((a) => a.id != approval.id),
        approval,
      ],
    );
  });

  /// Candidate discovery is independent from permission to execute downloaded code.
  List<PluginOffer> get updateCandidates {
    if (!_inventoryFresh) return const [];
    final installed = {for (final row in state.installed) row.id: row};
    return List.unmodifiable([
      for (final repository in state.repositories)
        for (final offer in repository.snapshot?.offers ?? <PluginOffer>[])
          if (offer.repositoryId == state.owners[offer.id])
            if (installed[offer.id] case final current?)
              if (offer.version.compareTo(current.version) > 0) offer,
    ]);
  }

  Future<void> reconcileInstalled() => _serial(() async {
    // Failure leaves persisted installed state untouched and marks derived
    // update suggestions unavailable rather than promoting stale evidence.
    _inventoryFresh = false;
    final installed = await _inventory.readInstalled();
    await _publish(installed: installed);
    _inventoryFresh = true;
  });
}
