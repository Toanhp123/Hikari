import 'package:flutter/material.dart';

import 'dart:typed_data';

import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/features/remote_manga/media_metadata_view.dart';

class MangaChapterPage extends StatefulWidget {
  const MangaChapterPage({
    super.key,
    required this.title,
    required this.sourceName,
    this.loadChapters,
    this.loadDetails,
    this.readArtwork,
    required this.openChapter,
  });
  final String title, sourceName;
  final Future<List<MangaChapter>> Function()? loadChapters;
  final Future<MangaSeriesDetails> Function()? loadDetails;
  final Future<Uint8List> Function(SourceMediaRef)? readArtwork;
  final Future<void> Function(BuildContext, MangaChapter) openChapter;
  @override
  State<MangaChapterPage> createState() => _MangaChapterPageState();
}

class _MangaChapterPageState extends State<MangaChapterPage> {
  late Future<List<MangaChapter>> _chaptersFuture;
  bool _isOpeningChapter = false;
  MangaSeriesDetails? _details;
  Future<List<MangaChapter>> _load() async {
    if (widget.loadDetails != null) {
      final details = await widget.loadDetails!();
      _details = details;
      return details.chapters;
    }
    return widget.loadChapters!();
  }

  @override
  void initState() {
    super.initState();
    _chaptersFuture = Future.sync(_load);
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
            child: FutureBuilder<List<MangaChapter>>(
              future: _chaptersFuture,
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snapshot.hasError) {
                  return Center(
                    child: SingleChildScrollView(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Text(
                            'Could not load chapters. Check source access or rate limits.',
                          ),
                          TextButton(
                            onPressed: () => setState(() {
                              _chaptersFuture = Future.sync(_load);
                            }),
                            child: const Text('Try again'),
                          ),
                        ],
                      ),
                    ),
                  );
                }
                final chapters = snapshot.data!;
                if (chapters.isEmpty && _details == null) {
                  return const Center(
                    child: Text('No readable chapters found.'),
                  );
                }
                return ListView.builder(
                  itemCount: chapters.length + (_details == null ? 0 : 1),
                  itemBuilder: (context, index) {
                    if (_details != null && index == 0) {
                      return Column(
                        children: [
                          MediaMetadataView(
                            metadata: _details!.metadata,
                            sourceName: widget.sourceName,
                            readArtwork: widget.readArtwork,
                          ),
                          if (chapters.isEmpty)
                            const Text('No readable chapters found.'),
                        ],
                      );
                    }
                    final chapter =
                        chapters[index - (_details == null ? 0 : 1)];
                    final subtitle = <String>[
                      chapter.scanlator ?? widget.sourceName,
                      if (chapter.chapterNumber != null)
                        'Chapter ${chapter.chapterNumber}',
                      if (chapter.dateUpload != null && chapter.dateUpload! > 0)
                        DateTime.fromMillisecondsSinceEpoch(
                          chapter.dateUpload!,
                          isUtc: true,
                        ).toIso8601String().split('T').first,
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
