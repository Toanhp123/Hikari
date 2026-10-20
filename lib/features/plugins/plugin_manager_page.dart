import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/apk_actions_page.dart';
import 'package:hikari/features/plugins/novel_actions_page.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';
import 'package:hikari/features/plugins/plugin_manager_view_model.dart';
import 'package:hikari/features/plugins/widgets/plugin_browse_filters.dart';
import 'package:hikari/features/plugins/widgets/plugin_details_dialog.dart';
import 'package:hikari/features/plugins/widgets/plugin_repository_section.dart';

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
    final selected = await showDialog<RepositoryId>(
      context: context,
      builder: (_) => PluginDetailsDialog(
        id: id,
        owner: manager.state.owners[id],
        repositories: manager.state.repositories,
        details: _viewModel.detailsFor(id),
        name: _viewModel.nameFor(id),
        runtime: _viewModel.runtimeFor(id),
        busy: _busy,
        onOpenApkActions: session.apkActions == null
            ? null
            : () => _openApkActions(pluginId: id),
        onOpenNovelActions: session.novelActions == null
            ? null
            : () => _openNovelActions(id),
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

  Future<void> _openApkActions({PluginId? pluginId}) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        builder: (_) => ApkActionsPage(management: session, pluginId: pluginId),
      ),
    );
    if (mounted) await _viewModel.refresh();
  }

  Future<void> _openNovelActions(PluginId id) async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        builder: (_) => NovelActionsPage(management: session, pluginId: id),
      ),
    );
    if (mounted) await _viewModel.refresh();
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
                                    : () => _openApkActions(),
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
                          PluginBrowseFilters(
                            state: _viewModel.state,
                            onQueryChanged: _viewModel.setQuery,
                            onLanguageChanged: _viewModel.setLanguage,
                            onEcosystemChanged: _viewModel.setEcosystem,
                            onTabChanged: _viewModel.setTab,
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
                    child: PluginRepositorySection(
                      repositories: manager.state.repositories,
                      busy: _busy,
                      onRefresh: _viewModel.refreshRepository,
                      onRemove: _removeRepository,
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
