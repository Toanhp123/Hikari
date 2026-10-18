import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/features/settings/apk_actions_page.dart';
import 'package:hikari/features/settings/novel_actions_page.dart';
import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/plugins/plugins.dart';

String _version(PluginVersion value) => switch (value) {
  ApkPluginVersion() => '${value.name} (${value.code})',
  NovelPluginVersion() => value.value,
};

/// Management browsing. Package actions use separate explicit native consent routes.
class PluginManagerPage extends StatefulWidget {
  const PluginManagerPage({super.key, required this.management});
  final PluginManagement management;
  @override
  State<PluginManagerPage> createState() => _PluginManagerPageState();
}

class _PluginManagerPageState extends State<PluginManagerPage> {
  bool _busy = true;
  String? _error;
  String _query = '';
  String? _language;
  PluginEcosystem? _ecosystem;
  int _tab = 0;
  PluginManagement get session => widget.management;
  @override
  void initState() {
    super.initState();
    _refresh();
  }

  Future<void> _refresh() => _run(session.refresh);
  Future<void> _run(Future<void> Function() action) async {
    if (mounted) {
      setState(() {
        _busy = true;
        _error = null;
      });
    }
    try {
      await action();
    } catch (_) {
      _error = 'Could not save plugin changes. Retry.';
    }
    if (mounted) setState(() => _busy = false);
  }

  String _name(PluginId id) {
    final manager = session.manager!;
    return manager.offersFor(id).firstOrNull?.name ??
        session.novels
            .where((n) => n.current.metadata.id == id)
            .firstOrNull
            ?.current
            .metadata
            .name ??
        session.novelRuntime?.loaded
            .where(
              (n) =>
                  n.id == id.upstreamId &&
                  id.ecosystem == PluginEcosystem.novel,
            )
            .firstOrNull
            ?.name ??
        id.upstreamId;
  }

  String _runtime(PluginId id) {
    if (id.ecosystem == PluginEcosystem.anime) {
      return 'Runtime deferred (runtimeDeferred)';
    }
    if (id.ecosystem == PluginEcosystem.novel) {
      final evidence = session.novelRuntime;
      final current = session.novels
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
          'Not loaded (${evidence?.initialization.name ?? 'unavailable'})';
    }
    final running = session.apkRuntime?.loaded
        .where((n) => n.id == id)
        .firstOrNull;
    final installed = session.apks.where((n) => n.id == id).firstOrNull;
    if (running != null) {
      if (installed == null) return 'Loaded removed revision; restart required';
      return installed.version.code == running.versionCode &&
              installed.currentSigners.length == 1 &&
              installed.currentSigners.single == running.signer
          ? 'Current revision loaded'
          : 'Different revision loaded; restart required';
    }
    return session.apkRuntime?.errors[id.upstreamId] ??
        (session.apkRuntime?.initialized == true
            ? 'Not loaded'
            : 'Runtime not initialized');
  }

