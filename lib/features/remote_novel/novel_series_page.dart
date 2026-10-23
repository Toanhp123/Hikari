import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/layout/hikari_page_scaffold.dart';
import 'package:hikari/core/ui/layout/hikari_content_region.dart';
import 'package:hikari/application/media/load_series_reading_target.dart';
import 'package:hikari/application/media/open_media.dart';
import 'package:hikari/core/ui/components/hikari_refresh_action.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/widgets/library_button.dart';
import 'package:hikari/features/remote_novel/novel_series_view_model.dart';
import 'package:hikari/features/remote_novel/widgets/novel_series_content.dart';

class NovelSeriesPage extends StatefulWidget {
  const NovelSeriesPage({
    super.key,
    required this.target,
    required this.openChapter,
    this.dependencyOwner,
    this.library,
    this.readArtwork,
    this.loadReadingTarget,
  });
  final Future<SeriesReadingTarget> Function(List<SourceMediaRef>)?
  loadReadingTarget;

  /// Stable provider/repository identity; defaults to the target source.
  final Object? dependencyOwner;
  final NovelSeriesOpenTarget target;

  /// [canNavigate] is rechecked after asynchronous chapter opening,
  /// before pushing a reader route, to reject stale provider completions.
  final Future<void> Function(
    BuildContext,
    NovelChapter,
    List<NovelChapter>,
    bool Function() canNavigate,
  )
  openChapter;
  final LibraryRepository? library;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;
  @override
  State<NovelSeriesPage> createState() => _NovelSeriesPageState();
}

class _NovelSeriesPageState extends State<NovelSeriesPage> {
  late NovelSeriesViewModel _viewModel = NovelSeriesViewModel(
    widget.target.loadDetails,
    loadReadingTarget: widget.loadReadingTarget,
    series: widget.target.media.source,
    previewMetadata: widget.target.previewMetadata,
    readCachedDetails: widget.target.readCachedDetails,
  );
  bool _isOpeningChapter = false;
  int _openingGeneration = 0;

  @override
  void initState() {
    super.initState();
    unawaited(_viewModel.load(useCached: true));
  }

  @override
  void didUpdateWidget(NovelSeriesPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.target.media.source != widget.target.media.source) {
      _viewModel.dispose();
      _viewModel = NovelSeriesViewModel(
        widget.target.loadDetails,
        loadReadingTarget: widget.loadReadingTarget,
        series: widget.target.media.source,
        previewMetadata: widget.target.previewMetadata,
        readCachedDetails: widget.target.readCachedDetails,
      );
      _openingGeneration++;
      _isOpeningChapter = false;
      unawaited(_viewModel.load(useCached: true));
    } else {
      final ownerChanged =
          (oldWidget.dependencyOwner ?? oldWidget.target.source) !=
          (widget.dependencyOwner ?? widget.target.source);
      if (ownerChanged) {
        _openingGeneration++;
        _isOpeningChapter = false;
      }
      _viewModel.updateDependencies(
        widget.target.loadDetails,
        widget.loadReadingTarget,
        ownerChanged: ownerChanged,
        readCachedDetails: widget.target.readCachedDetails,
      );
    }
  }

  @override
  void dispose() {
    _viewModel.dispose();
    super.dispose();
  }

  Future<void> _openChapter(NovelChapter chapter) async {
    if (!mounted || _isOpeningChapter) return;
    final viewModel = _viewModel;
    final generation = _openingGeneration;
    final sequence = viewModel.readingSequence;
    if (!sequence.contains(chapter)) return;
    bool canNavigate() =>
        mounted &&
        identical(viewModel, _viewModel) &&
        generation == _openingGeneration;
    setState(() => _isOpeningChapter = true);
    try {
      await widget.openChapter(
        context,
        chapter,
        List.unmodifiable(sequence),
        canNavigate,
      );
    } catch (_) {
      if (mounted &&
          identical(viewModel, _viewModel) &&
          generation == _openingGeneration) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Could not open this chapter. Check source access and try again.',
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
  Widget build(BuildContext context) {
    final source = widget.target.source;
    return HikariPageScaffold(
      appBar: AppBar(
        title: ListenableBuilder(
          listenable: _viewModel,
          builder: (_, _) => Text(
            _viewModel.effectiveMetadata?.title ?? widget.target.media.title,
          ),
        ),
        actions: [
          ListenableBuilder(
            listenable: _viewModel,
            builder: (context, _) => HikariRefreshAction(
              tooltip: 'Refresh',
              refreshing: _viewModel.state.refreshing,
              onPressed: _viewModel.state.status == NovelSeriesStatus.loading
                  ? null
                  : () => unawaited(_viewModel.load()),
            ),
          ),
          if (widget.library != null)
            ListenableBuilder(
              listenable: _viewModel,
              builder: (_, _) => LibraryButton(
                repository: widget.library!,
                media: widget.target.media,
                artwork: _viewModel.effectiveMetadata?.cover,
              ),
            ),
        ],
      ),
      body: HikariContentRegion(
        child: Column(
          children: [
            if (_isOpeningChapter) const LinearProgressIndicator(),
            Expanded(
              child: ListenableBuilder(
                listenable: _viewModel,
                builder: (context, _) => NovelSeriesContent(
                  key: ValueKey(widget.target.media.source),
                  viewModel: _viewModel,
                  sourceName: source.name,
                  openingChapter: _isOpeningChapter,
                  readArtwork: widget.readArtwork,
                  artworkOwner: widget.dependencyOwner ?? widget.target.source,
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
}
