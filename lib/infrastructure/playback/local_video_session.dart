import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/resume.dart';
import 'package:hikari/infrastructure/playback/src/video_driver.dart';
import 'package:hikari/infrastructure/playback/video_progress.dart';
import 'package:media_kit_video/media_kit_video.dart';

enum _Phase { resetting, opening, active, failed, closing, closed }

/// A route's identity and frozen progress, not a native player owner.
final class LocalVideoPlayback extends ChangeNotifier {
  LocalVideoPlayback._(
    this._owner,
    this.locator,
    this.initialProgress,
    this._tracker,
  );
  final LocalVideoSession _owner;
  final String locator;
  final MediaProgress? initialProgress;
  final VideoProgressTracker _tracker;
  _Phase _phase = _Phase.resetting;
  final _duration = Completer<Duration?>();
  late Future<void> ready;
  Future<void>? _finish;
  String? error;
  bool get loading => _phase == _Phase.resetting || _phase == _Phase.opening;
  VideoController? get controller => _owner._driver?.controller;
  Future<void> finish() => _finish ??= _owner._finish(this);
  void _changed() => notifyListeners();
}

/// One app-owned native player. Route identities fence all async continuations.
final class LocalVideoSession {
  LocalVideoSession({VideoDriver Function()? driverFactory})
    : _driverFactory = driverFactory ?? MediaKitVideoDriver.new;
  final VideoDriver Function() _driverFactory;
  VideoDriver? _driver;
  LocalVideoPlayback? _current;
  final _subscriptions = <StreamSubscription<Object?>>[];
  Future<void> _commands = Future<void>.value();
  Future<void>? _shutdown;
  Timer? _timer;
  bool _foreground = true;

  Future<void> _serialize(Future<void> Function() action) {
    final result = _commands.then((_) => action());
    _commands = result.catchError((Object _) {});
    return result;
  }

  bool _is(LocalVideoPlayback session, _Phase phase) =>
      identical(_current, session) && session._phase == phase;

  void _initialize() {
    if (_driver != null) return;
    final driver = _driver = _driverFactory();
    _subscriptions.add(
      driver.duration.listen((duration) {
        final session = _current;
        if (session == null) return;
        if (duration == Duration.zero && session._phase == _Phase.resetting) {
          session._phase = _Phase.opening;
          return;
        }
        if (duration <= Duration.zero) return;
        if (session._phase == _Phase.opening &&
            !session._duration.isCompleted) {
          session._duration.complete(duration);
        } else if (session._phase == _Phase.active) {
          session._tracker.noteDuration(duration);
        }
      }),
    );
    _subscriptions.add(
      driver.position.listen((position) {
        final session = _current;
        // Zero is also mpv's unload/reset value, not evidence of user playback.
        if (session == null ||
            session._phase != _Phase.active ||
            position <= Duration.zero) {
          return;
        }
        session._tracker.notePosition(position, session._tracker.duration);
      }),
    );
    _subscriptions.add(
      driver.completed.listen((completed) {
        final session = _current;
        if (session == null || session._phase != _Phase.active || !completed) {
          return;
        }
        session._tracker.completed = true;
        unawaited(_flush(session));
      }),
    );
    _subscriptions.add(
      driver.playing.listen((playing) {
        final session = _current;
        if (session != null && !playing) unawaited(_flush(session));
      }),
    );
    _subscriptions.add(
      driver.error.listen((message) {
        final session = _current;
        if (session == null ||
            session._phase == _Phase.closing ||
            session._phase == _Phase.closed) {
          return;
        }
        session.error = message;
        session._changed();
      }),
    );
    _timer = Timer.periodic(const Duration(seconds: 5), (_) {
      final session = _current;
      if (session != null) unawaited(_flush(session, force: false));
    });
  }

  LocalVideoPlayback open({
    required String locator,
    MediaProgress? initialProgress,
    required Future<void> Function(VideoPosition, bool) saveProgress,
  }) {
    if (_shutdown != null) throw StateError('Playback owner is shut down.');
    if (_current != null) {
      throw StateError('Finish current playback before opening another.');
    }
    _initialize();
    final session = LocalVideoPlayback._(
      this,
      locator,
      initialProgress,
      VideoProgressTracker(saveProgress: saveProgress),
    );
    _current = session;
    session.ready = _serialize(() async {
      try {
        if (!_is(session, _Phase.resetting)) return;
        await _driver!.open(locator);
        if (!identical(_current, session) || !session.loading) return;
        // Broadcast distinct streams may suppress a repeated zero after stop.
        if (session._phase == _Phase.resetting) session._phase = _Phase.opening;
        final duration = await session._duration.future.timeout(
          const Duration(seconds: 15),
        );
        if (duration == null || !_is(session, _Phase.opening)) return;
        await _driver!.seek(resumeVideo(initialProgress, duration));
        if (!_is(session, _Phase.opening)) return;
        session._tracker.noteDuration(duration);
        session._phase = _Phase.active;
        session._changed();
        if (_foreground) await _driver!.play();
      } catch (error) {
        if (!_is(session, _Phase.resetting) &&
            !_is(session, _Phase.opening) &&
            !_is(session, _Phase.active)) {
          return;
        }
        session._phase = _Phase.failed;
        session.error = error.toString();
        session._changed();
      }
    });
    return session;
  }

  Future<void> _flush(LocalVideoPlayback session, {bool force = true}) async {
    if (!_is(session, _Phase.active)) return;
    try {
      await session._tracker.flush(force: force);
    } catch (_) {
      if (!_is(session, _Phase.active)) return;
      session.error = 'Could not save playback progress.';
      session._changed();
    }
  }

  Future<void> _finish(LocalVideoPlayback session) {
    if (!identical(_current, session)) return Future<void>.value();
    session._phase = _Phase.closing;
    if (!session._duration.isCompleted) session._duration.complete(null);
    // Freeze immediately, before any native pause/stop reset can arrive.
    final saved = session._tracker.finish();
    // Attach a handler now, even while an earlier native operation is pending.
    Object? saveError;
    final saving = saved.catchError((Object error) {
      saveError = error;
    });
    return _serialize(() async {
      try {
        await saving;
        await _driver!.stop();
        if (saveError != null) throw saveError!;
      } finally {
        session._phase = _Phase.closed;
        if (identical(_current, session)) _current = null;
      }
    });
  }

  Future<void> setForeground(bool foreground) {
    _foreground = foreground;
    final session = _current;
    if (foreground || session == null) return Future<void>.value();
    final saving = _flush(session);
    return _serialize(() async {
      await saving;
      if (_is(session, _Phase.active)) await _driver!.pause();
    });
  }

  Future<void> shutdown() => _shutdown ??= _closeOwner();

  Future<void> _closeOwner() async {
    _timer?.cancel();
    try {
      final current = _current;
      if (current != null) await current.finish();
    } finally {
      await _serialize(() async {
        for (final subscription in _subscriptions) {
          await subscription.cancel();
        }
        _subscriptions.clear();
        await _driver?.dispose();
      });
    }
  }
}
