import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';

/// One feature content tree, borrowing a route-owned controller.
typedef HikariContentBuilder<T> = Widget Function(
  BuildContext context,
  HikariPresentationController<T> controller,
);

/// Exactly-once interaction settlement. Host owns route closure and disposal.
///
/// Feature updates pre-pop permission synchronously when nested state changes.
/// A guard may consume a denied Back by changing that state, but cannot veto a
/// pop after it happened. Disposal never leaves [result] pending.
final class HikariPresentationController<T> extends ChangeNotifier {
  final _result = Completer<HikariPresentationResult<T>>();
  bool _disposed = false;
  bool _canDismiss = true;
  bool Function(HikariDismissReason)? _guard;

  Future<HikariPresentationResult<T>> get result => _result.future;
  bool get isSettled => _result.isCompleted;
  bool get canDismiss => !_disposed && !isSettled && _canDismiss;

  /// Updates predictive-Back veto state before the next pop attempt.
  void setDismissalPolicy({
    required bool canDismiss,
    bool Function(HikariDismissReason)? guard,
  }) {
    if (_disposed || isSettled) return;
    final changed = _canDismiss != canDismiss || _guard != guard;
    _canDismiss = canDismiss;
    _guard = guard;
    if (changed) notifyListeners();
  }

  /// Returns false after any competing result or disposal.
  bool complete(T value) => _settle(HikariCompleted(value));

  /// Guarded request; host must use this before popping its owned route.
  bool cancel(HikariDismissReason reason) {
    if (_disposed || isSettled) return false;
    final allowed = _guard?.call(reason) ?? _canDismiss;
    if (!allowed) return false;
    return _settle(HikariCancelled(reason));
  }

  /// Framework already removed the route; permission can no longer veto it.
  void didDismiss() =>
      _settle(HikariCancelled(HikariDismissReason.routeDismissed));

  bool _settle(HikariPresentationResult<T> result) {
    if (_disposed || isSettled) return false;
    _result.complete(result);
    notifyListeners();
    return true;
  }

  @override
  void dispose() {
    if (_disposed) return;
    if (!isSettled) {
      _result.complete(HikariCancelled(HikariDismissReason.routeDisposed));
    }
    _disposed = true;
    _guard = null;
    super.dispose();
  }
}
