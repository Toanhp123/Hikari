import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';

/// Cinematic Video Player screen with auto-fading overlay controls,
/// scrubber slider, 10s skip buttons, and playback settings.
class PlayerPage extends StatefulWidget {
  const PlayerPage({
    super.key,
    required this.title,
    required this.playback,
    this.beforeExit,
    this.onNextEpisode,
  });

  final String title;
  final Widget playback;
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
  bool _isPlaying = true;
  double _playbackSpeed = 1.0;
  double _scrubberPosition = 0.35; // mock progress scrubber
  Duration _currentPosition = const Duration(minutes: 8, seconds: 24);
  final Duration _totalDuration = const Duration(minutes: 24, seconds: 0);

  @override
  void initState() {
    super.initState();
    _startHideTimer();
  }

  void _startHideTimer() {
    _hideTimer?.cancel();
    _hideTimer = Timer(const Duration(seconds: 3), () {
      if (mounted && _isPlaying) {
        setState(() => _showControls = false);
      }
    });
  }

  void _toggleControls() {
    setState(() => _showControls = !_showControls);
    if (_showControls && _isPlaying) {
      _startHideTimer();
    }
  }

  void _togglePlayPause() {
    setState(() => _isPlaying = !_isPlaying);
    if (_isPlaying) {
      _startHideTimer();
    } else {
      _hideTimer?.cancel();
    }
  }

  void _seekRelative(int seconds) {
    setState(() {
      final newSeconds = (_currentPosition.inSeconds + seconds).clamp(
        0,
        _totalDuration.inSeconds,
      );
      _currentPosition = Duration(seconds: newSeconds);
      _scrubberPosition = _totalDuration.inSeconds > 0
          ? newSeconds / _totalDuration.inSeconds
          : 0.0;
    });
    if (_isPlaying) _startHideTimer();
  }

