import 'package:flutter/foundation.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';
import 'package:hikari/features/plugins/plugin_manager_ui_state.dart';

typedef PluginDetails = ({
  List<PluginOffer> offers,
  ApkPluginIdentity? apk,
  NovelArtifactInventory? novel,
  RunningApkRevision? runningApk,
  LoadedNovelRevision? runningNovel,
});

const Object _unchanged = Object();

/// Repository browsing and commands. Consent dialogs remain in the view;
/// native mutation authority remains in the application layer.
final class PluginManagerViewModel extends ChangeNotifier {
  PluginManagerViewModel(this.management) {
    _state = _buildState(
      busy: true,
      error: null,
      query: '',
      language: null,
      ecosystem: null,
      tab: 0,
    );
  }

  final PluginManagement management;
  late PluginManagerUiState _state;
  PluginManagerUiState get state => _state;
  bool get busy => _state.busy;
  String? get error => _state.error;
  String? get language => _state.language;
  PluginEcosystem? get ecosystem => _state.ecosystem;
  int get tab => _state.tab;
  PluginListing get listing => _state.listing;

  bool _started = false;
  bool _disposed = false;

  void setQuery(String value) => _publish(query: value);
  void setLanguage(String? value) => _publish(language: value);
  void setEcosystem(PluginEcosystem? value) => _publish(ecosystem: value);
  void setTab(int value) => _publish(tab: value);

  /// Rebuilds the snapshot after an action route returns, using the native
  /// inventory readers rather than assuming its previously displayed rows.
  Future<void> refresh() => run(management.refresh);

  Future<void> addRepository(String url, PluginEcosystem ecosystem) =>
      run(() => management.manager!.addRepository(url, ecosystem));

  Future<void> removeRepository(RepositoryId id) =>
      run(() => management.manager!.removeRepository(id));

  Future<void> refreshRepository(RepositoryId id) =>
      run(() => management.manager!.refresh(id));

  Future<void> selectOwner(PluginId id, RepositoryId owner) =>
      run(() => management.manager!.selectOwner(id, owner));

  Future<void> run(Future<void> Function() action) async {
    if (_disposed || (busy && _started)) return;
    _started = true;
    _publish(busy: true, error: null);
    try {
      await action();
    } catch (_) {
      _publish(error: 'Could not save plugin changes. Retry.');
    } finally {
      _publish(busy: false);
    }
  }

  void _publish({
    Object? busy = _unchanged,
    Object? error = _unchanged,
    Object? query = _unchanged,
    Object? language = _unchanged,
    Object? ecosystem = _unchanged,
    Object? tab = _unchanged,
  }) {
    if (_disposed) return;
    final previous = _state;
    _state = _buildState(
      busy: identical(busy, _unchanged) ? previous.busy : busy as bool,
      error: identical(error, _unchanged) ? previous.error : error as String?,
      query: identical(query, _unchanged) ? previous.query : query as String,
      language: identical(language, _unchanged)
          ? previous.language
          : language as String?,
      ecosystem: identical(ecosystem, _unchanged)
          ? previous.ecosystem
          : ecosystem as PluginEcosystem?,
      tab: identical(tab, _unchanged) ? previous.tab : tab as int,
    );
    notifyListeners();
  }

  PluginManagerUiState _buildState({
    required bool busy,
    required String? error,
    required String query,
    required String? language,
    required PluginEcosystem? ecosystem,
    required int tab,
  }) {
    final manager = management.manager;
    final catalog = <PluginId, List<PluginOffer>>{};
    final names = <PluginId, String>{};
    for (final repository
        in manager?.state.repositories ?? <PluginRepository>[]) {
      for (final offer in repository.snapshot?.offers ?? <PluginOffer>[]) {
        (catalog[offer.id] ??= []).add(offer);
        names.putIfAbsent(offer.id, () => offer.name);
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
    // Languages describe repository offers, not every installed APK. Installed
    // revisions may have no matching repository; never hide them by guessing.
    // Scope options to the active view and ecosystem, but not to the search
    // query so typing a query cannot unexpectedly clear the selected facet.
    final languageSet = <String>{};
    if (manager != null && tab != 1) {
      final facetIds = tab == 0
          ? catalog.keys
          : manager.updateCandidates.map((offer) => offer.id);
      for (final id in facetIds) {
        if (ecosystem != null && id.ecosystem != ecosystem) continue;
        for (final offer in catalog[id] ?? const <PluginOffer>[]) {
          languageSet.addAll(offer.sources.map((source) => source.language));
        }
      }
    }
    final languages = languageSet.toList()..sort();
    // Normalize filters on input/evidence changes, never in a build getter.
    final effectiveLanguage = languageSet.contains(language) ? language : null;
    return PluginManagerUiState(
      busy: busy,
      error: error,
      query: query,
      language: effectiveLanguage,
      ecosystem: ecosystem,
      tab: tab,
      listing: PluginListing(
        names: names,
        languages: languages,
        rows: manager == null
            ? <PluginId>[]
            : _visibleRows(
                catalog,
                names,
                query,
                effectiveLanguage,
                ecosystem,
                tab,
              ),
      ),
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

  List<PluginId> _visibleRows(
    Map<PluginId, List<PluginOffer>> catalog,
    Map<PluginId, String> names,
    String query,
    String? language,
    PluginEcosystem? ecosystem,
    int tab,
  ) {
    final manager = management.manager!;
    final ids = <PluginId>{};
    if (tab == 0) {
      ids.addAll(catalog.keys);
    } else if (tab == 1) {
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
      return (ecosystem == null || id.ecosystem == ecosystem) &&
          searchable.contains(query.toLowerCase()) &&
          (language == null ||
              offers.any((o) => o.sources.any((s) => s.language == language)));
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
