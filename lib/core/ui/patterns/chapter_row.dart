import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';

class ChapterRow extends StatelessWidget {
  const ChapterRow({
    super.key,
    required this.chapter,
    required this.sourceName,
    required this.enabled,
    required this.onTap,
    this.isContinuation = false,
  });

  final bool isContinuation;
  final Object chapter;
  final String sourceName;
  final bool enabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final (
      SourceMediaRef ref,
      String title,
      String subtitle,
      bool readable,
    ) = switch (chapter) {
      MangaChapter c => (
        c.source,
        c.title,
        <String>[
          c.scanlator ?? sourceName,
          if (c.chapterNumber != null) 'Chapter ${c.chapterNumber}',
          if (c.uploadedAt != null)
            c.uploadedAt!.toIso8601String().split('T').first,
          if (!c.canReadPages) 'Not readable in Hikari',
        ].join(' · '),
        c.canReadPages,
      ),
      NovelChapter c => (
        c.source,
        c.title,
        <String>[
          if (c.chapterNumber != null) 'Chapter ${c.chapterNumber}',
          ...c.scanlators,
          if (c.releaseLabel != null)
            c.releaseLabel!
          else if (c.releaseDate != null)
            c.releaseDate!.toIso8601String().split('T').first,
        ].join(' · '),
        true,
      ),
      _ => throw ArgumentError('Not a chapter.'),
    };
    final interactive = enabled && readable;
    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: HikariSpacing.md,
        vertical: HikariSpacing.xs,
      ),
      child: ListTile(
        key: ValueKey(ref),
        shape: const RoundedRectangleBorder(
          borderRadius: HikariRadius.borderMd,
        ),
        tileColor: Theme.of(context).colorScheme.surfaceContainerLow,
        title: Text(title),
        subtitle: Text(
          [
            subtitle,
            if (isContinuation) 'Continue reading',
          ].where((text) => text.isNotEmpty).join(' · '),
        ),
        trailing: interactive ? const Icon(Icons.chevron_right_rounded) : null,
        enabled: interactive,
        onTap: interactive ? onTap : null,
      ),
    );
  }
}