  void _showSettingsModal() {
    _hideTimer?.cancel();
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: context.hikariColors.surfaceElevated,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            final colors = context.hikariColors;
            return Padding(
              padding: const EdgeInsets.all(HikariSpacing.lg),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Playback Settings',
                    style: HikariTypography.titleMedium.copyWith(
                      color: colors.textPrimary,
                    ),
                  ),
                  const SizedBox(height: HikariSpacing.md),
                  Text(
                    'Playback Speed',
                    style: TextStyle(fontSize: 12, color: colors.textSecondary),
                  ),
                  const SizedBox(height: HikariSpacing.xs),
                  Wrap(
                    spacing: HikariSpacing.sm,
                    children: [0.75, 1.0, 1.25, 1.5, 2.0].map((speed) {
                      final selected = _playbackSpeed == speed;
                      return ChoiceChip(
                        label: Text('${speed}x'),
                        selected: selected,
                        onSelected: (val) {
                          if (val) {
                            setState(() => _playbackSpeed = speed);
                            setModalState(() {});
                          }
                        },
                      );
                    }).toList(),
                  ),
                  const SizedBox(height: HikariSpacing.lg),
                ],
              ),
            );
          },
        );
      },
    ).then((_) {
      if (_isPlaying) _startHideTimer();
    });
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
    // PopScope must publish eligibility before the final pop notification.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && ModalRoute.of(context)!.isCurrent) {
        Navigator.of(context).pop();
      }
    });
  }

  @override
  void dispose() {
    _hideTimer?.cancel();
    if (!_exiting) {
      unawaited(widget.beforeExit?.call().catchError((Object _) {}));
    }
    super.dispose();
  }

  String _formatDuration(Duration d) {
    final minutes = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = d.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;

    return PopScope<void>(
      canPop: _canPop,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _exit();
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        // AppBar with title ensures backward compatibility for tests finding title or AppBar back
        appBar: AppBar(
          title: Text(widget.title),
          backgroundColor: Colors.black,
          leading: BackButton(onPressed: _exit),
          actions: [
            HikariIconButton(
              icon: const Icon(Icons.tune_rounded),
              tooltip: 'Settings',
              onPressed: _showSettingsModal,
            ),
          ],
        ),
        body: SafeArea(
          child: Stack(
            fit: StackFit.expand,
            children: [
              // Player Surface (RepaintBoundary optimized)
              Center(child: RepaintBoundary(child: widget.playback)),

              // Tap detection overlay
              Positioned.fill(
                child: GestureDetector(
                  onTap: _toggleControls,
                  behavior: HitTestBehavior.translucent,
                  child: const SizedBox.expand(),
                ),
              ),

              // Center Transport Controls (10s skip, Play/Pause, Next)
              if (_showControls)
                Center(
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      HikariIconButton(
                        icon: const Icon(Icons.replay_10_rounded),
                        tooltip: 'Rewind 10s',
                        size: 52,
                        iconSize: 32,
                        variant: HikariIconButtonVariant.glass,
                        onPressed: () => _seekRelative(-10),
                      ),
                      const SizedBox(width: HikariSpacing.xl),
                      HikariIconButton(
                        icon: Icon(
                          _isPlaying
                              ? Icons.pause_rounded
                              : Icons.play_arrow_rounded,
                        ),
                        tooltip: _isPlaying ? 'Pause' : 'Play',
                        size: 64,
                        iconSize: 40,
                        variant: HikariIconButtonVariant.primary,
                        onPressed: _togglePlayPause,
                      ),
                      const SizedBox(width: HikariSpacing.xl),
                      HikariIconButton(
                        icon: const Icon(Icons.forward_10_rounded),
                        tooltip: 'Forward 10s',
                        size: 52,
                        iconSize: 32,
                        variant: HikariIconButtonVariant.glass,
                        onPressed: () => _seekRelative(10),
                      ),
                      if (widget.onNextEpisode != null) ...[
                        const SizedBox(width: HikariSpacing.md),
                        HikariIconButton(
                          icon: const Icon(Icons.skip_next_rounded),
                          tooltip: 'Next Episode',
                          size: 52,
                          iconSize: 28,
                          variant: HikariIconButtonVariant.glass,
                          onPressed: widget.onNextEpisode,
                        ),
                      ],
                    ],
                  ),
                ),

              // Bottom Scrubber Bar & Timers
              if (_showControls)
                Positioned(
                  bottom: 0,
                  left: 0,
                  right: 0,
                  child: ClipRect(
                    child: BackdropFilter(
                      filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: HikariSpacing.lg,
                          vertical: HikariSpacing.sm,
                        ),
                        decoration: BoxDecoration(
                          color: colors.background.withValues(alpha: 0.8),
                          border: Border(
                            top: BorderSide(color: colors.borderSubtle),
                          ),
                        ),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            SliderTheme(
                              data: SliderThemeData(
                                activeTrackColor: colors.primary,
                                inactiveTrackColor: colors.surfaceHighlight,
                                thumbColor: colors.primaryGlow,
                                trackHeight: 3.5,
                                thumbShape: const RoundSliderThumbShape(
                                  enabledThumbRadius: 6,
                                ),
                              ),
                              child: Slider(
                                value: _scrubberPosition,
                                onChanged: (val) {
                                  setState(() {
                                    _scrubberPosition = val;
                                    _currentPosition = Duration(
                                      seconds: (val * _totalDuration.inSeconds)
                                          .toInt(),
                                    );
                                  });
                                },
                              ),
                            ),
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Text(
                                  '${_formatDuration(_currentPosition)} / ${_formatDuration(_totalDuration)}',
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: colors.textSecondary,
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                                Row(
                                  children: [
                                    HikariIconButton(
                                      icon: const Icon(
                                        Icons.picture_in_picture_alt_rounded,
                                      ),
                                      size: 36,
                                      iconSize: 18,
                                      tooltip: 'Picture in Picture',
                                      onPressed: () {},
                                    ),
                                    HikariIconButton(
                                      icon: const Icon(
                                        Icons.fullscreen_rounded,
                                      ),
                                      size: 36,
                                      iconSize: 20,
                                      tooltip: 'Fullscreen',
                                      onPressed: () {},
                                    ),
                                  ],
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
