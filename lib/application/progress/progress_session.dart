import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';

/// Progress state bound to one source-scoped media reference.
final class ProgressSession {
  ProgressSession._({
    required ProgressRepository repository,
    required this.media,
    required this.initialProgress,
  }) : _repository = repository;

  final ProgressRepository _repository;
  final SourceMediaRef media;
  final MediaProgress? initialProgress;

  static Future<ProgressSession> load({
    required ProgressRepository repository,
    required SourceMediaRef media,
  }) async =>
      ProgressSession._(
        repository: repository,
        media: media,
        initialProgress: await repository.load(media),
      );

  Future<void> save(ProgressPosition position, bool completed) =>
      _repository.save(
        MediaProgress(
          media: media,
          position: position,
          completed: completed,
          updatedAt: DateTime.now().toUtc(),
        ),
      );
}
