import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/local_media/local_media_view_model.dart';
import 'package:hikari/features/local_media/widgets/local_media_content.dart';

class LocalMediaPage extends StatefulWidget {
  const LocalMediaPage({
    super.key,
    required this.scanSelectedRoot,
    required this.chooseRoot,
    required this.openMedia,
    this.supported = true,
    this.library,
    this.scanRevision = 0,
  });

  final Future<List<Media>?> Function() scanSelectedRoot;
  final Future<bool> Function() chooseRoot;
  final void Function(BuildContext, Media) openMedia;
  final bool supported;
  final LibraryRepository? library;
  final int scanRevision;

  @override
  State<LocalMediaPage> createState() => _LocalMediaPageState();
}

class _LocalMediaPageState extends State<LocalMediaPage> {
  late final LocalMediaViewModel _viewModel;

  @override
  void initState() {
    super.initState();
    _viewModel = LocalMediaViewModel(
      () => widget.scanSelectedRoot(),
      () => widget.chooseRoot(),
    );
    if (widget.supported) unawaited(_viewModel.scan());
  }

  @override
  void didUpdateWidget(LocalMediaPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.supported && oldWidget.scanRevision != widget.scanRevision) {
      unawaited(_viewModel.scan(refresh: true));
    }
  }

  @override
  void dispose() {
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return HikariScaffold(
      useSafeArea: true,
      body: ListenableBuilder(
        listenable: _viewModel,
        builder: (context, _) => LocalMediaContent(
          state: _viewModel.state,
          supported: widget.supported,
          openMedia: widget.openMedia,
          onChooseRoot: _viewModel.chooseRoot,
          onScan: _viewModel.scan,
          library: widget.library,
        ),
      ),
    );
  }
}
