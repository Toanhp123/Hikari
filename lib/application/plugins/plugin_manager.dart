import 'package:hikari/domain/plugins/plugins.dart';

/// Repository/inventory policy only. No installation or mutable source registry.
final class PluginManager {
  PluginManager._(
    this._store,
    this._reader,
    this._inventory,
    this._state,
    this._activated,
  );
  final PluginStateStore _store;
  final PluginRepositoryReader _reader;
  final PluginInventory _inventory;
  PluginState _state;
  final Map<PluginId, InstalledPlugin> _activated;
  Future<void> _tail = Future.value();
  PluginState get state => _state;

  static Future<PluginManager> open({
    required PluginStateStore store,
    required PluginRepositoryReader reader,
    required PluginInventory inventory,
    required Map<PluginId, InstalledPlugin> activatedAtStartup,
  }) async {
    final loaded = await store.load();
    final manager = PluginManager._(
      store,
      reader,
      inventory,
      loaded,
      Map.unmodifiable(activatedAtStartup),
    );
    // Reconcile actual inventory; interrupted native work is never replayed.
    await manager._publish(
      installed: await inventory.readInstalled(),
      operations: loaded.operations.map(
        (o) => PendingPluginOperation(
          o.id,
          o.operation,
          PluginOperationStatus.interrupted,
        ),
      ),
    );
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

  PluginApproval? approvalFor(PluginId id) {
    for (final approval in state.approvals) {
      if (approval.id == id) return approval;
    }
    return null;
  }

  Future<void> approve(PluginApproval approval) => _serial(() async {
    if (state.owners[approval.id] != approval.repositoryId) {
      throw StateError('Consent requires explicit selected owner');
    }
    final repo = _repository(approval.repositoryId);
    if (!offersFor(approval.id).any((o) => o.repositoryId == repo.id)) {
      throw StateError('Offer not found');
    }
    if (approval.kind == PluginApprovalKind.signer &&
        repo.snapshot?.signerFingerprint != approval.digest) {
      throw StateError('Signer differs from displayed repository signer');
    }
    await _publish(
      approvals: [
        ...state.approvals.where((a) => a.id != approval.id),
        approval,
      ],
    );
  });

  /// Candidate discovery is independent from permission to execute downloaded code.
  PluginOffer? updateCandidateFor(PluginId id) {
    final owner = state.owners[id];
    for (final offer in offersFor(id)) {
      if (offer.repositoryId != owner) continue;
      for (final installed in state.installed) {
        if (installed.id == id &&
            offer.version.compareTo(installed.version) > 0) {
          return offer;
        }
      }
    }
    return null;
  }

  InstalledPlugin? activeRevisionFor(PluginId id) => _activated[id];

  /// Only explicitly owned, signer-approved newer APK offers are actionable.
  /// Novel metadata has no digest: downloaded bytes need separate digest consent.
  PluginOffer? updateOfferFor(PluginId id) {
    final owner = state.owners[id];
    final approval = approvalFor(id);
    if (owner == null ||
        approval == null ||
        approval.repositoryId != owner ||
        id.ecosystem == PluginEcosystem.novel) {
      return null;
    }
    for (final repository in state.repositories) {
      if (repository.id != owner ||
          repository.snapshot?.signerFingerprint != approval.digest) {
        continue;
      }
      for (final offer in repository.snapshot!.offers) {
        if (offer.id != id) continue;
        for (final installed in state.installed) {
          if (installed.id == id &&
              installed.digest == approval.digest &&
              offer.version.compareTo(installed.version) > 0) {
            return offer;
          }
        }
      }
    }
    return null;
  }

  Future<void> reconcileInstalled() => _serial(() async {
    await _publish(installed: await _inventory.readInstalled());
  });

  PluginActivation activationFor(PluginId id) {
    for (final installed in state.installed) {
      if (installed.id != id) continue;
      if (id.ecosystem == PluginEcosystem.anime) {
        return PluginActivation.runtimeDeferred;
      }
      // Distinguish current inventory revision from startup-pinned running code.
      final active = _activated[id];
      if (active != null) {
        return active.digest == installed.digest &&
                active.version.compareTo(installed.version) == 0
            ? PluginActivation.active
            : PluginActivation.restartRequired;
      }
      if (installed.compatibility != PluginCompatibility.compatible) {
        return PluginActivation.incompatible;
      }
      final approval = approvalFor(id);
      if (approval == null || approval.digest != installed.digest) {
        return PluginActivation.unapproved;
      }
      return PluginActivation.restartRequired;
    }
    return PluginActivation.notInstalled;
  }
}
