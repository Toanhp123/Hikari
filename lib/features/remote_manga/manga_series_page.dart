import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

import 'package:hikari/core/ui/components/hikari_refresh_action.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/widgets/library_button.dart';
import 'package:hikari/features/remote_manga/manga_series_view_model.dart';
import 'package:hikari/features/remote_manga/widgets/manga_series_content.dart';

class MangaSeriesPage extends StatefulWidget {
  const MangaSeriesPage({
    super.key,
    required this.media,
    required this.sourceName,
    required this.loadDetails,
    this.readArtwork,
    this.library,
    required this.openChapter,
  });
  final Media media;
  final String sourceName;
  final Future<MangaSeriesDetails> Function() loadDetails;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;
  final LibraryRepository? library;
  final Future<void> Function(BuildContext, MangaChapter, List<MangaChapter>)
  openChapter;
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
    final sequence = _viewModel.state.details?.chaptersInReadingOrder;
    if (sequence == null) return;
    setState(() => _isOpeningChapter = true);
    try {
      await widget.openChapter(context, chapter, List.unmodifiable(sequence));
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
    appBar: AppBar(
      title: Text(widget.media.title),
      actions: [
        ListenableBuilder(
          listenable: _viewModel,
          builder: (context, _) => HikariRefreshAction(
            tooltip: 'Refresh',
            refreshing: _viewModel.state.refreshing,
            onPressed: _viewModel.state.status == MangaSeriesStatus.loading
                ? null
                : () => unawaited(_viewModel.load()),
          ),
        ),
        if (widget.library != null)
          LibraryButton(repository: widget.library!, media: widget.media),
      ],
    ),
    body: SafeArea(
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(HikariSpacing.lg),
            child: Text(widget.sourceName),
          ),
          if (_isOpeningChapter) const LinearProgressIndicator(),
          Expanded(
            child: ListenableBuilder(
              listenable: _viewModel,
              builder: (context, _) => MangaSeriesContent(
                state: _viewModel.state,
                sourceName: widget.sourceName,
                openingChapter: _isOpeningChapter,
                readArtwork: widget.readArtwork,
                onRefresh: _viewModel.load,
                onOpenChapter: (chapter) => unawaited(_openChapter(chapter)),
              ),
            ),
          ),
        ],
      ),
    ),
  );
}
