import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/domain/playback/video_playback.dart';
import 'package:hikari/features/player/widgets/player_controls.dart';

/// Video route chrome driven by the active playback session.
class PlayerPage extends StatefulWidget {
  const PlayerPage({
    super.key,
    required this.title,
    required this.playback,
    this.controls,
    this.beforeExit,
    this.onNextEpisode,
  });

  final String title;
  final Widget playback;
  final VideoPlaybackControls? controls;
  final Future<void> Function()? beforeExit;
  final VoidCallback? onNextEpisode;

  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends State<PlayerPage> {
  bool _exiting = false;
  bool _canPop = false;
  bool _showControls = true;
  Timer? _hideTimer;
  StreamSubscription<void>? _controlsSubscription;
  bool? _lastPlaying;

  @override
  void initState() {
    super.initState();
    final controls = widget.controls;
    _lastPlaying = controls?.playing;
    _controlsSubscription = controls?.changes.listen((_) {
      if (!mounted || controls.playing == _lastPlaying) return;
      _lastPlaying = controls.playing;
      if (controls.playing) {
        if (_showControls) _restartHideTimer();
      } else {
        _hideTimer?.cancel();
      }
    });
    _restartHideTimer();
  }

  void _restartHideTimer() {
    _hideTimer?.cancel();
    final controls = widget.controls;
    if (controls == null || !controls.playing) return;
    _hideTimer = Timer(const Duration(seconds: 3), () {
      if (mounted) setState(() => _showControls = false);
    });
  }

  void _toggleControls() {
    setState(() => _showControls = !_showControls);
    if (_showControls) _restartHideTimer();
  }

  Future<void> _togglePlayPause() async {
    final controls = widget.controls;
    if (controls == null) return;
    try {
      if (controls.playing) {
        await controls.pause();
        _hideTimer?.cancel();
      } else {
        await controls.play();
        _restartHideTimer();
      }
    } catch (_) {
      _showPlaybackError();
    }
  }

  Future<void> _seekRelative(int seconds) async {
    final controls = widget.controls;
    if (controls == null) return;
    try {
      await controls.seek(controls.position + Duration(seconds: seconds));
      _restartHideTimer();
    } catch (_) {
      _showPlaybackError();
    }
  }

  Future<void> _seekToProgress(double progress) async {
    final controls = widget.controls;
    if (controls == null || controls.duration <= Duration.zero) return;
    try {
      await controls.seek(
        Duration(
          microseconds: (controls.duration.inMicroseconds * progress).round(),
        ),
      );
      _restartHideTimer();
    } catch (_) {
      _showPlaybackError();
    }
  }

  void _showPlaybackError() {
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Could not update playback.')));
  }

  Future<void> _exit() async {
    if (_exiting) return;
    _exiting = true;
    _hideTimer?.cancel();
    try {
      await widget.beforeExit?.call();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not save playback progress.')),
        );
      }
    }
    if (!mounted) return;
    setState(() => _canPop = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && ModalRoute.of(context)!.isCurrent) {
        Navigator.of(context).pop();
      }
    });
  }

  @override
  void dispose() {
    _hideTimer?.cancel();
    unawaited(_controlsSubscription?.cancel());
    if (!_exiting) {
      unawaited(widget.beforeExit?.call().catchError((Object _) {}));
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controls = widget.controls;

    return PopScope<void>(
      canPop: _canPop,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _exit();
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        appBar: AppBar(
          title: Text(widget.title),
          backgroundColor: Colors.black,
          leading: BackButton(onPressed: _exit),
        ),
        body: SafeArea(
          child: Stack(
            fit: StackFit.expand,
            children: [
              Center(child: RepaintBoundary(child: widget.playback)),
              if (controls != null) ...[
                Positioned.fill(
                  child: GestureDetector(
                    onTap: _toggleControls,
                    behavior: HitTestBehavior.translucent,
                    child: const SizedBox.expand(),
                  ),
                ),
                if (_showControls)
                  StreamBuilder<void>(
                    stream: controls.changes,
                    builder: (context, _) => PlayerControls(
                      controls: controls,
                      onTogglePlayPause: () => unawaited(_togglePlayPause()),
                      onSeekRelative: (seconds) =>
                          unawaited(_seekRelative(seconds)),
                      onSeekToProgress: (progress) =>
                          unawaited(_seekToProgress(progress)),
                      onNextEpisode: widget.onNextEpisode,
                    ),
                  ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
