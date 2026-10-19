import 'package:flutter/foundation.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/settings/plugin_labels.dart';

/// Immutable, derived browsing data. No native operations or widget context.
final class PluginListing {
  PluginListing({
    required Map<PluginId, String> names,
    required List<String> languages,
    required List<PluginId> rows,
  }) : names = Map.unmodifiable(names),
       languages = List.unmodifiable(languages),
       rows = List.unmodifiable(rows);

  final Map<PluginId, String> names;
  final List<String> languages;
  final List<PluginId> rows;
}

typedef PluginDetails = ({
  List<PluginOffer> offers,
  ApkPluginIdentity? apk,
  NovelArtifactInventory? novel,
  RunningApkRevision? runningApk,
  LoadedNovelRevision? runningNovel,
});

/// Repository browsing and commands. Consent dialogs remain in the view;
/// native mutation authority remains in the application layer.
final class PluginManagerViewModel extends ChangeNotifier {
  PluginManagerViewModel(this.management);
  final PluginManagement management;
  bool _busy = true;
  bool get busy => _busy;
  bool _started = false;
  bool _disposed = false;
  String? _error;
  String? get error => _error;
  String _query = '';
  String? _language;
  String? get language => _language;
  PluginEcosystem? _ecosystem;
  PluginEcosystem? get ecosystem => _ecosystem;
  int _tab = 0;
  int get tab => _tab;

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  void setQuery(String value) {
    _query = value;
    _notify();
  }

  void setLanguage(String? value) {
    _language = value;
    _notify();
  }

  void setEcosystem(PluginEcosystem? value) {
    _ecosystem = value;
    _notify();
  }

  void setTab(int value) {
    _tab = value;
    _notify();
  }

  Future<void> refresh() => run(management.refresh);

  Future<void> addRepository(String url, PluginEcosystem ecosystem) =>
      run(() async {
        await management.manager!.addRepository(url, ecosystem);
      });

  Future<void> removeRepository(RepositoryId id) =>
      run(() => management.manager!.removeRepository(id));

  Future<void> refreshRepository(RepositoryId id) =>
      run(() => management.manager!.refresh(id));

  Future<void> selectOwner(PluginId id, RepositoryId owner) =>
      run(() => management.manager!.selectOwner(id, owner));

  Future<void> run(Future<void> Function() action) async {
    if (_disposed || (_busy && _started)) return;
    _started = true;
    _busy = true;
    _error = null;
    _notify();
    try {
      await action();
    } catch (_) {
      _error = 'Could not save plugin changes. Retry.';
    } finally {
      _busy = false;
      _notify();
    }
  }

  PluginListing get listing {
    final manager = management.manager;
    final catalog = <PluginId, List<PluginOffer>>{};
    final names = <PluginId, String>{};
    final languageSet = <String>{};
    for (final repository
        in manager?.state.repositories ?? <PluginRepository>[]) {
      for (final offer in repository.snapshot?.offers ?? <PluginOffer>[]) {
        (catalog[offer.id] ??= []).add(offer);
        names.putIfAbsent(offer.id, () => offer.name);
        languageSet.addAll(offer.sources.map((source) => source.language));
      }
    }
    for (final row in management.novels) {
      names.putIfAbsent(
        row.current.metadata.id,
        () => row.current.metadata.name,
      );
    }
    for (final row
        in management.novelRuntime?.loaded ?? <LoadedNovelRevision>[]) {
      names.putIfAbsent(
        PluginId(PluginEcosystem.novel, row.id),
        () => row.name,
      );
    }
    // A selected language may disappear after repository removal/refresh.
    if (_language != null && !languageSet.contains(_language)) {
      _language = null;
    }
    final languages = languageSet.toList()..sort();
    return PluginListing(
      names: names,
      languages: languages,
      rows: manager == null ? <PluginId>[] : visibleRows(catalog, names),
    );
  }

