/// Platform-independent controls exposed by an active video playback.
///
/// Rendering the native video surface remains infrastructure-specific. Feature
/// UI depends only on this small state/command contract.
abstract interface class VideoPlaybackControls {
  Stream<void> get changes;
  bool get playing;
  Duration get position;
  Duration get duration;

  Future<void> play();
  Future<void> pause();
  Future<void> seek(Duration position);
}
