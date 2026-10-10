import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_refresh_action.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/local_media_scan_result.dart';
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
    this.readArtwork,
  });

  final Future<LocalMediaScanResult?> Function() scanSelectedRoot;
  final Future<bool> Function() chooseRoot;
  final void Function(BuildContext, Media) openMedia;
  final bool supported;
  final LibraryRepository? library;
  final int scanRevision;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;

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
      unawaited(_viewModel.scan(rootChanged: true));
    }
  }

  @override
  void dispose() {
    _viewModel.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _viewModel,
      builder: (context, _) {
        final state = _viewModel.state;
        final canRescan =
            (state.hasScanResult &&
                state.failure != LocalMediaFailure.accessLost) ||
            (state.status == LocalMediaStatus.error &&
                state.failure == LocalMediaFailure.unavailable);
        return HikariScaffold(
          useSafeArea: true,
          appBar: AppBar(
            title: const Text('Local'),
            actions: [
              if (widget.supported && canRescan) ...[
                HikariRefreshAction(
                  refreshing: state.refreshing,
                  tooltip: 'Rescan folder',
                  onPressed: state.busy
                      ? null
                      : () => unawaited(_viewModel.scan()),
                ),
                const SizedBox(width: HikariSpacing.xs),
              ],
            ],
          ),
          body: LocalMediaContent(
            state: state,
            supported: widget.supported,
            openMedia: widget.openMedia,
            onChooseRoot: _viewModel.chooseRoot,
            onScan: _viewModel.scan,
            onSelectFilter: _viewModel.selectFilter,
            readArtwork: widget.readArtwork,
            library: widget.library,
          ),
        );
      },
    );
  }
}
