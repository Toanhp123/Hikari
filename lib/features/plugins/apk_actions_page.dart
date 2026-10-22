import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/components/hikari_confirmation_dialog.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/application/plugins/manage_apk_plugins.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/content/hikari_section_header.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/apk_actions_view_model.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';
import 'package:hikari/features/plugins/widgets/apk_identity_details.dart';
import 'package:hikari/features/plugins/widgets/apk_operation_card.dart';
import 'package:hikari/features/plugins/widgets/plugin_action_section.dart';
import 'package:hikari/features/plugins/widgets/plugin_notice.dart';

/// Explicit package actions. Never changes the running source registry.
class ApkActionsPage extends StatefulWidget {
  const ApkActionsPage({super.key, required this.management, this.pluginId});
  final PluginManagement management;
  final PluginId? pluginId;
  @override
  State<ApkActionsPage> createState() => _ApkActionsPageState();
}

class _ApkActionsPageState extends State<ApkActionsPage>
    with WidgetsBindingObserver {
  PluginManagement get session => widget.management;
  ApkPluginGateway get gateway => session.apkGateway!;
  bool _foreground = true;
  late final ApkActionsViewModel _actionsViewModel;
  bool get _busy => _actionsViewModel.busy;
  String? get _error => _actionsViewModel.error;
  void _onViewModelChanged() {
    if (mounted) setState(() {});
  }

  bool _polling = false;
  bool _permission = false;
  bool _stageApproved = false;
  bool _stageUsable = true;
  int _epoch = 0;
  Timer? _timer;
  String? _request;
  StagedApk? _stage;
  PluginOffer? _offer;
  RepositoryId? _owner;
  String? _expectedSigner;
  String? _installedAtStage;
  static int _sequence = 0;

  bool get _active => mounted && _foreground;
  bool get _visible => _active && (ModalRoute.of(context)?.isCurrent ?? false);
  bool _validBoundary(int epoch) => _active && epoch == _epoch;
  PluginId? get id => widget.pluginId;
  ApkPluginIdentity? get _installed =>
      session.apks.where((row) => row.id == id).firstOrNull;
  bool _supported(ApkPluginIdentity row) =>
      row.compatibility == ApkCompatibility.compatible &&
      row.currentSigners.length == 1;
  bool get _unresolved =>
      session.apkOperations.any((row) => row.pluginId == id && !row.isTerminal);
  String? _revision(ApkPluginIdentity? row) => row == null
      ? null
      : '${row.id}:${row.version.code}:${row.compatibility.name}:${row.currentSigners.join(',')}';

  @override
  void initState() {
    super.initState();
    _actionsViewModel = ApkActionsViewModel(session)
      ..addListener(_onViewModelChanged);
    _foreground =
        WidgetsBinding.instance.lifecycleState == null ||
        WidgetsBinding.instance.lifecycleState == AppLifecycleState.resumed;
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) => unawaited(_poll()));
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    if (!_foreground) {
      _epoch++;
      _timer?.cancel();
    } else {
      unawaited(_poll());
    }
    if (mounted) setState(() {});
  }

  void _schedule() {
    _timer?.cancel();
    if (_active) _timer = Timer(const Duration(seconds: 2), _poll);
  }

  Future<void> _poll() async {
    if (!_active || _polling) return;
    if (!_visible || _busy || session.apkActions == null) {
      _schedule();
      return;
    }
    _polling = true;
    try {
      final permission = await _actionsViewModel.refreshNativeEvidence(
        stillVisible: () => _visible,
      );
      if (permission != null && mounted) {
        setState(() => _permission = permission);
      }
    } catch (_) {
      if (mounted) {
        _actionsViewModel.showError(
          'Could not refresh native evidence. No operation was replayed.',
        );
      }
    } finally {
      _polling = false;
      _schedule();
    }
  }

  Future<void> _run(Future<void> Function() action) => _actionsViewModel.run(
    action,
    canStart: () => _active && !_polling,
    onFinished: _schedule,
  );

  Future<bool> _confirm(String title, List<Widget> contents, String action) =>
      showHikariConfirmation(
        context: context,
        title: title,
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: contents,
        ),
        confirmLabel: action,
      );

  bool _ownerUnchanged() {
    final manager = session.manager!;
    return manager.state.owners[id] == _owner &&
        manager.offersFor(id!).any((offer) => identical(offer, _offer)) &&
        manager.state.repositories
                .where((row) => row.id == _owner)
                .firstOrNull
                ?.snapshot
                ?.signerFingerprint ==
            _expectedSigner;
  }

  Future<void> _checkStage(int epoch) async {
    final inventory = await gateway.inventory();
    session.replaceApkInventory(inventory);
    if (!_validBoundary(epoch) ||
        !_ownerUnchanged() ||
        _revision(_installed) != _installedAtStage ||
        !_stageUsable ||
        _unresolved) {
      throw StateError('Displayed stage or installed identity changed');
    }
  }

  Future<void> _download() => _run(() async {
    final manager = session.manager!;
    _owner = manager.state.owners[id];
    _offer = manager
        .offersFor(id!)
        .where((row) => row.repositoryId == _owner)
        .firstOrNull;
    _expectedSigner = manager.state.repositories
        .where((row) => row.id == _owner)
        .firstOrNull
        ?.snapshot
        ?.signerFingerprint;
    if (_offer == null || _expectedSigner == null || _unresolved) {
      throw StateError('Owner or signer unavailable');
    }
    session.replaceApkInventory(await gateway.inventory());
    if (!_active) return;
    _installedAtStage = _revision(_installed);
    if (_installed != null && !_supported(_installed!)) {
      throw StateError('Installed identity unsupported');
    }
    final request =
        'apk-ui-${DateTime.now().microsecondsSinceEpoch}-${_sequence++}';
    setState(() => _request = request);
    final epoch = _epoch;
    try {
      final staged = await session.apkActions!.stage(id!, requestId: request);
      if (!_validBoundary(epoch) || _request != request || !_ownerUnchanged()) {
        await gateway.discardStaged(staged.token);
        return;
      }
      _stage = staged;
      _stageApproved = staged.identity.trust == ApkTrust.approved;
      _stageUsable = true;
    } finally {
      if (_request == request) _request = null;
    }
  });

  Future<void> _cancelTransfer() async {
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
        if (mounted) {
          _actionsViewModel.showError(
            'Transfer cancellation not confirmed. Late bytes will be discarded.',
          );
        }
      }
    }
  }

  Future<void> _discard() async {
    final stage = _stage;
    if (stage == null) return;
    await gateway.discardStaged(stage.token);
    _stage = null;
    _stageApproved = false;
  }

  Future<void> _approve({required bool staged}) => _run(() async {
    final displayed = staged ? _stage!.identity : _installed!;
    final epoch = _epoch;
    if (!await _confirm('Trust this APK signer?', [
      ApkIdentityDetails(identity: displayed),
      const Text(
        'Signer approval allows executable extension code. Repository metadata is not authenticity proof. Approval does not install or replace running sources.',
      ),
    ], 'Approve signer')) {
      return;
    }
    if (!_validBoundary(epoch)) return;
    if (staged) {
      await _checkStage(epoch);
      try {
        final approved = await session.apkActions!.approveStaged(_stage!.token);
        _stage = StagedApk(token: _stage!.token, identity: approved);
        _stageApproved = true;
      } on ApkApprovalReconciliationFailure {
        _stageApproved =
            true; // Native consent committed; do not offer it again.
        rethrow;
      }
    } else {
      session.replaceApkInventory(await gateway.inventory());
      if (!_validBoundary(epoch) ||
          _revision(_installed) != _revision(displayed)) {
        throw StateError('Installed identity changed');
      }
      try {
        await session.apkActions!.approveInstalled(displayed);
      } on ApkApprovalReconciliationFailure {
        // Refresh OS-owned trust without repeating approval.
        session.replaceApkInventory(await gateway.inventory());
        rethrow;
      }
      session.replaceApkInventory(await gateway.inventory());
    }
  });

  Future<void> _install() => _run(() async {
    final epoch = _epoch;
    await _checkStage(epoch);
    _permission = await gateway.canInstall();
    if (!_validBoundary(epoch) || !_permission) return;
    final kind = _installed == null
        ? PluginOperation.install
        : PluginOperation.update;
    if (!await _confirm('Submit ${pluginOperationLabel(kind)}?', [
      ApkIdentityDetails(identity: _stage!.identity),
      const Text(
        'Android confirmation and verified native result are still required. Running sources stay frozen; restart may be required.',
      ),
    ], 'Submit ${pluginOperationLabel(kind)}')) {
      return;
    }
    await _checkStage(epoch);
    if (!await gateway.canInstall() || !_validBoundary(epoch)) return;
    _stageUsable = false; // Crossing native submission boundary; never replay this stage on error.
    final operation = await session.apkActions!.installStaged(
      _stage!.token,
      kind,
    );
    session.recordApkOperation(operation);
    if (operation.stageTransferred) _stage = null;
    if (!mounted && !operation.stageTransferred) {
      try {
        await gateway.discardStaged(_stage!.token);
      } catch (_) {
        /* Native may already own or discard stage. */
      }
    }
  });

  Future<void> _uninstall() => _run(() async {
    final displayed = _installed!;
    final epoch = _epoch;
    if (!await _confirm('Uninstall APK?', [
      ApkIdentityDetails(identity: displayed),
      const Text(
        'Library and Progress remain unchanged. A loaded reader may fail after removal. Android removal UI is not proof of completion.',
      ),
    ], 'Open system removal')) {
      return;
    }
    session.replaceApkInventory(await gateway.inventory());
    if (!_validBoundary(epoch) ||
        _revision(_installed) != _revision(displayed) ||
        _unresolved) {
      throw StateError('Installed identity changed');
    }
    session.recordApkOperation(await session.apkActions!.uninstall(displayed));
  });

  Future<void> _operation(
    ApkInstallOperation displayed,
    String action,
  ) => _run(() async {
    final epoch = _epoch;
    if (!await _confirm('$action?', [
      SelectableText(
        '${displayed.pluginId.upstreamId} · ${displayed.versionCode} · ${displayed.signer}',
      ),
      const Text(
        'This request is not proof of success or cancellation. Native operation state remains authoritative.',
      ),
    ], action)) {
      return;
    }
    await session.refreshApkOperations();
    final current = session.apkOperations
        .where((row) => row.id == displayed.id)
        .firstOrNull;
    if (!_validBoundary(epoch) ||
        current == null ||
        current.isTerminal ||
        current.status != displayed.status) {
      return;
    }
    session.recordApkOperation(
      action == 'Resume Android confirmation'
          ? await gateway.resumeInstallConfirmation(current.id)
          : await gateway.cancelInstall(current.id),
    );
  });

  Future<void> _retire(ApkInstallOperation displayed) => _run(() async {
    var abandon = false;
    var acknowledged = false;
    final removal = displayed.kind == PluginOperation.uninstall;
    final epoch = _epoch;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, update) => AlertDialog(
          title: const Text('Retire unknown outcome?'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                SelectableText(
                  '${displayed.pluginId.upstreamId} · ${displayed.versionCode} · ${displayed.signer}',
                ),
                const Text(
                  'Outcome remains unknown, not success or cancellation. Native authority must verify no owned session remains.',
                ),
                CheckboxListTile(
                  value: abandon,
                  onChanged: (value) => update(() => abandon = value!),
                  title: const Text('Abandon remaining owned install sessions'),
                ),
                if (removal) ...[
                  const Text(
                    'Old system removal dialog cannot be revoked and could remove a replacement. Dismiss it before retiring this operation.',
                  ),
                  CheckboxListTile(
                    value: acknowledged,
                    onChanged: (value) => update(() => acknowledged = value!),
                    title: const Text(
                      'I dismissed the old system removal dialog',
                    ),
                  ),
                ],
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: removal && !acknowledged
                  ? null
                  : () => Navigator.pop(context, true),
              child: const Text('Retire operation'),
            ),
          ],
        ),
      ),
    );
    if (confirmed != true || !_validBoundary(epoch)) return;
    await session.refreshApkOperations();
    final current = session.apkOperations
        .where((row) => row.id == displayed.id)
        .firstOrNull;
    if (!_validBoundary(epoch) ||
        current == null ||
        current.isTerminal ||
        current.status != displayed.status) {
      return;
    }
    session.recordApkOperation(
      await session.apkActions!.retireInstall(
        current.id,
        abandonOwnedSession: abandon,
        acknowledgeUnrevokedRemoval: acknowledged,
      ),
    );
  });

  @override
  void dispose() {
    _epoch++;
    _foreground = false;
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    if (session.apkGateway != null) {
      unawaited(_cancelTransfer());
      final stage = _stage;
      if (stage != null) {
        unawaited(gateway.discardStaged(stage.token).catchError((Object _) {}));
      }
    }
    _actionsViewModel.removeListener(_onViewModelChanged);
    _actionsViewModel.dispose();
    // Submitted sessions belong to native journal, never automatically cancelled.
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final available = session.apkActions != null;
    final enabled = available && _foreground && !_busy && !_polling;
    final installed = _installed;
    final stage = _stage;
    final owner = session.manager?.state.owners[id];
    final offer = id == null
        ? null
        : session.manager
              ?.offersFor(id!)
              .where((row) => row.repositoryId == owner)
              .firstOrNull;
    final signer = session.manager?.state.repositories
        .where((row) => row.id == owner)
        .firstOrNull
        ?.snapshot
        ?.signerFingerprint;
    final operations = session.apkOperations
        .where((row) => id == null || row.pluginId == id)
        .toList();
    return HikariScaffold(
      appBar: AppBar(title: const Text('APK actions')),
      body: Align(
        alignment: Alignment.topCenter,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: ListView(
            padding: const EdgeInsets.all(HikariSpacing.lg),
            children: [
              const HikariSectionHeader(
                title: 'Package management',
                description:
                    'Install, update and remove Android extension packages.',
              ),
              if (!available)
                const PluginNotice(
                  message: 'APK actions unavailable. Android management session required.',
                  error: true,
                )
              else ...[
                if (id != null) ...[
                  SelectableText(
                    '${pluginEcosystemLabel(id!.ecosystem)} · ${id!.upstreamId}',
                  ),
                  const SizedBox(height: HikariSpacing.sm),
                ],
                if (id?.ecosystem == PluginEcosystem.anime) ...[
                  const PluginNotice(
                    message: 'Runtime deferred (runtimeDeferred). APK management does not enable playback.',
                  ),
                  const SizedBox(height: HikariSpacing.sm),
                ],
                const PluginNotice(
                  message: 'Package operations do not hot-replace sources or modify Library and Progress.',
                ),
                if (_error != null) ...[
                  const SizedBox(height: HikariSpacing.sm),
                  PluginNotice(message: _error!, error: true),
                ],
                if (session.operationError != null) ...[
                  const SizedBox(height: HikariSpacing.sm),
                  PluginNotice(message: session.operationError!, error: true),
                ],
                if (_busy) ...[
                  const SizedBox(height: HikariSpacing.sm),
                  const Text('Action in progress'),
                ],
                const SizedBox(height: HikariSpacing.md),
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: OutlinedButton.icon(
                    onPressed: enabled ? _poll : null,
                    icon: const Icon(Icons.refresh_rounded),
                    label: const Text('Refresh native evidence'),
                  ),
                ),
                const SizedBox(height: HikariSpacing.xl),
                if (installed != null)
                  PluginActionSection(
                    title: 'Installed APK',
                    children: [
                      ApkIdentityDetails(identity: installed),
                      if (!_supported(installed))
                        const PluginNotice(
                          message: 'Unsupported APK identity. Approval, update and removal blocked here; use Android system app settings for removal.',
                          error: true,
                        )
                      else ...[
                        if (installed.trust == ApkTrust.unapproved)
                          OutlinedButton(
                            onPressed: enabled && !_unresolved
                                ? () => _approve(staged: false)
                                : null,
                            child: const Text('Approve installed signer'),
                          ),
                        OutlinedButton.icon(
                          onPressed: enabled && !_unresolved
                              ? _uninstall
                              : null,
                          icon: const Icon(Icons.delete_outline_rounded),
                          label: const Text('Uninstall APK'),
                        ),
                      ],
                    ],
                  ),
                if (id != null &&
                    stage == null &&
                    (installed == null || _supported(installed)))
                  PluginActionSection(
                    title: 'Get APK',
                    description:
                        'Download and inspect before any install consent.',
                    children: [
                      SelectableText(
                        'Selected repository: ${owner ?? 'Not selected'}',
                      ),
                      if (offer == null || signer == null)
                        const PluginNotice(
                          message: 'Select repository owner with an offer and signer expectation before downloading.',
                        )
                      else
                        OutlinedButton.icon(
                          onPressed: enabled && !_unresolved ? _download : null,
                          icon: const Icon(Icons.download_rounded),
                          label: const Text('Download and inspect APK'),
                        ),
                    ],
                  ),
                if (_request != null)
                  OutlinedButton(
                    onPressed: _cancelTransfer,
                    child: const Text('Cancel download'),
                  ),
                if (stage != null)
                  PluginActionSection(
                    title: 'Inspected staged APK',
                    children: [
                      ApkIdentityDetails(identity: stage.identity),
                      SelectableText('Repository: $_owner'),
                      SelectableText(
                        'Repository signer expectation: $_expectedSigner',
                      ),
                      if (!_supported(stage.identity))
                        const PluginNotice(
                          message: 'Unsupported staged APK. Installation and approval blocked.',
                          error: true,
                        )
                      else if (_stageUsable && _ownerUnchanged()) ...[
                        if (!_stageApproved)
                          OutlinedButton(
                            onPressed: enabled
                                ? () => _approve(staged: true)
                                : null,
                            child: const Text('Approve staged signer'),
                          )
                        else if (!_permission)
                          OutlinedButton(
                            onPressed: enabled
                                ? () => _run(() async {
                                    if (_active) {
                                      await gateway.openInstallSettings();
                                    }
                                  })
                                : null,
                            child: const Text('Open install settings'),
                          )
                        else
                          FilledButton(
                            onPressed: enabled && !_unresolved
                                ? _install
                                : null,
                            child: Text(
                              installed == null ? 'Install APK' : 'Update APK',
                            ),
                          ),
                      ] else
                        const PluginNotice(
                          message: 'Stage no longer actionable. Refresh operation evidence; discard before downloading again.',
                        ),
                      OutlinedButton(
                        onPressed: enabled ? () => _run(_discard) : null,
                        child: const Text('Discard staged APK'),
                      ),
                    ],
                  ),
                const HikariSectionHeader(
                  title: 'Native APK operations',
                  description: 'Only succeeded means verified native completion. Other states, including externallyInstalled and retiredUnknown, are not manager success. Runtime evidence is separate.',
                ),
                if (operations.isEmpty)
                  const PluginNotice(
                    message: 'No native APK operations recorded.',
                  ),
                for (final operation in operations) ...[
                  ApkOperationCard(
                    operation: operation,
                    enabled: enabled,
                    onResume: () =>
                        _operation(operation, 'Resume Android confirmation'),
                    onCancel: () =>
                        _operation(operation, 'Request cancellation'),
                    onRetire: () => _retire(operation),
                  ),
                  const SizedBox(height: HikariSpacing.sm),
                ],
              ],
            ],
          ),
        ),
      ),
    );
  }
}
