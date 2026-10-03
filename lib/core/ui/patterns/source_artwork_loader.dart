import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/domain/media/media.dart';

typedef SourceArtworkReader = Future<Uint8List?> Function(
  SourceMediaRef artwork,
);

typedef SourceArtworkBuilder = Widget Function(
  BuildContext context,
  Uint8List? bytes,
  bool loading,
);

/// Loads source-owned artwork once per [artwork] identity and degrades to null.
///
/// Artwork is presentation enrichment: failures must never make search results
/// or source candidates unusable.
class SourceArtworkLoader extends StatefulWidget {
  const SourceArtworkLoader({
    super.key,
    required this.builder,
    this.artwork,
    this.readArtwork,
  });

  final SourceMediaRef? artwork;
  final SourceArtworkReader? readArtwork;
  final SourceArtworkBuilder builder;

  @override
  State<SourceArtworkLoader> createState() => _SourceArtworkLoaderState();
}

class _SourceArtworkLoaderState extends State<SourceArtworkLoader> {
  Future<Uint8List?>? _load;

  @override
  void initState() {
    super.initState();
    _resetLoad();
  }

  @override
  void didUpdateWidget(SourceArtworkLoader oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.artwork != widget.artwork ||
        (oldWidget.readArtwork == null) != (widget.readArtwork == null)) {
      _resetLoad();
    }
  }

  void _resetLoad() {
    final artwork = widget.artwork;
    final reader = widget.readArtwork;
    _load = artwork == null || reader == null
        ? null
        : Future.sync(() => reader(artwork)).then(
            (bytes) => bytes == null || bytes.isEmpty ? null : bytes,
            onError: (_, _) => null,
          );
  }

  @override
  Widget build(BuildContext context) {
    final load = _load;
    if (load == null) return widget.builder(context, null, false);

    return FutureBuilder<Uint8List?>(
      future: load,
      builder: (context, snapshot) => widget.builder(
        context,
        snapshot.data,
        snapshot.connectionState != ConnectionState.done,
      ),
    );
  }
}
