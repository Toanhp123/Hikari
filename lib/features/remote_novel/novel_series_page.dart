import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
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
    this.library,
    this.readArtwork,
  });
  final NovelSeriesOpenTarget target;
  final Future<void> Function(BuildContext, NovelChapter, List<NovelChapter>)
  openChapter;
  final LibraryRepository? library;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;
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
    final sequence = _viewModel.state.details?.chaptersInReadingOrder;
    if (sequence == null) return;
    setState(() => _opening = true);
    try {
      await widget.openChapter(context, chapter, List.unmodifiable(sequence));
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
  Widget build(BuildContext context) {
    final source = widget.target.source;
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.target.media.title),
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
            LibraryButton(
              repository: widget.library!,
              media: widget.target.media,
            ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: HikariBreakpoints.maxContentWidth,
            ),
            child: ListenableBuilder(
              listenable: _viewModel,
              builder: (context, _) => NovelSeriesContent(
                state: _viewModel.state,
                sourceName: source.name,
                openingChapter: _opening,
                readArtwork: widget.readArtwork,
                onRefresh: _viewModel.load,
                onOpenChapter: (chapter) => unawaited(_open(chapter)),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
