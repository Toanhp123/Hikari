import 'dart:async';

import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/resume.dart';
import 'package:hikari/infrastructure/playback/video_progress.dart';
import 'package:media_kit_video/media_kit_video.dart';

class LocalVideo extends StatefulWidget {
  const LocalVideo({
    super.key,
    required this.locator,
    this.initialProgress,
    required this.progress,
  });

  final MediaProgress? initialProgress;
  final VideoProgressTracker progress;

  final String locator;

  @override
  State<LocalVideo> createState() => _LocalVideoState();
}

class _LocalVideoState extends State<LocalVideo> with WidgetsBindingObserver {
  Player? _player;
  VideoController? _controller;
  StreamSubscription<String>? _errors;
  String? _error;
  bool _opening = true;
  bool _restored = false;
  bool _restoring = false;
  bool _foreground = true;
  VideoProgressTracker get _progress => widget.progress;
  final _subscriptions = <StreamSubscription<Object?>>[];
  Timer? _timer;

  Future<void> _restore(Duration duration) async {
    if (!mounted ||
        _progress.exiting ||
        _opening ||
        _restored ||
        _restoring ||
        duration <= Duration.zero ||
        _player == null) {
      return;
    }
    _restoring = true;
    try {
      final position = resumeVideo(widget.initialProgress, duration);
      await _player!.seek(position);
      if (!mounted || _progress.exiting) return;
      _progress.notePosition(position, duration);
      _restored = true;
      if (_foreground) await _player!.play();
    } catch (error) {
      _restored = false;
      if (mounted && !_progress.exiting) {
        setState(() => _error = error.toString());
      }
    } finally {
      _restoring = false;
    }
  }

  Future<void> _flush({bool force = true}) async {
    if (!_restored || _opening || _progress.exiting) return;
    try {
      await _progress.flush(force: force);
    } catch (_) {
      if (mounted && !_progress.exiting) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not save playback progress.')),
        );
      }
    }
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _open();
  }

  Future<void> _open() async {
    try {
      MediaKit.ensureInitialized();
      final player = _player ??= Player();
      _controller ??= VideoController(player);
      _errors ??= player.stream.error.listen((message) {
        if (mounted && !_progress.exiting) {
          setState(() {
            _error = message;
            _opening = false;
          });
        }
      });
      if (_subscriptions.isEmpty) {
        _subscriptions.add(
          player.stream.duration.listen((duration) {
            _progress.noteDuration(duration);
            unawaited(_restore(duration));
          }),
        );
        _subscriptions.add(
          player.stream.position.listen((position) {
            if (_restored) {
              _progress.notePosition(position, _progress.duration);
            }
          }),
        );
        _subscriptions.add(
          player.stream.completed.listen((completed) {
            if (!_restored || !completed || _progress.exiting) return;
            _progress.completed = true;
            unawaited(_flush());
          }),
        );
        _subscriptions.add(
          player.stream.playing.listen((playing) {
            if (!playing) unawaited(_flush());
          }),
        );
        _timer = Timer.periodic(
          const Duration(seconds: 5),
          (_) => unawaited(_flush(force: false)),
        );
      }
      _restored = false;
      await player.open(Media(widget.locator), play: false);
      if (!mounted || _progress.exiting) return;
      setState(() => _opening = false);
      await _restore(_progress.duration);
    } catch (error) {
      if (mounted && !_progress.exiting) {
        setState(() {
          _error = error.toString();
          _opening = false;
        });
      }
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (_progress.exiting) return;
    _foreground = state == AppLifecycleState.resumed;
    if (!_foreground) {
      final player = _player;
      unawaited(_flush());
      if (player != null) unawaited(player.pause());
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    // Forced widget removal is best-effort; normal route exits await finish first.
    unawaited(_progress.finish().catchError((Object _) {}));
    _timer?.cancel();
    for (final subscription in _subscriptions) {
      unawaited(subscription.cancel());
    }
    unawaited(_errors?.cancel());
    final player = _player;
    if (player != null) unawaited(player.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_error != null) {
      return Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Could not play this video.'),
              const SizedBox(height: 8),
              Text(_error!),
              TextButton(
                onPressed: _opening
                    ? null
                    : () {
                        setState(() {
                          _opening = true;
                          _error = null;
                        });
                        _open();
                      },
                child: const Text('Try again'),
              ),
            ],
          ),
        ),
      );
    }
    final controller = _controller;
    if (controller == null) {
      return const Center(child: CircularProgressIndicator());
    }
    return Stack(
      fit: StackFit.expand,
      children: [
        Video(controller: controller),
        if (_opening) const Center(child: CircularProgressIndicator()),
      ],
    );
  }
}
