import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/foreground_read_gate.dart';

void main() {
  test(
    'waits for all foreground reads, not just the first completion',
    () async {
      final gate = ForegroundReadGate();
      final first = Completer<int>();
      final second = Completer<int>();
      final firstRead = gate.track(() => first.future);
      final secondRead = gate.track(() => second.future);
      var idle = false;
      final pending = gate.waitForIdle().then((_) => idle = true);

      first.complete(1);
      await firstRead;
      await Future<void>.delayed(Duration.zero);
      expect(idle, isFalse);
      second.complete(2);
      await secondRead;
      await pending;
      expect(idle, isTrue);
    },
  );

  test('a failed foreground read releases speculative waiters', () async {
    final gate = ForegroundReadGate();
    final pendingRead = Completer<int>();
    final read = gate.track(() => pendingRead.future);
    final idle = gate.waitForIdle();

    pendingRead.completeError(StateError('offline'));
    await expectLater(read, throwsStateError);
    await idle;
    await gate.waitForIdle();
  });
}
