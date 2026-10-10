import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';

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
    this.dependencyOwner,
    this.readArtwork,
    this.loadReadingTarget,
    this.library,
    required this.openChapter,
  });
  final Future<SeriesReadingTarget> Function(List<SourceMediaRef>)?
  loadReadingTarget;

  /// Stable provider/repository identity; not a per-build callback.
  final Object? dependencyOwner;
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
  late MangaSeriesViewModel _viewModel = MangaSeriesViewModel(
    widget.loadDetails,
    loadReadingTarget: widget.loadReadingTarget,
    series: widget.media.source,
  );
  bool _isOpeningChapter = false;
  int _openingGeneration = 0;

  @override
  void initState() {
    super.initState();
    unawaited(_viewModel.load());
  }

  @override
  void didUpdateWidget(MangaSeriesPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.media.source != widget.media.source) {
      _viewModel.dispose();
      _viewModel = MangaSeriesViewModel(
        widget.loadDetails,
        loadReadingTarget: widget.loadReadingTarget,
        series: widget.media.source,
      );
      _isOpeningChapter = false;
      unawaited(_viewModel.load());
    } else {
      final ownerChanged = oldWidget.dependencyOwner != widget.dependencyOwner;
      if (ownerChanged) {
        _openingGeneration++;
        _isOpeningChapter = false;
      }
      _viewModel.updateDependencies(
        widget.loadDetails,
        widget.loadReadingTarget,
        ownerChanged: ownerChanged,
      );
    }
  }

  @override
  void dispose() {
    _viewModel.dispose();
    super.dispose();
  }

  Future<void> _openChapter(MangaChapter chapter) async {
    if (!mounted || _isOpeningChapter) return;
    final viewModel = _viewModel;
    final generation = _openingGeneration;
    final sequence = viewModel.readingSequence;
    if (!chapter.canReadPages || !sequence.contains(chapter)) return;
    setState(() => _isOpeningChapter = true);
    try {
      await widget.openChapter(context, chapter, List.unmodifiable(sequence));
    } catch (_) {
      if (mounted &&
          identical(viewModel, _viewModel) &&
          generation == _openingGeneration) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Could not open this chapter. It may no longer be available from the source.',
            ),
          ),
        );
      }
    } finally {
      if (mounted &&
          identical(viewModel, _viewModel) &&
          generation == _openingGeneration) {
        setState(() => _isOpeningChapter = false);
        unawaited(viewModel.refreshReadingTarget());
      }
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
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: HikariBreakpoints.maxContentWidth,
          ),
          child: Column(
            children: [
              if (_isOpeningChapter) const LinearProgressIndicator(),
              Expanded(
                child: ListenableBuilder(
                  listenable: _viewModel,
                  builder: (context, _) => MangaSeriesContent(
                    key: ValueKey(widget.media.source),
                    viewModel: _viewModel,
                    sourceName: widget.sourceName,
                    openingChapter: _isOpeningChapter,
                    readArtwork: widget.readArtwork,
                    artworkOwner: widget.dependencyOwner,
                    onRefresh: _viewModel.load,
                    onOpenChapter: (chapter) =>
                        unawaited(_openChapter(chapter)),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
