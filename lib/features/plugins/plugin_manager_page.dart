import 'package:flutter/material.dart';
import 'package:hikari/core/ui/layout/hikari_content_region.dart';
import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/dialog/hikari_confirmation_dialog.dart';
import 'package:hikari/core/ui/surfaces/dialog/hikari_dialog.dart';
import 'package:hikari/core/ui/controls/hikari_icon_button.dart';
import 'package:hikari/core/ui/components/hikari_refresh_action.dart';
import 'package:hikari/core/ui/layout/hikari_page_scaffold.dart';
import 'package:hikari/features/plugins/widgets/plugin_repository_dialog.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/apk_actions_page.dart';
import 'package:hikari/features/plugins/novel_actions_page.dart';
import 'package:hikari/features/plugins/plugin_manager_view_model.dart';
import 'package:hikari/features/plugins/widgets/plugin_browse_filters.dart';
import 'package:hikari/features/plugins/widgets/plugin_details_dialog.dart';
import 'package:hikari/features/plugins/widgets/plugin_list_tile.dart';
import 'package:hikari/core/ui/feedback/hikari_notice_banner.dart';
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
  bool _interactionOpen = false;
  bool _actionRouteOpen = false;
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
    if (_interactionOpen || _busy) return;
    final model = _viewModel;
    final manager = session.manager!;
    final previous = manager.state.owners[id];
    final details = model.detailsFor(id);
    bool current() =>
        mounted &&
        identical(model, _viewModel) &&
        identical(manager, session.manager) &&
        !_busy &&
        manager.state.owners[id] == previous;
    _interactionOpen = true;
    try {
      final result = await showHikariDialog<PluginDetailsChoice>(
        context: context,
        useRootNavigator: true,
        contentBuilder: (_, controller) => PluginDetailsDialog(
          controller: controller,
          id: id,
          owner: previous,
          repositories: manager.state.repositories,
          details: details,
          name: model.nameFor(id),
          runtime: model.runtimeFor(id),
          canOpenApkActions: session.apkActions != null,
          canOpenNovelActions: session.novelActions != null,
        ),
      );
      if (!current() || result is! HikariCompleted<PluginDetailsChoice>) return;
      switch (result.value) {
        case PluginApkChoice():
          if (session.apkActions != null) await _openApkActions(pluginId: id);
        case PluginNovelChoice():
          if (session.novelActions != null) await _openNovelActions(id);
        case PluginOwnerChoice(:final repositoryId):
          final offer = details.offers
              .where((offer) => offer.repositoryId == repositoryId)
              .firstOrNull;
          bool eligible() =>
              current() &&
              offer != null &&
              model
                  .detailsFor(id)
                  .offers
                  .any((value) => identical(value, offer)) &&
              manager.state.repositories.any(
                (value) => value.id == repositoryId,
              );
          if (!eligible()) return;
          if (previous != null && previous != repositoryId) {
            if (!mounted) return;
            final confirmed = await showHikariConfirmationDialog(
              context: context,
              useRootNavigator: true,
              title: 'Change repository owner?',
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SelectableText('Current: $previous'),
                  SelectableText('New: $repositoryId'),
                  const SizedBox(height: HikariSpacing.md),
                  const Text(
                    'This controls future update ownership only. Signing, code and origin consent do not transfer. Running sources are not replaced.',
                  ),
                ],
              ),
              confirmLabel: 'Change owner',
            );
            if (confirmed is! HikariCompleted<bool> ||
                confirmed.value != true ||
                !eligible()) {
              return;
            }
          }
          await model.selectOwner(id, repositoryId);
      }
    } finally {
      _interactionOpen = false;
    }
  }

  Future<void> _openApkActions({PluginId? pluginId}) async {
    if (_actionRouteOpen || _busy || session.apkActions == null) return;
    final management = session;
    final model = _viewModel;
    _actionRouteOpen = true;
    try {
      await Navigator.of(context).push<void>(
        MaterialPageRoute<void>(
          builder: (_) =>
              ApkActionsPage(management: management, pluginId: pluginId),
        ),
      );
      if (mounted && identical(model, _viewModel)) await model.refresh();
    } finally {
      _actionRouteOpen = false;
    }
  }

  Future<void> _openNovelActions(PluginId id) async {
    if (_actionRouteOpen || _busy || session.novelActions == null) return;
    final management = session;
    final model = _viewModel;
    _actionRouteOpen = true;
    try {
      await Navigator.of(context).push<void>(
        MaterialPageRoute<void>(
          builder: (_) =>
              NovelActionsPage(management: management, pluginId: id),
        ),
      );
      if (mounted && identical(model, _viewModel)) await model.refresh();
    } finally {
      _actionRouteOpen = false;
    }
  }

  Future<void> _addRepository() async {
    if (_interactionOpen || _busy) return;
    final model = _viewModel;
    _interactionOpen = true;
    try {
      final result = await showHikariDialog<PluginRepositoryInput>(
        context: context,
        useRootNavigator: true,
        contentBuilder: (_, controller) =>
            PluginRepositoryDialog(controller: controller),
      );
      if (!mounted || !identical(model, _viewModel) || _busy) return;
      if (result case HikariCompleted<PluginRepositoryInput>(:final value)) {
        await model.addRepository(value.url, value.ecosystem);
      }
    } finally {
      _interactionOpen = false;
    }
  }

  Future<void> _removeRepository(PluginRepository repository) async {
    if (_interactionOpen || _busy) return;
    final model = _viewModel;
    _interactionOpen = true;
    try {
      final confirmed = await showHikariConfirmationDialog(
        context: context,
        useRootNavigator: true,
        title: 'Remove repository?',
        content: const Text(
          'Installed plugins, selected ownership and consent remain. No fallback repository will be selected.',
        ),
        confirmLabel: 'Remove repository',
        destructive: true,
      );
      if (mounted &&
          identical(model, _viewModel) &&
          !_busy &&
          confirmed is HikariCompleted<bool> &&
          confirmed.value == true &&
          session.manager!.state.repositories.any(
            (value) => identical(value, repository),
          )) {
        await model.removeRepository(repository.id);
      }
    } finally {
      _interactionOpen = false;
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
          padding: const EdgeInsets.symmetric(vertical: HikariSpacing.lg),
          sliver: SliverToBoxAdapter(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (!session.supported)
                  const HikariNoticeBanner(
                    message: 'Plugin management and extension runtimes are available on Android only. Your saved sources and reading data are unchanged.',
                  )
                else ...[
                  if (_viewModel.error ?? session.error
                      case final String error) ...[
                    const SizedBox(height: HikariSpacing.md),
                    HikariNoticeBanner(
                      message: error,
                      tone: HikariNoticeTone.error,
                    ),
                  ],
                  if (session.apkEvidenceError != null) ...[
                    const SizedBox(height: HikariSpacing.sm),
                    HikariNoticeBanner(
                      message: session.apkEvidenceError!,
                      tone: HikariNoticeTone.error,
                    ),
                  ],
                  if (session.novelEvidenceError != null) ...[
                    const SizedBox(height: HikariSpacing.sm),
                    HikariNoticeBanner(
                      message: session.novelEvidenceError!,
                      tone: HikariNoticeTone.error,
                    ),
                  ],
                  if (session.novelRuntime?.failure != null &&
                      manager != null) ...[
                    const SizedBox(height: HikariSpacing.sm),
                    HikariNoticeBanner(
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
            padding: EdgeInsets.zero,
            sliver: SliverList.builder(
              itemCount: rows.length,
              itemBuilder: (context, index) {
                final id = rows[index];
                return Padding(
                  key: ValueKey(id),
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
              0,
              HikariSpacing.xl,
              0,
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
    return HikariPageScaffold(
      showAmbientGlow: true,
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
      body: HikariContentRegion(
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
