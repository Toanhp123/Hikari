import 'dart:async';

/// Lets speculative work yield to foreground reads sharing this gate.
/// Already-started work is never interrupted or cancelled.
final class ForegroundReadGate {
  int _active = 0;
  Completer<void>? _idle;

  Future<void> waitForIdle() async {
    while (_active > 0) {
      await _idle!.future;
    }
  }

  Future<T> track<T>(Future<T> Function() read) async {
    _active++;
    _idle ??= Completer<void>();
    try {
      return await read();
    } finally {
      _active--;
      if (_active == 0) {
        _idle!.complete();
        _idle = null;
      }
    }
  }
}
