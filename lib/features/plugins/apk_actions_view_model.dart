import 'package:flutter/foundation.dart';
import 'package:hikari/application/plugins/manage_apk_plugins.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/features/plugins/plugin_acquisition_message.dart';

/// UI-only operation feedback; native journal/publication remains authoritative.
final class ApkActionsUiState {
  const ApkActionsUiState({required this.busy, required this.error});
  final bool busy;
  final String? error;
}

/// Async presentation state only. Consent and native-authoritative changes are
/// kept in their existing boundary-checked commands.
final class ApkActionsViewModel extends ChangeNotifier {
  ApkActionsViewModel(this.management);
  final PluginManagement management;
  ApkActionsUiState _state = const ApkActionsUiState(busy: false, error: null);
  ApkActionsUiState get state => _state;
  bool get busy => state.busy;
  String? get error => state.error;
  bool _disposed = false;

  void _setState({bool? busy, String? error, bool clearError = false}) {
    if (_disposed) return;
    _state = ApkActionsUiState(
      busy: busy ?? _state.busy,
      error: clearError ? null : error ?? _state.error,
    );
    notifyListeners();
  }

  void showError(String message) {
    if (_disposed) return;
    _setState(error: message);
  }

  /// UI polling reads OS evidence, but never interprets it as install success.
  /// A route that loses foreground cannot publish a late inventory snapshot.
  Future<bool?> refreshNativeEvidence({
    required bool Function() stillVisible,
  }) async {
    await management.refreshApkOperations();
    if (!stillVisible()) return null;
    final gateway = management.apkGateway!;
    final permission = await gateway.canInstall();
    if (!stillVisible()) return null;
    final inventory = await gateway.inventory();
    if (!stillVisible()) return null;
    management.replaceApkInventory(inventory);
    return permission;
  }

  Future<void> run(
    Future<void> Function() action, {
    required bool Function() canStart,
    required void Function() onFinished,
  }) async {
    if (_disposed || busy || !canStart()) return;
    _setState(busy: true, clearError: true);
    try {
      await action();
    } on ApkApprovalReconciliationFailure {
      _setState(
        error: 'Native signer approval committed. Inventory reconciliation failed. Refresh evidence; do not repeat approval.',
      );
    } catch (error) {
      _setState(
        error: pluginAcquisitionMessage(error) ?? 'Action failed or outcome unknown. Refresh native operations before retrying; no success or rollback inferred.',
      );
      try {
        await management.refreshApkOperations();
      } catch (_) {
        // Keep last native evidence; never replay the operation.
      }
    } finally {
      if (!_disposed) {
        _setState(busy: false);
        onFinished();
      }
    }
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
