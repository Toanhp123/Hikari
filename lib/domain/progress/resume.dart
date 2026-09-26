import 'package:hikari/domain/progress/progress.dart';

Duration resumeVideo(MediaProgress? saved, Duration duration) {
  if (saved == null || saved.completed || duration <= Duration.zero) {
    return Duration.zero;
  }
  final position = saved.position;
  return position is VideoPosition
      ? Duration(
          microseconds: position.position.inMicroseconds.clamp(
            0,
            duration.inMicroseconds,
          ),
        )
      : Duration.zero;
}

int resumePage(MediaProgress? saved, int pageCount) {
  if (saved == null || saved.completed || pageCount <= 0) return 0;
  final position = saved.position;
  return position is PagePosition
      ? position.pageIndex.clamp(0, pageCount - 1)
      : 0;
}

double resumeText(MediaProgress? saved) {
  if (saved == null || saved.completed) return 0;
  final position = saved.position;
  return position is TextPosition ? position.progression : 0;
}

double textProgression(double offset, double extent) =>
    !offset.isFinite || !extent.isFinite || extent <= 0
    ? 0
    : (offset / extent).clamp(0, 1);

bool textAtEnd(double offset, double extent) =>
    offset.isFinite && extent.isFinite && extent > 0 && offset >= extent - 0.5;
