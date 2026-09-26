import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/progress/resume.dart';
import 'package:hikari/infrastructure/playback/video_progress.dart';

void main() {
  const ref = SourceMediaRef(sourceId: SourceId.local, itemId: 'video');
  MediaProgress saved(ProgressPosition position, {bool completed = false}) =>
      MediaProgress(
        media: ref,
        position: position,
        completed: completed,
        updatedAt: DateTime.utc(2026),
      );
  test('resume clamps current content; completed content restarts', () {
    final video = saved(
      VideoPosition(
        position: const Duration(seconds: 80),
        duration: const Duration(seconds: 100),
      ),
    );
    expect(
      resumeVideo(video, const Duration(seconds: 50)),
      const Duration(seconds: 50),
    );
    expect(
      resumeVideo(
        saved(video.position, completed: true),
        const Duration(seconds: 100),
      ),
      Duration.zero,
    );
    final page = saved(PagePosition(pageIndex: 8, pageCount: 10));
    expect(resumePage(page, 3), 2);
    expect(resumePage(page, 0), 0);
    expect(resumePage(saved(page.position, completed: true), 3), 0);
    expect(resumeText(saved(TextPosition(progression: 0.7))), 0.7);
    expect(resumeText(saved(TextPosition(progression: 1), completed: true)), 0);
  });
  test('text progress uses current extent, finite normalized position and true end', () {
    expect(textProgression(25, 100), 0.25);
    expect(textProgression(-10, 100), 0);
    expect(textProgression(110, 100), 1);
    expect(textProgression(0, 0), 0);
    expect(textAtEnd(98, 100), isFalse);
    expect(textAtEnd(99.8, 100), isTrue);
    expect(textAtEnd(0, 0), isFalse);
  });
  test(
    'video progress throttles changed states and completion is explicit',
    () {
      final tracker = VideoProgressTracker();
      final start = DateTime.utc(2026);
      expect(
        tracker.capture(
          position: Duration.zero,
          duration: const Duration(seconds: 10),
          now: start,
          force: true,
        ),
        isNull,
      );
      expect(
        tracker.capture(
          position: const Duration(seconds: 1),
          duration: const Duration(seconds: 10),
          now: start,
        ),
        isNotNull,
      );
      expect(
        tracker.capture(
          position: const Duration(seconds: 3),
          duration: const Duration(seconds: 10),
          now: start.add(const Duration(seconds: 2)),
        ),
        isNull,
      );
      expect(
        tracker.capture(
          position: const Duration(seconds: 6),
          duration: const Duration(seconds: 10),
          now: start.add(const Duration(seconds: 5)),
        ),
        isNotNull,
      );
      final nearEnd = tracker.capture(
        position: const Duration(seconds: 10),
        duration: const Duration(seconds: 10),
        now: start.add(const Duration(seconds: 6)),
        force: true,
      )!;
      expect(nearEnd.completed, isFalse);
      tracker.completed = true;
      expect(
        tracker
            .capture(
              position: const Duration(seconds: 10),
              duration: const Duration(seconds: 10),
              now: start,
              force: true,
            )!
            .completed,
        isTrue,
      );
      tracker.notePosition(
        const Duration(seconds: 1),
        const Duration(seconds: 10),
      );
      expect(tracker.completed, isFalse);
      expect(
        tracker.capture(
          position: const Duration(seconds: 1),
          duration: const Duration(seconds: 10),
          now: start,
          force: true,
        ),
        isNotNull,
      );
      expect(
        tracker.capture(
          position: const Duration(seconds: 1),
          duration: const Duration(seconds: 10),
          now: start,
          force: true,
        ),
        isNull,
      );
    },
  );
}
