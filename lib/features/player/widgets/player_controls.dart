import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/domain/playback/video_playback.dart';

class PlayerControls extends StatefulWidget {
  const PlayerControls({
    super.key,
    required this.controls,
    required this.onTogglePlayPause,
    required this.onSeekRelative,
    required this.onSeekToProgress,
    this.onNextEpisode,
  });

  final VideoPlaybackControls controls;
  final VoidCallback onTogglePlayPause;
  final ValueChanged<int> onSeekRelative;
  final ValueChanged<double> onSeekToProgress;
  final VoidCallback? onNextEpisode;

  @override
  State<PlayerControls> createState() => _PlayerControlsState();
}

class _PlayerControlsState extends State<PlayerControls> {
  double? _dragProgress;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final controls = widget.controls;
    final duration = controls.duration;
    final progress =
        _dragProgress ??
        (duration > Duration.zero
            ? (controls.position.inMicroseconds / duration.inMicroseconds)
                  .clamp(0.0, 1.0)
            : 0.0);

    return Stack(
      fit: StackFit.expand,
      children: [
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
                onPressed: () => widget.onSeekRelative(-10),
              ),
              const SizedBox(width: HikariSpacing.xl),
              HikariIconButton(
                icon: Icon(
                  controls.playing
                      ? Icons.pause_rounded
                      : Icons.play_arrow_rounded,
                ),
                tooltip: controls.playing ? 'Pause' : 'Play',
                size: 64,
                iconSize: 40,
                variant: HikariIconButtonVariant.primary,
                onPressed: widget.onTogglePlayPause,
              ),
              const SizedBox(width: HikariSpacing.xl),
              HikariIconButton(
                icon: const Icon(Icons.forward_10_rounded),
                tooltip: 'Forward 10s',
                size: 52,
                iconSize: 32,
                variant: HikariIconButtonVariant.glass,
                onPressed: () => widget.onSeekRelative(10),
              ),
            ],
          ),
        ),
        Positioned(
          bottom: 0,
          left: 0,
          right: 0,
          child: ColoredBox(
            color: colors.background.withValues(alpha: 0.88),
            child: Padding(
              padding: const EdgeInsets.symmetric(
                horizontal: HikariSpacing.lg,
                vertical: HikariSpacing.sm,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Slider(
                    value: progress,
                    onChanged: duration <= Duration.zero
                        ? null
                        : (value) => setState(() => _dragProgress = value),
                    onChangeEnd: duration <= Duration.zero
                        ? null
                        : (value) {
                            setState(() => _dragProgress = null);
                            widget.onSeekToProgress(value);
                          },
                  ),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        '${_formatDuration(controls.position)} / ${_formatDuration(duration)}',
                        style: TextStyle(
                          fontSize: 12,
                          color: colors.textSecondary,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      if (widget.onNextEpisode != null)
                        TextButton.icon(
                          onPressed: widget.onNextEpisode,
                          icon: const Icon(Icons.skip_next_rounded),
                          label: const Text('Next'),
                        ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

String _formatDuration(Duration duration) {
  final hours = duration.inHours;
  final minutes = duration.inMinutes.remainder(60).toString().padLeft(2, '0');
  final seconds = duration.inSeconds.remainder(60).toString().padLeft(2, '0');
  return hours > 0 ? '$hours:$minutes:$seconds' : '$minutes:$seconds';
}
