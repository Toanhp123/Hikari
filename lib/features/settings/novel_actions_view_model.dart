import 'package:flutter/foundation.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/features/settings/plugin_acquisition_message.dart';

/// Novel action feedback is UI state, not native publication evidence.
final class NovelActionsViewModel extends ChangeNotifier {
  bool _busy = false;
  bool get busy => _busy;
  String? _error;
  String? get error => _error;
  bool _disposed = false;

  void showError(String message) {
    _error = message;
    if (!_disposed) notifyListeners();
  }

  Future<void> run(
    Future<void> Function() action, {
    required bool Function() canStart,
  }) async {
    if (_disposed || _busy || !canStart()) return;
    _busy = true;
    _error = null;
    notifyListeners();
    try {
      await action();
    } on NovelAdmissionRejected catch (error) {
      _error =
          'Admission rejected: ${error.reason}. No compatibility or trust proven.';
    } on NovelPublicationFailure {
      // App-owned sticky evidence is separately rendered; never clear it here.
    } catch (error) {
      _error =
          pluginAcquisitionMessage(error) ??
          (error is FormatException
              ? 'Unsupported Novel metadata or invalid stage evidence. No consent or code execution was inferred.'
              : 'Novel action failed or outcome unknown. Refresh evidence; never automatically repeat code evaluation or infer rollback.');
    } finally {
      _busy = false;
      if (!_disposed) notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
