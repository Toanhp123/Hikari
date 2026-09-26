import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/features/player/player_page.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/infrastructure/playback/local_video_session.dart';
import 'package:hikari/infrastructure/playback/src/video_driver.dart';
import 'package:media_kit_video/media_kit_video.dart';

class FakeVideoDriver implements VideoDriver {
  final positions = StreamController<Duration>.broadcast(sync: true);
  final durations = StreamController<Duration>.broadcast(sync: true);
  final completions = StreamController<bool>.broadcast(sync: true);
  final playingEvents = StreamController<bool>.broadcast(sync: true);
  final errors = StreamController<String>.broadcast(sync: true);
  int stops = 0;
  int disposals = 0;
  int pauses = 0;
  int plays = 0;
  final seeks = <Duration>[];
  bool failSeek = false;
  bool ready = true;
  Completer<void>? opening;
  Completer<void>? stopping;
  @override
  VideoController? get controller => null;
  @override
  Stream<Duration> get position => positions.stream;
  @override
  Stream<Duration> get duration => durations.stream;
  @override
  Stream<bool> get completed => completions.stream;
  @override
  Stream<bool> get playing => playingEvents.stream;
  @override
  Stream<String> get error => errors.stream;
  void reset() {
    positions.add(Duration.zero);
    durations.add(Duration.zero);
    completions.add(false);
    playingEvents.add(false);
  }

  @override
  Future<void> open(String locator) async {
    reset();
    await opening?.future;
    if (ready) durations.add(const Duration(seconds: 100));
  }

  @override
  Future<void> seek(Duration value) async {
    seeks.add(value);
    if (failSeek) throw StateError('seek failed');
    positions.add(value);
  }

  @override
  Future<void> play() async {
    plays++;
    playingEvents.add(true);
  }

  @override
  Future<void> pause() async {
    pauses++;
    playingEvents.add(false);
  }

  @override
  Future<void> stop() async {
    stops++;
    reset();
    await stopping?.future;
  }