  PluginDetails detailsFor(PluginId id) {
    final offers = management.manager!.offersFor(id);
    return (
      offers: offers,
      apk: management.apks.where((row) => row.id == id).firstOrNull,
      novel: management.novels
          .where((row) => row.current.metadata.id == id)
          .firstOrNull,
      runningApk: management.apkRuntime?.loaded
          .where((row) => row.id == id)
          .firstOrNull,
      runningNovel: management.novelRuntime?.loaded
          .where(
            (row) =>
                id.ecosystem == PluginEcosystem.novel &&
                row.id == id.upstreamId,
          )
          .firstOrNull,
    );
  }

  String nameFor(PluginId id) {
    final manager = management.manager!;
    return manager.offersFor(id).firstOrNull?.name ??
        management.novels
            .where((n) => n.current.metadata.id == id)
            .firstOrNull
            ?.current
            .metadata
            .name ??
        management.novelRuntime?.loaded
            .where(
              (n) =>
                  n.id == id.upstreamId &&
                  id.ecosystem == PluginEcosystem.novel,
            )
            .firstOrNull
            ?.name ??
        id.upstreamId;
  }

  String runtimeFor(PluginId id) {
    if (id.ecosystem == PluginEcosystem.anime) {
      return 'Runtime deferred (runtimeDeferred)';
    }
    if (id.ecosystem == PluginEcosystem.novel) {
      final evidence = management.novelRuntime;
      final current = management.novels
          .where((n) => n.current.metadata.id == id)
          .firstOrNull;
      final loaded = evidence?.loaded
          .where((n) => n.id == id.upstreamId)
          .firstOrNull;
      if (loaded != null) {
        if (current == null) {
          return loaded.artifact == null
              ? 'Loaded packaged source'
              : 'Loaded removed revision; restart required';
        }
        return evidence!.loadedCurrent(current.current)
            ? 'Current revision loaded; compatibility not proven'
            : 'Different revision loaded; restart required';
      }
      final issue = evidence?.issues
          .where((n) => n.artifact.metadata.id == id)
          .firstOrNull;
      return issue?.reason ??
          'Not loaded (${evidence == null ? 'unavailable' : novelRuntimeInitializationLabel(evidence.initialization)})';
    }
    final running = management.apkRuntime?.loaded
        .where((n) => n.id == id)
        .firstOrNull;
    final installed = management.apks.where((n) => n.id == id).firstOrNull;
    if (running != null) {
      if (installed == null) return 'Loaded removed revision; restart required';
      return installed.version.code == running.versionCode &&
              installed.currentSigners.length == 1 &&
              installed.currentSigners.single == running.signer
          ? 'Current revision loaded'
          : 'Different revision loaded; restart required';
    }
    return management.apkRuntime?.errors[id.upstreamId] ??
        (management.apkRuntime?.initialized == true
            ? 'Not loaded'
            : 'Runtime not initialized');
  }

  List<PluginId> visibleRows(
    Map<PluginId, List<PluginOffer>> catalog,
    Map<PluginId, String> names,
  ) {
    final manager = management.manager!;
    final ids = <PluginId>{};
    if (_tab == 0) {
      ids.addAll(catalog.keys);
    } else if (_tab == 1) {
      ids.addAll(management.apks.map((a) => a.id));
      ids.addAll(management.novels.map((n) => n.current.metadata.id));
      ids.addAll(
        management.apkRuntime?.loaded.map((r) => r.id) ?? <PluginId>[],
      );
      ids.addAll(
        management.novelRuntime?.loaded.map(
              (r) => PluginId(PluginEcosystem.novel, r.id),
            ) ??
            <PluginId>[],
      );
    } else {
      ids.addAll(manager.updateCandidates.map((offer) => offer.id));
    }
    return ids.where((id) {
      final offers = catalog[id] ?? const <PluginOffer>[];
      final searchable = [
        names[id] ?? id.upstreamId,
        id.upstreamId,
        ...offers.expand((o) => o.sources.map((s) => s.name)),
      ].join(' ').toLowerCase();
      return (_ecosystem == null || id.ecosystem == _ecosystem) &&
          searchable.contains(_query.toLowerCase()) &&
          (_language == null ||
              offers.any((o) => o.sources.any((s) => s.language == _language)));
    }).toList()..sort(
      (a, b) => (names[a] ?? a.upstreamId).compareTo(names[b] ?? b.upstreamId),
    );
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
