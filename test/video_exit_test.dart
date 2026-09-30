import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/features/player/player_page.dart';
import 'package:hikari/infrastructure/playback/video_progress.dart';

void main() {
  for (final systemBack in [false, true]) {
    testWidgets(
      '${systemBack ? 'system' : 'AppBar'} back awaits final save once',
      (tester) async {
        final pending = Completer<void>();
        final writes = <Duration>[];
        var disposed = 0;
        var exited = false;
        final tracker = VideoProgressTracker(
          saveProgress: (position, completed) async {
            writes.add(position.position);
            await pending.future;
          },
        );
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: Builder(
                builder: (context) => TextButton(
                  onPressed: () async {
                    await Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => PlayerPage(
                          title: 'Video',
                          beforeExit: tracker.finish,
                          playback: _DisposalProbe(
                            onDispose: () {
                              disposed++;
                              tracker.notePosition(
                                Duration.zero,
                                const Duration(seconds: 100),
                              );
                              unawaited(tracker.flush());
                            },
                          ),
                        ),
                      ),
                    );
                    exited = true;
                  },
                  child: const Text('Open'),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Open'));
        await tester.pumpAndSettle();
        tracker.notePosition(
          const Duration(seconds: 42),
          const Duration(seconds: 100),
        );
        if (systemBack) {
          await tester.binding.handlePopRoute();
        } else {
          await tester.tap(find.byTooltip('Back'));
        }
        await tester.pump();
        expect(writes, [const Duration(seconds: 42)]);
        expect(find.text('Video'), findsOneWidget);
        expect(disposed, 0);
        expect(exited, isFalse);
        await tester.binding.handlePopRoute();
        tracker.notePosition(Duration.zero, const Duration(seconds: 100));
        await tracker.flush();
        await tester.pump();
        expect(writes, hasLength(1));
        pending.complete();
        await tester.pumpAndSettle();
        expect(find.text('Video'), findsNothing);
        expect(disposed, 1);
        expect(exited, isTrue);
        expect(writes, [const Duration(seconds: 42)]);
      },
    );
  }

  testWidgets('failed final save still permits exit', (tester) async {
    final tracker = VideoProgressTracker(
      saveProgress: (_, _) async => throw StateError('write failed'),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Builder(
            builder: (context) => TextButton(
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => PlayerPage(
                    title: 'Video',
                    beforeExit: tracker.finish,
                    playback: const SizedBox(),
                  ),
                ),
              ),
              child: const Text('Open'),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    tracker.notePosition(
      const Duration(seconds: 12),
      const Duration(seconds: 100),
    );
    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();
    expect(find.text('Video'), findsNothing);
    expect(find.text('Could not save playback progress.'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  test('event-backed flush retains five-second throttling', () async {
    final writes = <Duration>[];
    final tracker = VideoProgressTracker(
      saveProgress: (position, _) async => writes.add(position.position),
    );
    final now = DateTime.utc(2026);
    tracker.notePosition(
      const Duration(seconds: 1),
      const Duration(seconds: 100),
    );
    await tracker.flush(force: false, now: now);
    tracker.notePosition(
      const Duration(seconds: 3),
      const Duration(seconds: 100),
    );
    await tracker.flush(force: false, now: now.add(const Duration(seconds: 2)));
    expect(writes, [const Duration(seconds: 1)]);
    tracker.notePosition(
      const Duration(seconds: 6),
      const Duration(seconds: 100),
    );
    await tracker.flush(force: false, now: now.add(const Duration(seconds: 5)));
    expect(writes, [const Duration(seconds: 1), const Duration(seconds: 6)]);
    await tracker.finish();
    tracker.notePosition(Duration.zero, Duration.zero);
    await tracker.flush();
    expect(writes.last, const Duration(seconds: 6));
    expect(writes, hasLength(3));
  });

  test('exit before meaningful playback never creates zero progress', () async {
    var writes = 0;
    final tracker = VideoProgressTracker(
      saveProgress: (_, _) async {
        writes++;
      },
    );
    tracker.noteDuration(const Duration(seconds: 100));
    await tracker.finish();
    tracker.notePosition(Duration.zero, const Duration(seconds: 100));
    await tracker.flush();
    expect(writes, 0);
  });

  test('final write follows pending write and freezes snapshot', () async {
    final pending = Completer<void>();
    final writes = <Duration>[];
    final tracker = VideoProgressTracker(
      saveProgress: (position, _) async {
        writes.add(position.position);
        if (writes.length == 1) await pending.future;
      },
    );
    tracker.notePosition(
      const Duration(seconds: 5),
      const Duration(seconds: 100),
    );
    final first = tracker.flush();
    await Future<void>.delayed(Duration.zero);
    tracker.notePosition(
      const Duration(seconds: 42),
      const Duration(seconds: 100),
    );
    final exit = tracker.finish();
    expect(identical(exit, tracker.finish()), isTrue);
    tracker.notePosition(Duration.zero, Duration.zero);
    expect(writes, [const Duration(seconds: 5)]);
    pending.complete();
    await first;
    await exit;
    expect(writes, [const Duration(seconds: 5), const Duration(seconds: 42)]);
  });
}

class _DisposalProbe extends StatefulWidget {
  const _DisposalProbe({required this.onDispose});
  final VoidCallback onDispose;
  @override
  State<_DisposalProbe> createState() => _DisposalProbeState();
}

class _DisposalProbeState extends State<_DisposalProbe> {
  @override
  void dispose() {
    widget.onDispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => const SizedBox();
}
