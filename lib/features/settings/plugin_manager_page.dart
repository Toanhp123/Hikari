import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/settings/apk_actions_page.dart';
import 'package:hikari/features/settings/novel_actions_page.dart';
import 'package:hikari/features/settings/plugin_labels.dart';
import 'package:hikari/features/settings/plugin_manager_view_model.dart';

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
  late PluginManagerViewModel _viewModel;
  PluginManagement get session => widget.management;
  bool get _busy => _viewModel.busy;
  Future<void> _refresh() => _viewModel.refresh();

  @override
  void initState() {
    super.initState();
    _attachModel();
  }

  void _attachModel() {
    _viewModel = PluginManagerViewModel(session)..addListener(_rebuild);
    _refresh();
  }

  void _rebuild() {
    if (mounted) setState(() {});
  }

  @override
  void didUpdateWidget(covariant PluginManagerPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.management, widget.management)) {
      _viewModel.removeListener(_rebuild);
      _viewModel.dispose();
      _attachModel();
    }
  }

  @override
  void dispose() {
    _viewModel.removeListener(_rebuild);
    _viewModel.dispose();
    super.dispose();
  }

  Future<void> _details(PluginId id) async {
    final manager = session.manager!;
    final details = _viewModel.detailsFor(id);
    final offers = details.offers;
    final apk = details.apk;
    final novel = details.novel;
    final runningApk = details.runningApk;
    final runningNovel = details.runningNovel;
    final selected = await showDialog<RepositoryId>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_viewModel.nameFor(id)),
        content: SizedBox(
          width: 480,
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                SelectableText(
                  '${pluginEcosystemLabel(id.ecosystem)} · ${id.upstreamId}',
                ),
                Text('Owner: ${manager.state.owners[id] ?? 'Not selected'}'),
                Text('Runtime: ${_viewModel.runtimeFor(id)}'),
                if (apk != null) ...[
                  Text('Installed: ${_version(apk.version)}'),
                  Text('Native trust: ${apkTrustLabel(apk.trust)}'),
                  Text(
                    'Compatibility: ${apkCompatibilityLabel(apk.compatibility)}',
                  ),
                  SelectableText(
                    'Current signers: ${apk.currentSigners.join(', ')}',
                  ),
                ],
                if (novel != null) ...[
                  Text(
                    'Installed: ${_version(novel.current.metadata.version)} · ${novelStatusLabel(novel.status)}',
                  ),
                  Text(
                    'Admission: ${novelAdmissionLabel(novel.admission)}; provider compatibility unknown',
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
                          'Offer compatibility: ${pluginCompatibilityLabel(offer.compatibility)}',
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
      await _viewModel.selectOwner(id, selected);
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
                        (e) => DropdownMenuItem(
                          value: e,
                          child: Text(pluginEcosystemLabel(e)),
                        ),
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
      await _viewModel.addRepository(result.url, result.ecosystem);
    }
  }

  Future<void> _removeRepository(PluginRepository repository) async {
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
      await _viewModel.removeRepository(repository.id);
    }
  }

  @override
  Widget build(BuildContext context) {
    final manager = session.manager;
    final listing = _viewModel.listing;
    final names = listing.names;
    final languages = listing.languages;
    final rows = listing.rows;
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
                        if (_viewModel.error ?? session.error
                            case final String error)
                          Semantics(
                            liveRegion: true,
                            child: Text(
                              error,
                              style: TextStyle(
                                color: Theme.of(context).colorScheme.error,
                              ),
                            ),
                          ),
                        if (session.apkEvidenceError != null)
                          Text(session.apkEvidenceError!),
                        if (session.novelEvidenceError != null)
                          Text(session.novelEvidenceError!),
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
                                  selected: _viewModel.tab == index,
                                  onSelected: (_) => _viewModel.setTab(index),
                                ),
                            ],
                          ),
                          TextField(
                            decoration: const InputDecoration(
                              labelText: 'Search plugins or sources',
                            ),
                            onChanged: _viewModel.setQuery,
                          ),
                          DropdownButtonFormField<PluginEcosystem>(
                            initialValue: _viewModel.ecosystem,
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
                                  child: Text(pluginEcosystemLabel(e)),
                                ),
                              ),
                            ],
                            onChanged: _viewModel.setEcosystem,
                          ),
                          DropdownButtonFormField<String>(
                            key: ValueKey((
                              languages.join(String.fromCharCode(0)),
                              _viewModel.language,
                            )),
                            initialValue: _viewModel.language,
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
                            onChanged: _viewModel.setLanguage,
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
                          '${pluginEcosystemLabel(id.ecosystem)} · ${id.upstreamId}\n${_viewModel.runtimeFor(id)}',
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
                                    '${pluginEcosystemLabel(repository.ecosystem)} · ${repository.snapshot?.name ?? 'Not refreshed'}',
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
                                            : () =>
                                                  _viewModel.refreshRepository(
                                                    repository.id,
                                                  ),
                                        child: const Text('Refresh repository'),
                                      ),
                                      TextButton(
                                        onPressed: _busy
                                            ? null
                                            : () =>
                                                  _removeRepository(repository),
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
