import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/remote_manga/manga_series_view_model.dart';

class MangaSeriesPage extends StatefulWidget {
  const MangaSeriesPage({
    super.key,
    required this.title,
    required this.sourceName,
    required this.loadDetails,
    this.readArtwork,
    required this.openChapter,
  });
  final String title, sourceName;
  final Future<MangaSeriesDetails> Function() loadDetails;
  final Future<Uint8List> Function(SourceMediaRef)? readArtwork;
  final Future<void> Function(BuildContext, MangaChapter) openChapter;
  @override
  State<MangaSeriesPage> createState() => _MangaSeriesPageState();
}

class _MangaSeriesPageState extends State<MangaSeriesPage> {
  late final MangaSeriesViewModel _viewModel = MangaSeriesViewModel(
    widget.loadDetails,
  );
  bool _isOpeningChapter = false;

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

  Future<void> _openChapter(MangaChapter chapter) async {
    if (_isOpeningChapter) return;
    setState(() => _isOpeningChapter = true);
    try {
      await widget.openChapter(context, chapter);
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Could not open this chapter. It may no longer be available from the source.',
            ),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isOpeningChapter = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(widget.title)),
    body: SafeArea(
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Text(widget.sourceName),
          ),
          if (_isOpeningChapter) const LinearProgressIndicator(),
          Expanded(
            child: ListenableBuilder(
              listenable: _viewModel,
              builder: (context, _) {
                final state = _viewModel.state;
                if (state is MangaSeriesLoading) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (state is MangaSeriesFailure) {
                  return Center(
                    child: SingleChildScrollView(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Text(
                            'Could not load chapters. Check source access or rate limits.',
                          ),
                          TextButton(
                            onPressed: _viewModel.load,
                            child: const Text('Try again'),
                          ),
                        ],
                      ),
                    ),
                  );
                }
                final ready = state as MangaSeriesReady;
                final chapters = ready.details.chapters;
                final details = ready.details;
                return ListView.builder(
                  itemCount: chapters.length + 1,
                  itemBuilder: (context, index) {
                    if (index == 0) {
                      return Column(
                        children: [
                          MediaMetadataView(
                            metadata: details.metadata,
                            sourceName: widget.sourceName,
                            readArtwork: widget.readArtwork,
                          ),
                          if (chapters.isEmpty)
                            const Text('No readable chapters found.'),
                        ],
                      );
                    }
                    final chapter = chapters[index - 1];
                    final subtitle = <String>[
                      chapter.scanlator ?? widget.sourceName,
                      if (chapter.chapterNumber != null)
                        'Chapter ${chapter.chapterNumber}',
                      if (chapter.uploadedAt != null)
                        chapter.uploadedAt!.toIso8601String().split('T').first,
                      if (!chapter.canReadPages) 'Not readable in Hikari',
                    ].join(' · ');
                    return ListTile(
                      key: ValueKey(chapter.source),
                      title: Text(chapter.title),
                      subtitle: Text(subtitle),
                      enabled: !_isOpeningChapter && chapter.canReadPages,
                      onTap: chapter.canReadPages
                          ? () => _openChapter(chapter)
                          : null,
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    ),
  );
}
