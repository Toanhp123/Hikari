import 'package:flutter/material.dart';
import 'package:hikari/infrastructure/playback/local_video_session.dart';
import 'package:media_kit_video/media_kit_video.dart';

/// Presentation only. Native resources belong to the app's session owner.
class LocalVideo extends StatelessWidget {
  const LocalVideo({super.key, required this.playback});
  final LocalVideoPlayback playback;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: playback,
    builder: (context, _) {
      final controller = playback.controller;
      return Stack(
        fit: StackFit.expand,
        children: [
          if (controller != null) Video(controller: controller),
          if (playback.loading)
            const Center(child: CircularProgressIndicator()),
          if (playback.error != null)
            Center(
              child: Material(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Text(
                    '${playback.error}\nLeave and reopen to try again.',
                  ),
                ),
              ),
            ),
        ],
      );
    },
  );
}
