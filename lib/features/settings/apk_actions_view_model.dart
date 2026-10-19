import 'package:flutter/foundation.dart';
import 'package:hikari/application/plugins/manage_apk_plugins.dart';
import 'package:hikari/application/plugins/plugin_management.dart';
import 'package:hikari/features/settings/plugin_acquisition_message.dart';

/// Async presentation state only. Consent and native-authoritative changes are
/// kept in their existing boundary-checked commands.
final class ApkActionsViewModel extends ChangeNotifier {
  ApkActionsViewModel(this.management);
  final PluginManagement management;
  bool _busy = false;
  bool get busy => _busy;
  String? _error;
  String? get error => _error;
  bool _disposed = false;

  void showError(String message) {
    _error = message;
    if (!_disposed) notifyListeners();
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
    if (_disposed || _busy || !canStart()) return;
    _busy = true;
    _error = null;
    notifyListeners();
    try {
      await action();
    } on ApkApprovalReconciliationFailure {
      _error = 'Native signer approval committed. Inventory reconciliation failed. Refresh evidence; do not repeat approval.';
    } catch (error) {
      _error = pluginAcquisitionMessage(error) ?? 'Action failed or outcome unknown. Refresh native operations before retrying; no success or rollback inferred.';
      try {
        await management.refreshApkOperations();
      } catch (_) {
        // Keep last native evidence; never replay the operation.
      }
    } finally {
      _busy = false;
      if (!_disposed) {
        notifyListeners();
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
