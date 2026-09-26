import 'package:hikari/domain/progress/progress.dart';

/// Event calculations separated from native decoding for deterministic tests.
final class VideoProgressTracker {
  VideoProgressTracker({this.saveProgress});

  final Future<void> Function(VideoPosition, bool)? saveProgress;
  bool completed = false;
  bool exiting = false;
  Duration _position = Duration.zero;
  Duration _duration = Duration.zero;
  Future<void> _pending = Future<void>.value();
  Future<void>? _finalWrite;
  DateTime? _lastWrite;
  (int, int, bool)? _lastValue;

  void noteDuration(Duration duration) {
    if (!exiting && duration > Duration.zero) _duration = duration;
  }

  void notePosition(Duration position, Duration duration) {
    if (exiting || position.isNegative || duration <= Duration.zero) return;
    _position = position;
    _duration = duration;
    if (position > Duration.zero && position < duration) completed = false;
  }

  Duration get duration => _duration;

  Future<void> flush({bool force = true, DateTime? now}) {
    if (exiting) return Future<void>.value();
    return _write(force: force, now: now);
  }

  Future<void> finish() {
    if (_finalWrite != null) return _finalWrite!;
    exiting = true;
    return _finalWrite = _write(force: true);
  }

  Future<void> _write({required bool force, DateTime? now}) {
    final value = capture(
      position: _position,
      duration: _duration,
      now: now ?? DateTime.now().toUtc(),
      force: force,
      finalSample: exiting,
    );
    // Queue immutable samples so an older in-flight write cannot finish last.
    final write = _pending.then((_) async {
      if (value != null) {
        await saveProgress?.call(value.position, value.completed);
      }
    });
    _pending = write.catchError((Object error) {
      retry();
    });
    return write;
  }

  ({VideoPosition position, bool completed})? capture({
    required Duration position,
    required Duration duration,
    required DateTime now,
    bool force = false,
    bool finalSample = false,
  }) {
    if (duration <= Duration.zero || position.isNegative) return null;
    if (_lastValue == null && position == Duration.zero && !completed) {
      return null;
    }
    final value = (
      position.inMilliseconds.clamp(0, duration.inMilliseconds),
      duration.inMilliseconds,
      completed,
    );
    if (!finalSample && value == _lastValue) return null;
    if (!force &&
        _lastWrite != null &&
        now.difference(_lastWrite!) < const Duration(seconds: 5)) {
      return null;
    }
    _lastWrite = now;
    _lastValue = value;
    return (
      position: VideoPosition(
        position: Duration(milliseconds: value.$1),
        duration: duration,
      ),
      completed: completed,
    );
  }

  void retry() {
    _lastValue = null;
    _lastWrite = null;
  }
}
