import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';

final class ContinueReadingItem {
  const ContinueReadingItem({
    required this.media,
    required this.progress,
    this.position,
    this.chapter,
    this.completed = false,
  });

  final Media media;
  final double progress;
  final ProgressPosition? position;
  final SourceMediaRef? chapter;
  final bool completed;
}
