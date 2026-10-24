import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/layout/hikari_content_region.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/dialog/hikari_confirmation_dialog.dart';
import 'package:hikari/core/ui/layout/hikari_page_scaffold.dart';
import 'package:hikari/core/ui/content/hikari_section_header.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/novel_actions_view_model.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';
import 'package:hikari/features/plugins/widgets/novel_artifact_details.dart';
import 'package:hikari/features/plugins/widgets/plugin_action_section.dart';
import 'package:hikari/core/ui/feedback/hikari_notice_banner.dart';

/// Session/package identity owns consent and staged-resource lifetime.
class NovelActionsPage extends StatefulWidget {
  const NovelActionsPage({
    super.key,
    required this.management,
    required this.pluginId,
  });

  final PluginManagement management;
  final PluginId pluginId;

  @override
  State<NovelActionsPage> createState() => _NovelActionsPageState();
}

class _NovelActionsPageState extends State<NovelActionsPage>
    with WidgetsBindingObserver {
  PluginManagement get session => widget.management;
  NovelPluginGateway get gateway => session.novelGateway!;
  PluginId get id => widget.pluginId;
  NovelArtifactInventory? get current =>
      session.novels.where((row) => row.current.metadata.id == id).firstOrNull;
  bool _foreground = true, _stageUsable = true;
  late NovelActionsViewModel _actionsViewModel;
  bool get _busy => _actionsViewModel.busy;
  String? get _error => _actionsViewModel.error;
  void _onViewModelChanged() {
    if (mounted) setState(() {});
  }

  int _epoch = 0;
  static int _sequence = 0;
  String? _request;
  StagedNovelArtifact? _stage;
  NovelArtifactInventory? _base;
  PluginOffer? _offer;
  RepositoryId? _owner;
  String _origins = '';
  bool get _active => mounted && _foreground;
  bool _boundary(int epoch) =>
      _active &&
      epoch == _epoch &&
      !session.novelMutationsLocked &&
      (ModalRoute.of(context)?.isCurrent ?? false);
  bool _sameRow(NovelArtifactInventory? a, NovelArtifactInventory? b) =>
      sameNovelArtifact(a?.current, b?.current) &&
      sameNovelArtifact(a?.previous, b?.previous) &&
      a?.status == b?.status &&
      a?.previousStatus == b?.previousStatus &&
      a?.admission == b?.admission &&
      a?.previousAdmission == b?.previousAdmission;
  bool _sameOwner(RepositoryId? owner) =>
      session.manager!.state.owners[id] == owner;
  bool _sameOffer() =>
      _sameOwner(_owner) &&
      session.manager!.offersFor(id).any((row) => identical(row, _offer));
  @override
  void initState() {
    super.initState();
    _actionsViewModel = NovelActionsViewModel()
      ..addListener(_onViewModelChanged);
    _foreground =
        WidgetsBinding.instance.lifecycleState == null ||
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
    WidgetsBinding.instance.addObserver(this);
    final owner = session.manager?.state.owners[id];
    final site = session.manager
        ?.offersFor(id)
        .where((row) => row.repositoryId == owner)
        .firstOrNull
        ?.siteUrl;
    _origins = site?.origin ?? '';
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    if (!_foreground) _epoch++;
    if (mounted) setState(() {});
  }

  @override
  void didUpdateWidget(covariant NovelActionsPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(widget.management, oldWidget.management) ||
        widget.pluginId != oldWidget.pluginId) {
      _epoch++;
      if (oldWidget.management.novelGateway != null) {
        unawaited(_cancel());
        final stage = _stage;
        if (stage != null) {
          unawaited(
            oldWidget.management.novelGateway!
                .discard(stage.token)
                .catchError((Object _) {}),
          );
        }
      }
      _stageUsable = true;
      _request = null;
      _stage = null;
      _base = null;
      _offer = null;
      _owner = null;
      final owner = session.manager?.state.owners[id];
      final site = session.manager
          ?.offersFor(id)
          .where((row) => row.repositoryId == owner)
          .firstOrNull
          ?.siteUrl;
      _origins = site?.origin ?? '';
      _actionsViewModel.removeListener(_onViewModelChanged);
      _actionsViewModel.dispose();
      _actionsViewModel = NovelActionsViewModel()
        ..addListener(_onViewModelChanged);
      if (mounted) setState(() {});
    }
  }

  Future<void> _run(Future<void> Function() action) =>
      _actionsViewModel.run(action, canStart: () => _active);

  Future<bool> _confirm(
    String title,
    List<Widget> contents,
    String action,
  ) async {
    final result = await showHikariConfirmationDialog(
      context: context,
      useRootNavigator: true,
      title: title,
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: contents,
      ),
      confirmLabel: action,
    );
    return result is HikariCompleted<bool> && result.value == true;
  }

  static const _evaluationWarning = Text(
    'Explicit consent permits native evaluation of this exact code and origin policy. Repository self-declaration is not trust. Bounded admission is not JS safety or provider compatibility. Running sources stay frozen; restart required for changed sources.',
  );
  Future<void> _fresh(
    int epoch,
    NovelArtifactInventory? displayed,
    RepositoryId? owner, {
    bool offer = false,
  }) async {
    final rows = await gateway.inventory();
    if (!session.novelMutationsLocked) session.replaceNovelInventory(rows);
    if (!_boundary(epoch) ||
        !_sameRow(current, displayed) ||
        !_sameOwner(owner) ||
        offer && !_sameOffer()) {
      throw StateError('Novel consent boundary changed');
    }
  }

  Future<void> _download() => _run(() async {
    if (session.novelMutationsLocked) return;
    _owner = session.manager!.state.owners[id];
    _offer = session.manager!
        .offersFor(id)
        .where((row) => row.repositoryId == _owner)
        .singleOrNull;
    final offer = _offer;
    if (offer == null ||
        offer.siteUrl == null ||
        offer.customJsUrl != null ||
        offer.customCssUrl != null) {
      throw StateError('Unsupported owner offer');
    }
    final metadata = NovelArtifactMetadata(
      id: id,
      name: offer.name,
      version: offer.version as NovelPluginVersion,
      repositoryId: offer.repositoryId,
      site: offer.siteUrl.toString(),
      origins: _origins
          .split(RegExp(r'[\s,]+'))
          .where((value) => value.isNotEmpty),
    );
    final epoch = _epoch;
    final rows = await gateway.inventory();
    if (!_boundary(epoch) || !_sameOffer()) return;
    session.replaceNovelInventory(rows);
    _base = current;
    final request =
        'novel-ui-${DateTime.now().microsecondsSinceEpoch}-${_sequence++}';
    setState(() => _request = request);
    try {
      final staged = await session.novelActions!.stage(
        id,
        origins: metadata.origins,
        requestId: request,
      );
      if (!_boundary(epoch) || _request != request || !_sameOffer()) {
        await gateway.discard(staged.token);
        return;
      }
      _stage = staged;
      _stageUsable = true;
    } finally {
      if (_request == request) _request = null;
    }
  });
  Future<void> _cancel() async {
    final request = _request;
    if (_active) {
      setState(() => _request = null);
    } else {
      _request = null;
    }
    if (request != null) {
      try {
        await gateway.cancel(request);
      } catch (_) {
        if (_active) {
          _actionsViewModel.showError(
            'Cancellation not confirmed. Late staged bytes will be discarded.',
          );
        }
      }
    }
  }

  Future<void> _discard() async {
    final stage = _stage;
    if (stage != null) {
      await gateway.discard(stage.token);
      _stage = null;
    }
  }

  Future<void> _install() => _run(() async {
    final stage = _stage!;
    final epoch = _epoch;
    if (!_stageUsable ||
        !await _confirm('Consent to exact Novel bundle?', [
          NovelArtifactDetails(artifact: stage.artifact),
          _evaluationWarning,
        ], 'Evaluate and install')) {
      return;
    }
    await _fresh(epoch, _base, _owner, offer: true);
    _stageUsable = false; // Native may consume/move stage before returning an ambiguous failure.
    try {
      await session.mutateNovel((actions) => actions.install(stage));
      _stage = null;
    } finally {
      if (!mounted) {
        try {
          await gateway.discard(stage.token);
        } catch (_) {
          /* Native may already consume stage. */
        }
      }
    }
  });
  Future<void> _currentAction(String action) => _run(() async {
    final displayed = current!;
    final owner = session.manager!.state.owners[id];
    final epoch = _epoch;
    final offers = session.manager!.offersFor(id);
    final contents = <Widget>[
      const Text('Current exact tuple'),
      NovelArtifactDetails(artifact: displayed.current),
      if (action == 'rollback') ...[
        const Text('Previous target exact tuple'),
        NovelArtifactDetails(artifact: displayed.previous!),
      ],
      if (action != 'uninstall')
        _evaluationWarning
      else
        const Text(
          'Library, Progress and references remain unchanged. Running loaded revision stays frozen until restart; loaded reader may fail after removal.',
        ),
    ];
    final label = switch (action) {
      'admit' => 'Evaluate current',
      'rollback' => 'Consent and rollback',
      _ => 'Remove stored bundle',
    };
    if (!await _confirm('$action Novel bundle?', contents, label)) return;
    await _fresh(epoch, displayed, owner);
    final freshOffers = session.manager!.offersFor(id);
    if (freshOffers.length != offers.length ||
        !offers.every(freshOffers.contains)) {
      throw StateError('Novel offer changed during consent');
    }
    await session.mutateNovel(
      (actions) => switch (action) {
        'admit' => actions.admit(displayed.current),
        'rollback' => actions.rollback(displayed),
        _ => actions.uninstall(displayed.current),
      },
    );
  });
  @override
  void dispose() {
    _epoch++;
    _foreground = false;
    WidgetsBinding.instance.removeObserver(this);
    if (session.novelGateway != null) {
      unawaited(_cancel());
      final stage = _stage;
      if (stage != null) {
        unawaited(gateway.discard(stage.token).catchError((Object _) {}));
      }
    }
    _actionsViewModel.removeListener(_onViewModelChanged);
    _actionsViewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final available = session.novelActions != null;
    final locked = session.novelMutationsLocked;
    final enabled = available && !locked && _foreground && !_busy;
    final row = current;
    final stage = _stage;
    final failure = session.novelPublicationFailure;
    final owner = session.manager?.state.owners[id];
    final offer = session.manager
        ?.offersFor(id)
        .where((row) => row.repositoryId == owner)
        .singleOrNull;
    return HikariPageScaffold(
      showAmbientGlow: true,
      appBar: AppBar(title: const Text('Novel actions')),
      body: HikariContentRegion(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: ListView(
            padding: const EdgeInsets.symmetric(vertical: HikariSpacing.lg),
            children: [
              const HikariSectionHeader(
                title: 'Novel bundle management',
                description: 'Review exact origins and revisions before granting consent.',
              ),
              if (!available)
                const HikariNoticeBanner(
                  message: 'Novel actions unavailable. Android management session required.',
                  tone: HikariNoticeTone.error,
                )
              else ...[
                SelectableText(id.upstreamId),
                const SizedBox(height: HikariSpacing.sm),
                const HikariNoticeBanner(
                  message: 'Staging never evaluates JavaScript. Every mutation requires explicit consent. No source hot replacement.',
                ),
                if (_busy) ...[
                  const SizedBox(height: HikariSpacing.md),
                  const Text('Action in progress'),
                ],
                if (_error != null) ...[
                  const SizedBox(height: HikariSpacing.sm),
                  HikariNoticeBanner(
                    message: _error!,
                    tone: HikariNoticeTone.error,
                  ),
                ],
                if (session.novelActionError != null) ...[
                  const SizedBox(height: HikariSpacing.sm),
                  HikariNoticeBanner(
                    message: session.novelActionError!,
                    tone: HikariNoticeTone.error,
                  ),
                ],
                const SizedBox(height: HikariSpacing.md),
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: OutlinedButton.icon(
                    onPressed: !_busy && _foreground
                        ? () => _run(session.refreshNovelEvidence)
                        : null,
                    icon: const Icon(Icons.refresh_rounded),
                    label: const Text('Refresh Novel evidence'),
                  ),
                ),
                const SizedBox(height: HikariSpacing.xl),
                if (failure != null)
                  PluginActionSection(
                    title: 'Publication recovery',
                    children: [
                      HikariNoticeBanner(
                        tone: HikariNoticeTone.error,
                        message:
                            failure.kind ==
                                NovelPublicationFailureKind.publicationUnknown
                            ? 'Publication unknown. Last-confirmed pointers are not proof of current disk state. Native process restart required; all Novel mutations locked.'
                            : 'Pointers committed; directory durability unknown. Native process restart required; all Novel mutations locked.',
                      ),
                      if (failure.inventory == null)
                        const HikariNoticeBanner(
                          tone: HikariNoticeTone.error,
                          message: 'Failure evidence malformed or unavailable; mutation lock retained.',
                        ),
                      for (final item
                          in failure.inventory ??
                              <NovelArtifactInventory>[]) ...[
                        Text(
                          failure.kind ==
                                  NovelPublicationFailureKind.publicationUnknown
                              ? 'Last-confirmed inventory'
                              : 'Committed inventory',
                        ),
                        NovelArtifactDetails(artifact: item.current),
                        if (item.previous != null)
                          NovelArtifactDetails(artifact: item.previous!),
                      ],
                      for (final pointer
                          in failure.attempted ??
                              <NovelAttemptedPointer>[]) ...[
                        const Text('Attempted pointers — unconfirmed'),
                        NovelArtifactDetails(artifact: pointer.current),
                        if (pointer.previous != null)
                          NovelArtifactDetails(artifact: pointer.previous!),
                      ],
                    ],
                  ),
                if (row != null)
                  PluginActionSection(
                    title: 'Stored bundle',
                    children: [
                      Text(
                        'Stored status: ${novelStatusLabel(row.status)}; admission: ${novelAdmissionLabel(row.admission)}',
                      ),
                      NovelArtifactDetails(artifact: row.current),
                      if (!locked) ...[
                        if (row.status == NovelArtifactStatus.ready &&
                            row.admission == NovelAdmission.unknown &&
                            row.current.metadata.repositoryId == owner)
                          OutlinedButton(
                            onPressed: enabled
                                ? () => _currentAction('admit')
                                : null,
                            child: const Text('Consent and admit current'),
                          ),
                        if (row.previous != null &&
                            row.previousStatus == NovelArtifactStatus.ready)
                          OutlinedButton(
                            onPressed: enabled
                                ? () => _currentAction('rollback')
                                : null,
                            child: const Text('Rollback Novel'),
                          ),
                        OutlinedButton.icon(
                          onPressed: enabled
                              ? () => _currentAction('uninstall')
                              : null,
                          icon: const Icon(Icons.delete_outline_rounded),
                          label: const Text('Remove Novel bundle'),
                        ),
                      ],
                    ],
                  ),
                if (!locked &&
                    stage == null &&
                    offer != null &&
                    (offer.siteUrl == null ||
                        offer.customJsUrl != null ||
                        offer.customCssUrl != null)) ...[
                  const HikariNoticeBanner(
                    message: 'This upstream Novel plugin requires an unsupported origin or auxiliary CSS/JS. Download is unavailable until its requirements are supported.',
                  ),
                  const SizedBox(height: HikariSpacing.xl),
                ],
                if (!locked &&
                    stage == null &&
                    offer != null &&
                    offer.siteUrl != null &&
                    offer.customJsUrl == null &&
                    offer.customCssUrl == null)
                  PluginActionSection(
                    title: 'Download bundle',
                    description: 'Confirm the exact permitted HTTPS origins.',
                    children: [
                      TextFormField(
                        initialValue: _origins,
                        maxLines: 3,
                        onChanged: (value) => _origins = value,
                        decoration: const InputDecoration(
                          labelText: 'Exact HTTPS origins (one per line)',
                          helperText: 'Site origin only by default. Additional origins require your explicit entry; no CDN or wildcard inference.',
                        ),
                      ),
                      OutlinedButton.icon(
                        onPressed: enabled ? _download : null,
                        icon: const Icon(Icons.download_rounded),
                        label: const Text('Download inert Novel bundle'),
                      ),
                    ],
                  ),
                if (_request != null) ...[
                  OutlinedButton(
                    onPressed: _cancel,
                    child: const Text('Cancel download'),
                  ),
                  const SizedBox(height: HikariSpacing.md),
                ],
                if (stage != null)
                  PluginActionSection(
                    title: 'Staged inert bundle',
                    children: [
                      NovelArtifactDetails(artifact: stage.artifact),
                      if (!locked && _stageUsable && _sameOffer())
                        FilledButton(
                          onPressed: enabled ? _install : null,
                          child: const Text('Consent and install Novel'),
                        )
                      else
                        const HikariNoticeBanner(
                          message: 'Stage no longer actionable. Do not repeat evaluation; refresh evidence and discard.',
                        ),
                      OutlinedButton(
                        onPressed: !_busy ? () => _run(_discard) : null,
                        child: const Text('Discard Novel stage'),
                      ),
                    ],
                  ),
                const HikariSectionHeader(
                  title: 'Runtime evidence',
                  description: 'Stored inventory and admission are independent. Changes never replace already captured code.',
                ),
                HikariNoticeBanner(
                  message:
                      'Runtime: ${session.novelRuntime == null ? 'unavailable' : novelRuntimeInitializationLabel(session.novelRuntime!.initialization)}',
                ),
                for (final loaded
                    in session.novelRuntime?.loaded.where(
                          (row) => row.id == id.upstreamId,
                        ) ??
                        <LoadedNovelRevision>[]) ...[
                  const SizedBox(height: HikariSpacing.sm),
                  if (loaded.artifact != null)
                    PluginActionSection(
                      title: 'Frozen loaded revision',
                      children: [
                        NovelArtifactDetails(artifact: loaded.artifact!),
                      ],
                    )
                  else
                    const HikariNoticeBanner(
                      message: 'A loaded revision was reported without artifact details.',
                    ),
                ],
              ],
            ],
          ),
        ),
      ),
    );
  }
}
