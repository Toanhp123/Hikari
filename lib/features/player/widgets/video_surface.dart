import 'package:flutter/material.dart';
import 'package:media_kit_video/media_kit_video.dart';

/// Renders the current media-kit output while playback ownership stays outside UI.
class VideoSurface extends StatelessWidget {
  const VideoSurface({
    super.key,
    required this.changes,
    required this.controller,
    required this.loading,
    required this.error,
  });

  final Stream<void> changes;
  final VideoController? Function() controller;
  final bool Function() loading;
  final String? Function() error;

  @override
  Widget build(BuildContext context) => StreamBuilder<void>(
    stream: changes,
    builder: (context, _) {
      final videoController = controller();
      final playbackError = error();
      return Stack(
        fit: StackFit.expand,
        children: [
          if (videoController != null) Video(controller: videoController),
          if (loading()) const Center(child: CircularProgressIndicator()),
          if (playbackError != null)
            Center(
              child: Material(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Text('$playbackError\nLeave and reopen to try again.'),
                ),
              ),
            ),
        ],
      );
    },
  );
}
