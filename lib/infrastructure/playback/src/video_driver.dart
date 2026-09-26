import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';

/// Infrastructure-local seam for exercising native lifecycle ordering.
abstract interface class VideoDriver {
  VideoController? get controller;
  Stream<Duration> get position;
  Stream<Duration> get duration;
  Stream<bool> get completed;
  Stream<bool> get playing;
  Stream<String> get error;
  Future<void> open(String locator);
  Future<void> seek(Duration position);
  Future<void> play();
  Future<void> pause();
  Future<void> stop();
  Future<void> dispose();
}

final class MediaKitVideoDriver implements VideoDriver {
  final Player _player = Player();
  @override
  late final VideoController controller = VideoController(_player);
  MediaKitVideoDriver() {
    // Attach once; media_kit completes platform setup after a Flutter frame.
    controller;
  }
  @override
  Stream<Duration> get position => _player.stream.position;
  @override
  Stream<Duration> get duration => _player.stream.duration;
  @override
  Stream<bool> get completed => _player.stream.completed;
  @override
  Stream<bool> get playing => _player.stream.playing;
  @override
  Stream<String> get error => _player.stream.error;
  @override
  Future<void> open(String locator) =>
      _player.open(Media(locator), play: false);
  @override
  Future<void> seek(Duration position) => _player.seek(position);
  @override
  Future<void> play() => _player.play();
  @override
  Future<void> pause() => _player.pause();
  @override
  Future<void> stop() => _player.stop();
  @override
  Future<void> dispose() => _player.dispose();
}
