import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/features/library/library_button.dart';
import 'package:hikari/features/remote_novel/novel_series_view_model.dart';

class NovelSeriesPage extends StatefulWidget {
  const NovelSeriesPage({
    super.key,
    required this.target,
    required this.openChapter,
    this.library,
  });
  final NovelSeriesOpenTarget target;
  final Future<void> Function(BuildContext, NovelChapter) openChapter;
  final LibraryRepository? library;
  @override
  State<NovelSeriesPage> createState() => _NovelSeriesPageState();
}

class _NovelSeriesPageState extends State<NovelSeriesPage> {
  late final NovelSeriesViewModel _viewModel = NovelSeriesViewModel(
    widget.target.loadDetails,
  );
  bool _opening = false;

  @override
  void initState() {
    super.initState();
    unawaited(_viewModel.load());
  }

  @override
  void dispose() {
    _viewModel.dispose();
    super.dispose();
  }

  Future<void> _open(NovelChapter chapter) async {
    if (_opening) return;
    setState(() => _opening = true);
    try {
      await widget.openChapter(context, chapter);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Could not open this chapter. Check source access and try again.',
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _opening = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(widget.target.media.title),
      actions: [
        if (widget.library != null)
          LibraryButton(
            repository: widget.library!,
            media: widget.target.media,
          ),
      ],
    ),
    body: SafeArea(
      child: ListenableBuilder(
        listenable: _viewModel,
        builder: (context, _) {
          final state = _viewModel.state;
          if (state is NovelSeriesLoading) {
            return const Center(child: CircularProgressIndicator());
          }
          if (state is NovelSeriesFailure) {
            return Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('Could not load novel details.'),
                  TextButton(
                    onPressed: _viewModel.load,
                    child: const Text('Try again'),
                  ),
                ],
              ),
            );
          }
          final details = (state as NovelSeriesReady).details;
          final source = widget.target.source;
          return ListView.builder(
            itemCount: details.chapters.length + 1,
            itemBuilder: (context, index) {
              if (index == 0) {
                return Column(
                  children: [
                    MediaMetadataView(
                      metadata: details.metadata,
                      sourceName: source.name,
                      readArtwork: source is ArtworkSource
                          ? (source as ArtworkSource).readArtwork
                          : null,
                    ),
                    if (_opening) const LinearProgressIndicator(),
                    if (details.chapters.isEmpty)
                      const Text('No readable chapters found.'),
                  ],
                );
              }
              final chapter = details.chapters[index - 1];
              return ListTile(
                key: ValueKey(chapter.source),
                title: Text(chapter.title),
                enabled: !_opening,
                subtitle: Text(
                  [
                    if (chapter.chapterNumber != null)
                      'Chapter ${chapter.chapterNumber}',
                    ...chapter.scanlators,
                    if (chapter.releaseLabel != null)
                      chapter.releaseLabel!
                    else if (chapter.releaseDate != null)
                      chapter.releaseDate!.toIso8601String().split('T').first,
                  ].join(' · '),
                ),
                onTap: () => _open(chapter),
              );
            },
          );
        },
      ),
    ),
  );
}
