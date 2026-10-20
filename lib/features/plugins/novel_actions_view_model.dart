import 'package:flutter/foundation.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';
import 'package:hikari/features/plugins/plugin_acquisition_message.dart';

/// UI-only operation feedback; native journal/publication remains authoritative.
final class NovelActionsUiState {
  const NovelActionsUiState({required this.busy, required this.error});
  final bool busy;
  final String? error;
}

/// Novel action feedback is UI state, not native publication evidence.
final class NovelActionsViewModel extends ChangeNotifier {
  NovelActionsUiState _state = const NovelActionsUiState(
    busy: false,
    error: null,
  );
  NovelActionsUiState get state => _state;
  bool get busy => state.busy;
  String? get error => state.error;
  bool _disposed = false;

  void _setState({bool? busy, String? error, bool clearError = false}) {
    if (_disposed) return;
    _state = NovelActionsUiState(
      busy: busy ?? _state.busy,
      error: clearError ? null : error ?? _state.error,
    );
    notifyListeners();
  }

  void showError(String message) {
    if (_disposed) return;
    _setState(error: message);
  }

  Future<void> run(
    Future<void> Function() action, {
    required bool Function() canStart,
  }) async {
    if (_disposed || busy || !canStart()) return;
    _setState(busy: true, clearError: true);
    try {
      await action();
    } on NovelAdmissionRejected catch (error) {
      _setState(
        error:
            'Admission rejected: ${error.reason}. No compatibility or trust proven.',
      );
    } on NovelPublicationFailure {
      // App-owned sticky evidence is separately rendered; never clear it here.
    } catch (error) {
      _setState(
        error:
            pluginAcquisitionMessage(error) ??
            (error is FormatException
                ? 'Unsupported Novel metadata or invalid stage evidence. No consent or code execution was inferred.'
                : 'Novel action failed or outcome unknown. Refresh evidence; never automatically repeat code evaluation or infer rollback.'),
      );
    } finally {
      _setState(busy: false);
    }
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