  List<PluginId> _rows(
    Map<PluginId, List<PluginOffer>> catalog,
    Map<PluginId, String> names,
  ) {
    final manager = session.manager!;
    final installed = {for (final row in manager.state.installed) row.id: row};
    final ids = <PluginId>{};
    if (_tab == 0) {
      ids.addAll(catalog.keys);
    } else if (_tab == 1) {
      ids.addAll(session.apks.map((a) => a.id));
      ids.addAll(session.novels.map((n) => n.current.metadata.id));
      ids.addAll(session.apkRuntime?.loaded.map((r) => r.id) ?? <PluginId>[]);
      ids.addAll(
        session.novelRuntime?.loaded.map(
              (r) => PluginId(PluginEcosystem.novel, r.id),
            ) ??
            <PluginId>[],
      );
    } else {
      ids.addAll(
        manager.state.installed
            .map((i) => i.id)
            .where(
              (id) => (catalog[id] ?? const <PluginOffer>[]).any(
                (offer) =>
                    offer.repositoryId == manager.state.owners[id] &&
                    offer.version.compareTo(installed[id]!.version) > 0,
              ),
            ),
      );
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

  Future<void> _details(PluginId id) async {
    final manager = session.manager!;
    final offers = manager.offersFor(id);
    final apk = session.apks.where((a) => a.id == id).firstOrNull;
    final novel = session.novels
        .where((n) => n.current.metadata.id == id)
        .firstOrNull;
    final runningApk = session.apkRuntime?.loaded
        .where((a) => a.id == id)
        .firstOrNull;
    final runningNovel = session.novelRuntime?.loaded
        .where(
          (a) => id.ecosystem == PluginEcosystem.novel && a.id == id.upstreamId,
        )
        .firstOrNull;
    final selected = await showDialog<RepositoryId>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_name(id)),
        content: SizedBox(
          width: 480,
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                SelectableText('${id.ecosystem.name} · ${id.upstreamId}'),
                Text('Owner: ${manager.state.owners[id] ?? 'Not selected'}'),
                Text('Runtime: ${_runtime(id)}'),
                if (apk != null) ...[
                  Text('Installed: ${_version(apk.version)}'),
                  Text('Native trust: ${apk.trust.name}'),
                  Text('Compatibility: ${apk.compatibility.name}'),
                  SelectableText(
                    'Current signers: ${apk.currentSigners.join(', ')}',
                  ),
                ],
                if (novel != null) ...[
                  Text(
                    'Installed: ${_version(novel.current.metadata.version)} · ${novel.status.name}',
                  ),
                  Text(
                    'Admission: ${novel.admission.name}; provider compatibility unknown',
                  ),
                  SelectableText('Digest: ${novel.current.digest}'),
                  SelectableText(
                    'Repository: ${novel.current.metadata.repositoryId} · site: ${novel.current.metadata.site} · origins: ${novel.current.metadata.origins.join(', ')}',
                  ),
                  if (novel.previous != null)
                    Text(
                      'Previous: ${_version(novel.previous!.metadata.version)}',
                    ),
                ],
                if (runningApk != null)
                  SelectableText(
                    'Loaded: ${runningApk.versionCode} · ${runningApk.signer}',
                  ),
                if (runningNovel?.artifact != null) ...[
                  Text(
                    'Loaded: ${_version(runningNovel!.artifact!.metadata.version)}',
                  ),
                  SelectableText(
                    'Loaded name: ${runningNovel.artifact!.metadata.name} · digest: ${runningNovel.artifact!.digest}',
                  ),
                  SelectableText(
                    'Loaded repository: ${runningNovel.artifact!.metadata.repositoryId}',
                  ),
                  SelectableText(
                    'Loaded site: ${runningNovel.artifact!.metadata.site}',
                  ),
                  SelectableText(
                    'Loaded origins: ${runningNovel.artifact!.metadata.origins.join(', ')}',
                  ),
                ],
                for (final offer in offers)
                  Padding(
                    padding: const EdgeInsets.only(top: HikariSpacing.md),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SelectableText('Repository: ${offer.repositoryId}'),
                        if (id.ecosystem != PluginEcosystem.novel)
                          SelectableText(
                            'Repository signer expectation: ${manager.state.repositories.where((r) => r.id == offer.repositoryId).firstOrNull?.snapshot?.signerFingerprint ?? 'Not declared'}',
                          ),
                        Text('Available: ${_version(offer.version)}'),
                        Text(
                          'Offer compatibility: ${offer.compatibility.name}',
                        ),
                        for (final source in offer.sources)
                          Text(
                            '${source.name} · ${source.language} · ${source.upstreamId}',
                          ),
                        if (manager.state.owners[id] != offer.repositoryId)
                          OutlinedButton(
                            onPressed: _busy
                                ? null
                                : () => Navigator.pop(
                                    context,
                                    offer.repositoryId,
                                  ),
                            child: const Text('Select repository owner'),
                          ),
                        if (manager.state.owners[id] == offer.repositoryId)
                          const Text('Selected owner'),
                      ],
                    ),
                  ),
                if (id.ecosystem != PluginEcosystem.novel &&
                    session.apkActions != null)
                  OutlinedButton(
                    onPressed: () {
                      Navigator.pop(context);
                      Navigator.of(this.context).push(
                        MaterialPageRoute<void>(
                          builder: (_) =>
                              ApkActionsPage(management: session, pluginId: id),
                        ),
                      );
                    },
                    child: const Text('APK actions'),
                  ),
                if (id.ecosystem == PluginEcosystem.novel &&
                    session.novelActions != null)
                  OutlinedButton(
                    onPressed: () {
                      Navigator.pop(context);
                      Navigator.of(this.context).push(
                        MaterialPageRoute<void>(
                          builder: (_) => NovelActionsPage(
                            management: session,
                            pluginId: id,
                          ),
                        ),
                      );
                    },
                    child: const Text('Novel actions'),
                  ),
                const SizedBox(height: HikariSpacing.md),
                const Text(
                  'Repository metadata is not execution trust. Package actions require separate explicit consent. Existing readers keep their frozen sources.',
                ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
      ),
    );
    if (selected != null && mounted) {
      final previous = manager.state.owners[id];
      if (previous != null && previous != selected) {
        final confirmed = await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Change repository owner?'),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SelectableText('Current: $previous'),
                  SelectableText('New: $selected'),
                  const SizedBox(height: HikariSpacing.md),
                  const Text(
                    'This controls future update ownership only. Signing, code and origin consent do not transfer. Running sources are not replaced.',
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Change owner'),
              ),
            ],
          ),
        );
        if (confirmed != true || !mounted) return;
      }
      await _run(() => manager.selectOwner(id, selected));
    }
  }

  Future<void> _addRepository() async {
    var url = '';
    PluginEcosystem? ecosystem;
    String? error;
    final result = await showDialog<({String url, PluginEcosystem ecosystem})>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, update) => AlertDialog(
          title: const Text('Add repository'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                DropdownButtonFormField<PluginEcosystem>(
                  isExpanded: true,
                  decoration: const InputDecoration(labelText: 'Ecosystem'),
                  items: PluginEcosystem.values
                      .map(
                        (e) => DropdownMenuItem(value: e, child: Text(e.name)),
                      )
                      .toList(),
                  onChanged: (value) => update(() => ecosystem = value),
                ),
                const SizedBox(height: HikariSpacing.md),
                TextField(
                  onChanged: (value) => url = value,
                  decoration: InputDecoration(
                    labelText: 'HTTPS repository URL',
                    errorText: error,
                  ),
                  keyboardType: TextInputType.url,
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () {
                try {
                  if (ecosystem == null) throw const FormatException();
                  final id = RepositoryId(url);
                  Navigator.pop(context, (
                    url: id.toString(),
                    ecosystem: ecosystem!,
                  ));
                } catch (_) {
                  update(
                    () =>
                        error = 'Choose ecosystem and valid public HTTPS URL.',
                  );
                }
              },
              child: const Text('Add'),
            ),
          ],
        ),
      ),
    );
    if (result != null && mounted) {
      await _run(() async {
        await session.manager!.addRepository(result.url, result.ecosystem);
      });
    }
  }

  Future<void> _remove(PluginRepository repository) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Remove repository?'),
        content: const Text(
          'Installed plugins, selected ownership and consent remain. No fallback repository will be selected.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Remove repository'),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) {
      await _run(() => session.manager!.removeRepository(repository.id));
    }
  }

  @override
  Widget build(BuildContext context) {
    final manager = session.manager;
    // One catalog pass per build; filtering/sorting never rescan all offers per ID.
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
    for (final row in session.novels) {
      names.putIfAbsent(
        row.current.metadata.id,
        () => row.current.metadata.name,
      );
    }
    for (final row in session.novelRuntime?.loaded ?? <LoadedNovelRevision>[]) {
      names.putIfAbsent(
        PluginId(PluginEcosystem.novel, row.id),
        () => row.name,
      );
    }
    final languages = languageSet.toList()..sort();
    if (_language != null && !languageSet.contains(_language)) _language = null;
    final rows = manager == null ? <PluginId>[] : _rows(catalog, names);
    return HikariScaffold(
      appBar: AppBar(title: const Text('Plugins')),
      body: Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: CustomScrollView(
            slivers: [
              SliverPadding(
                padding: const EdgeInsets.all(HikariSpacing.lg),
                sliver: SliverToBoxAdapter(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      if (!session.supported)
                        const Text(
                          'Plugin management and extension runtimes are available on Android only. Your saved sources and reading data are unchanged.',
                        )
                      else ...[
                        Wrap(
                          spacing: HikariSpacing.sm,
                          runSpacing: HikariSpacing.sm,
                          children: [
                            OutlinedButton(
                              onPressed: _busy ? null : _refresh,
                              child: const Text('Refresh inventory'),
                            ),
                            if (session.apkActions != null)
                              OutlinedButton(
                                onPressed: _busy
                                    ? null
                                    : () => Navigator.push(
                                        context,
                                        MaterialPageRoute<void>(
                                          builder: (_) => ApkActionsPage(
                                            management: session,
                                          ),
                                        ),
                                      ),
                                child: const Text('APK operations'),
                              ),
                            if (manager != null)
                              FilledButton(
                                onPressed: _busy ? null : _addRepository,
                                child: const Text('Add repository'),
                              ),
                          ],
                        ),
                        if (_busy)
                          const LinearProgressIndicator(
                            semanticsLabel: 'Loading plugins',
                          ),
                        if (_error ?? session.error case final String error)
                          Semantics(
                            liveRegion: true,
                            child: Text(
                              error,
                              style: TextStyle(
                                color: Theme.of(context).colorScheme.error,
                              ),
                            ),
                          ),
                        if (manager != null) ...[
                          if (session.novelRuntime?.failure != null)
                            Text(
                              'Novel runtime: ${session.novelRuntime!.failure}; restart required.',
                            ),
                          const SizedBox(height: HikariSpacing.md),
                          Wrap(
                            spacing: HikariSpacing.sm,
                            children: [
                              for (final (index, label) in [
                                'Available',
                                'Installed',
                                'Updates',
                              ].indexed)
                                ChoiceChip(
                                  label: Text(label),
                                  selected: _tab == index,
                                  onSelected: (_) =>
                                      setState(() => _tab = index),
                                ),
                            ],
                          ),
                          TextField(
                            decoration: const InputDecoration(
                              labelText: 'Search plugins or sources',
                            ),
                            onChanged: (value) =>
                                setState(() => _query = value),
                          ),
                          DropdownButtonFormField<PluginEcosystem>(
                            initialValue: _ecosystem,
                            isExpanded: true,
                            decoration: const InputDecoration(
                              labelText: 'Ecosystem',
                            ),
                            items: [
                              const DropdownMenuItem<PluginEcosystem>(
                                value: null,
                                child: Text('All ecosystems'),
                              ),
                              ...PluginEcosystem.values.map(
                                (e) => DropdownMenuItem(
                                  value: e,
                                  child: Text(e.name),
                                ),
                              ),
                            ],
                            onChanged: (value) =>
                                setState(() => _ecosystem = value),
                          ),
                          DropdownButtonFormField<String>(
                            key: ValueKey((
                              languages.join(String.fromCharCode(0)),
                              _language,
                            )),
                            initialValue: _language,
                            isExpanded: true,
                            decoration: const InputDecoration(
                              labelText: 'Source language (when declared)',
                            ),
                            items: [
                              const DropdownMenuItem<String>(
                                value: null,
                                child: Text('All languages'),
                              ),
                              ...languages.map(
                                (l) =>
                                    DropdownMenuItem(value: l, child: Text(l)),
                              ),
                            ],
                            onChanged: (value) =>
                                setState(() => _language = value),
                          ),
                          const SizedBox(height: HikariSpacing.md),
                          if (rows.isEmpty)
                            const Text(
                              'No plugins match. Add or refresh a repository to discover offers.',
                            ),
                        ],
                      ],
                    ],
                  ),
                ),
              ),
              SliverPadding(
                padding: const EdgeInsets.symmetric(
                  horizontal: HikariSpacing.lg,
                ),
                sliver: SliverList.builder(
                  itemCount: rows.length,
                  itemBuilder: (context, index) {
                    final id = rows[index];
                    return Card(
                      child: ListTile(
                        title: Text(names[id] ?? id.upstreamId),
                        subtitle: Text(
                          '${id.ecosystem.name} · ${id.upstreamId}\n${_runtime(id)}',
                        ),
                        onTap: _busy ? null : () => _details(id),
                      ),
                    );
                  },
                ),
              ),
              if (session.supported && manager != null)
                SliverPadding(
                  padding: const EdgeInsets.all(HikariSpacing.lg),
                  sliver: SliverToBoxAdapter(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const SizedBox(height: HikariSpacing.lg),
                        Text(
                          'Repositories',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                        if (manager.state.repositories.isEmpty)
                          const Text(
                            'No repositories added. Nothing is downloaded automatically.',
                          ),
                        for (final repository in manager.state.repositories)
                          Card(
                            child: Padding(
                              padding: const EdgeInsets.all(HikariSpacing.md),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    '${repository.ecosystem.name} · ${repository.snapshot?.name ?? 'Not refreshed'}',
                                  ),
                                  SelectableText(repository.id.toString()),
                                  if (repository.refreshError != null)
                                    Text(
                                      '${repository.refreshError}. Last good offers retained.',
                                      style: TextStyle(
                                        color: Theme.of(context)
                                            .colorScheme
                                            .error,
                                      ),
                                    ),
                                  Wrap(
                                    spacing: HikariSpacing.sm,
                                    children: [
                                      TextButton(
                                        onPressed: _busy
                                            ? null
                                            : () => _run(
                                                () => manager.refresh(
                                                  repository.id,
                                                ),
                                              ),
                                        child: const Text('Refresh repository'),
                                      ),
                                      TextButton(
                                        onPressed: _busy
                                            ? null
                                            : () => _remove(repository),
                                        child: const Text('Remove repository'),
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
