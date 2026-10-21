import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/core/ui/components/hikari_choice_menu.dart';
import 'package:hikari/core/ui/components/hikari_confirmation_dialog.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/core/ui/components/hikari_refresh_action.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/apk_actions_page.dart';
import 'package:hikari/features/plugins/novel_actions_page.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';
import 'package:hikari/features/plugins/plugin_manager_view_model.dart';
import 'package:hikari/features/plugins/widgets/plugin_browse_filters.dart';
import 'package:hikari/features/plugins/widgets/plugin_details_dialog.dart';
import 'package:hikari/features/plugins/widgets/plugin_list_tile.dart';
import 'package:hikari/features/plugins/widgets/plugin_notice.dart';
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
        final confirmed = await showHikariConfirmation(
          context: context,
          title: 'Change repository owner?',
          content: Column(
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
          confirmLabel: 'Change owner',
        );
        if (!confirmed || !mounted) return;
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
    final formKey = GlobalKey<FormState>();
    var url = '';
    PluginEcosystem? ecosystem;
    final result = await showDialog<({String url, PluginEcosystem ecosystem})>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Add repository'),
        content: SingleChildScrollView(
          child: Form(
            key: formKey,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                FormField<PluginEcosystem>(
                  autovalidateMode: AutovalidateMode.onUserInteraction,
                  validator: (value) =>
                      value == null ? 'Choose an ecosystem.' : null,
                  onSaved: (value) => ecosystem = value,
                  builder: (field) => HikariChoiceMenu<PluginEcosystem>(
                    label: 'Ecosystem',
                    value: field.value,
                    options: [
                      for (final value in PluginEcosystem.values)
                        HikariChoiceOption(
                          value: value,
                          label: pluginEcosystemLabel(value),
                        ),
                    ],
                    expanded: true,
                    errorText: field.errorText,
                    onChanged: field.didChange,
                  ),
                ),
                const SizedBox(height: HikariSpacing.md),
                TextFormField(
                  decoration: const InputDecoration(
                    labelText: 'HTTPS repository URL',
                  ),
                  autovalidateMode: AutovalidateMode.onUserInteraction,
                  onSaved: (value) => url = value ?? '',
                  validator: (value) {
                    try {
                      RepositoryId(value ?? '');
                      return null;
                    } catch (_) {
                      return 'Enter a valid public HTTPS repository URL.';
                    }
                  },
                  keyboardType: TextInputType.url,
                ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () {
              if (!formKey.currentState!.validate()) return;
              formKey.currentState!.save();
              Navigator.pop(context, (
                url: RepositoryId(url).toString(),
                ecosystem: ecosystem!,
              ));
            },
            child: const Text('Add'),
          ),
        ],
      ),
    );
    if (result != null && mounted) {
      await _viewModel.addRepository(result.url, result.ecosystem);
    }
  }

  Future<void> _removeRepository(PluginRepository repository) async {
    final confirmed = await showHikariConfirmation(
      context: context,
      title: 'Remove repository?',
      content: const Text(
        'Installed plugins, selected ownership and consent remain. No fallback repository will be selected.',
      ),
      confirmLabel: 'Remove repository',
      destructive: true,
    );
    if (confirmed && mounted) {
      await _viewModel.removeRepository(repository.id);
    }
  }

  @override
  Widget build(BuildContext context) {
    final manager = session.manager;
    final listing = _viewModel.listing;
    final names = listing.names;
    final rows = listing.rows;
    final showRefreshAction = session.supported && context.isExpanded;
    final scrollView = CustomScrollView(
      physics: const AlwaysScrollableScrollPhysics(),
      slivers: [
        SliverPadding(
          padding: const EdgeInsets.all(HikariSpacing.lg),
          sliver: SliverToBoxAdapter(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (!session.supported)
                  const PluginNotice(
                    message: 'Plugin management and extension runtimes are available on Android only. Your saved sources and reading data are unchanged.',
                  )
                else ...[
                  if (_viewModel.error ?? session.error
                      case final String error) ...[
                    const SizedBox(height: HikariSpacing.md),
                    PluginNotice(message: error, error: true),
                  ],
                  if (session.apkEvidenceError != null) ...[
                    const SizedBox(height: HikariSpacing.sm),
                    PluginNotice(
                      message: session.apkEvidenceError!,
                      error: true,
                    ),
                  ],
                  if (session.novelEvidenceError != null) ...[
                    const SizedBox(height: HikariSpacing.sm),
                    PluginNotice(
                      message: session.novelEvidenceError!,
                      error: true,
                    ),
                  ],
                  if (session.novelRuntime?.failure != null &&
                      manager != null) ...[
                    const SizedBox(height: HikariSpacing.sm),
                    PluginNotice(
                      message:
                          'Novel runtime: ${session.novelRuntime!.failure}; restart required.',
                    ),
                  ],
                  if (manager != null) ...[
                    PluginBrowseFilters(
                      state: _viewModel.state,
                      onQueryChanged: _viewModel.setQuery,
                      onLanguageChanged: _viewModel.setLanguage,
                      onEcosystemChanged: _viewModel.setEcosystem,
                      onTabChanged: _viewModel.setTab,
                    ),
                    if (rows.isNotEmpty) ...[
                      const SizedBox(height: HikariSpacing.sm),
                      Text(
                        '${rows.length} ${rows.length == 1 ? 'plugin' : 'plugins'}',
                        style: Theme.of(context).textTheme.labelMedium
                            ?.copyWith(
                              color: Theme.of(context)
                                  .colorScheme
                                  .onSurfaceVariant,
                            ),
                      ),
                    ],
                  ],
                ],
              ],
            ),
          ),
        ),
        if (session.supported && manager == null && _busy)
          const SliverFillRemaining(
            hasScrollBody: false,
            child: Center(
              child: CircularProgressIndicator.adaptive(
                semanticsLabel: 'Loading plugins',
              ),
            ),
          ),
        if (manager != null)
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
            sliver: SliverList.builder(
              itemCount: rows.length,
              itemBuilder: (context, index) {
                final id = rows[index];
                return Padding(
                  padding: const EdgeInsets.only(bottom: HikariSpacing.sm),
                  child: PluginListTile(
                    id: id,
                    name: names[id] ?? id.upstreamId,
                    runtime: _viewModel.runtimeFor(id),
                    onTap: _busy ? null : () => _details(id),
                  ),
                );
              },
            ),
          ),
        if (session.supported && manager != null)
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(
              HikariSpacing.lg,
              HikariSpacing.xl,
              HikariSpacing.lg,
              HikariSpacing.xxl,
            ),
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
    );
    return HikariScaffold(
      appBar: AppBar(
        title: const Text('Plugins'),
        actions: [
          if (session.supported && manager != null)
            HikariIconButton(
              tooltip: 'Add repository',
              onPressed: _busy ? null : _addRepository,
              icon: const Icon(Icons.add_rounded),
            ),
          if (session.supported && session.apkActions != null)
            HikariIconButton(
              tooltip: 'APK operations',
              onPressed: _busy ? null : () => _openApkActions(),
              icon: const Icon(Icons.extension_outlined),
            ),
          if (showRefreshAction)
            HikariRefreshAction(
              tooltip: 'Refresh inventory',
              refreshing: _busy,
              onPressed: _busy ? null : _refresh,
            ),
        ],
      ),
      body: Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: session.supported
              ? RefreshIndicator.adaptive(
                  onRefresh: _refresh,
                  child: scrollView,
                )
              : scrollView,
        ),
      ),
    );
  }
}