  @override
  Future<void> dispose() async {
    disposals++;
    await positions.close();
    await durations.close();
    await completions.close();
    await playingEvents.close();
    await errors.close();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeVideoDriver driver;
  late LocalVideoSession owner;
  late Map<String, MediaProgress> stored;
  late int creations;
  var widgetTest = false;
  setUp(() {
    widgetTest = false;
    driver = FakeVideoDriver();
    stored = {};
    creations = 0;
    owner = LocalVideoSession(
      driverFactory: () {
        creations++;
        return driver;
      },
    );
  });
  tearDown(() async {
    if (!widgetTest) await owner.shutdown();
  });
  LocalVideoPlayback open(String id, {bool failSave = false}) => owner.open(
    locator: id,
    initialProgress: stored[id],
    saveProgress: (position, completed) async {
      if (failSave) throw StateError('save failed');
      stored[id] = MediaProgress(
        media: SourceMediaRef(sourceId: SourceId.local, itemId: id),
        position: position,
        completed: completed,
        updatedAt: DateTime.utc(2026),
      );
    },
  );
  for (final systemBack in [false, true]) {
    testWidgets(
      '${systemBack ? 'system' : 'AppBar'} back awaits owner save and stop once',
      (tester) async {
        widgetTest = true;
        driver = FakeVideoDriver();
        owner = LocalVideoSession(driverFactory: () => driver);
        driver.stopping = Completer<void>();
        LocalVideoPlayback? playback;
        await tester.pumpWidget(
          MaterialApp(
            home: Builder(
              builder: (context) => Scaffold(
                body: TextButton(
                  onPressed: () {
                    playback = open('A');
                    Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => PlayerPage(
                          title: 'Video',
                          playback: const SizedBox(),
                          beforeExit: playback!.finish,
                        ),
                      ),
                    );
                  },
                  child: const Text('Open'),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Open'));
        await tester.pumpAndSettle();
        expect(playback!.loading, false);
        driver.positions.add(const Duration(seconds: 42));
        if (systemBack) {
          await tester.binding.handlePopRoute();
        } else {
          await tester.tap(find.byTooltip('Back'));
        }
        await tester.pump();
        await tester.binding.handlePopRoute();
        await tester.pump();
        expect(driver.stops, 1);
        expect(driver.disposals, 0);
        expect(find.text('Video'), findsOneWidget);
        expect((stored['A']!.position as VideoPosition).position.inSeconds, 42);
        driver.positions.add(const Duration(seconds: 1));
        driver.stopping!.complete();
        await tester.pumpAndSettle();
        expect(find.text('Video'), findsNothing);
        expect((stored['A']!.position as VideoPosition).position.inSeconds, 42);
        unawaited(owner.shutdown());
        await tester.pump();
      },
    );
  }
  test(
    'one player reused across 60 video/manga/video cycles without reset writes',
    () async {
      final expected = <String, Duration>{};
      for (var i = 1; i <= 60; i++) {
        final id = i % 3 == 0 ? 'B' : 'A';
        final playback = open(id);
        await playback.ready;
        expect(driver.seeks.last, expected[id] ?? Duration.zero);
        expect(stored[id]?.position, isNot(isA<PagePosition>()));
        final position = Duration(seconds: i);
        driver.positions.add(position);
        await playback.finish();
        expected[id] = position;
        for (final entry in expected.entries) {
          expect(
            (stored[entry.key]!.position as VideoPosition).position,
            entry.value,
          );
        }
        expect(driver.disposals, 0);
        // Manga route has no video session; stale native reset events are ignored.
        driver.reset();
      }
      expect(creations, 1);
      expect(driver.stops, 60);
      await owner.shutdown();
      await owner.shutdown();
      expect(driver.disposals, 1);
    },
  );
  test('open and early exit do not create zero progress', () async {
    final playback = open('A');
    await playback.ready;
    await playback.finish();
    expect(stored, isEmpty);
  });
  test('failed seek preserves saved state and next opening tracks', () async {
    var playback = open('A');
    await playback.ready;
    driver.positions.add(const Duration(seconds: 20));
    await playback.finish();
    driver.failSeek = true;
    playback = open('A');
    await playback.ready;
    expect(playback.error, isNotNull);
    driver.positions.add(const Duration(seconds: 1));
    await playback.finish();
    expect((stored['A']!.position as VideoPosition).position.inSeconds, 20);
    driver.failSeek = false;
    playback = open('A');
    await playback.ready;
    driver.positions.add(const Duration(seconds: 30));
    await playback.finish();
    expect((stored['A']!.position as VideoPosition).position.inSeconds, 30);
  });
  test(
    'close during delayed open prevents stale play and tracker mutation',
    () async {
      driver.opening = Completer<void>();
      final a = open('A');
      await Future<void>.delayed(Duration.zero);
      final closing = a.finish();
      driver.opening!.complete();
      await closing;
      expect(driver.plays, 0);
      driver.opening = null;
      final b = open('B');
      await b.ready;
      await a.finish();
      driver.positions.add(const Duration(seconds: 25));
      await b.finish();
      expect(stored.containsKey('A'), false);
      expect((stored['B']!.position as VideoPosition).position.inSeconds, 25);
    },
  );
  test('close cancels missing duration readiness', () async {
    driver.ready = false;
    final a = open('A');
    await Future<void>.delayed(Duration.zero);
    await a.finish();
    await a.ready;
    expect(driver.stops, 1);
    expect(stored, isEmpty);
  });
  test('background flush pauses without closing session', () async {
    final a = open('A');
    await a.ready;
    driver.positions.add(const Duration(seconds: 10));
    await owner.setForeground(false);
    expect(driver.pauses, 1);
    expect(driver.stops, 0);
    expect((stored['A']!.position as VideoPosition).position.inSeconds, 10);
    await owner.setForeground(true);
    driver.positions.add(const Duration(seconds: 20));
    await a.finish();
    expect((stored['A']!.position as VideoPosition).position.inSeconds, 20);
  });
  test('completion stays explicit and resets never fake replay', () async {
    var a = open('A');
    await a.ready;
    driver.positions.add(const Duration(seconds: 100));
    driver.completions.add(true);
    await a.finish();
    expect(stored['A']!.completed, true);
    a = open('A');
    await a.ready;
    expect(driver.seeks.last, Duration.zero);
    await a.finish();
    expect(stored['A']!.completed, true);
    a = open('A');
    await a.ready;
    driver.positions.add(const Duration(seconds: 5));
    await a.finish();
    expect(stored['A']!.completed, false);
  });
  test(
    'save failure still stops and duplicate finalization runs once',
    () async {
      final a = open('A', failSave: true);
      await a.ready;
      driver.positions.add(const Duration(seconds: 10));
      final finish = a.finish();
      expect(identical(finish, a.finish()), true);
      await expectLater(finish, throwsStateError);
      expect(driver.stops, 1);
      final b = open('B');
      await b.ready;
      await b.finish();
    },
  );
}
