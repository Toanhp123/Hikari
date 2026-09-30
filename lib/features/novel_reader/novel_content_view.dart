import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_widget_from_html_core/flutter_widget_from_html_core.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';

/// Renders inert chapter HTML without ambient network or filesystem access.
class NovelContentView extends StatelessWidget {
  const NovelContentView({
    super.key,
    required this.content,
    required this.readResource,
    this.onTapLink,
    this.htmlKey,
    this.textStyle,
  });

  final GlobalKey<HtmlWidgetState>? htmlKey;
  final RichReadingContent content;
  final Future<Uint8List> Function(SourceMediaRef) readResource;
  final Future<bool> Function(String)? onTapLink;
  final TextStyle? textStyle;

  @override
  Widget build(BuildContext context) => HtmlWidget(
    content.html,
    key: htmlKey,
    factoryBuilder: _SourceWidgetFactory.new,
    customWidgetBuilder: (element) {
      if (element.localName != 'img') return null;
      final resource = content.resources[element.attributes['src']];
      if (resource == null) return const SizedBox.shrink();
      return _SourceImage(
        resource: resource,
        readResource: readResource,
        label: element.attributes['alt'] ?? 'Illustration',
      );
    },
    onTapUrl: (url) async => await onTapLink?.call(url) ?? true,
    textStyle:
        textStyle ??
        Theme.of(context).textTheme.bodyLarge?.copyWith(height: 1.6),
  );
}

class _SourceWidgetFactory extends WidgetFactory {
  // Also disable ambient providers used by CSS background images.
  @override
  ImageProvider? imageProviderFromNetwork(String url) => null;
  @override
  ImageProvider? imageProviderFromFileUri(String url) => null;
  @override
  ImageProvider? imageProviderFromAsset(String url) => null;
  @override
  ImageProvider? imageProviderFromDataUri(String url) => null;
}

class _SourceImage extends StatefulWidget {
  const _SourceImage({
    required this.resource,
    required this.readResource,
    required this.label,
  });

  final SourceMediaRef resource;
  final Future<Uint8List> Function(SourceMediaRef) readResource;
  final String label;

  @override
  State<_SourceImage> createState() => _SourceImageState();
}

class _SourceImageState extends State<_SourceImage> {
  late Future<Uint8List> _bytes = _read();

  Future<Uint8List> _read() async => widget.readResource(widget.resource);

  @override
  void didUpdateWidget(covariant _SourceImage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.resource != widget.resource) _bytes = _read();
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<Uint8List>(
    future: _bytes,
    builder: (context, snapshot) {
      if (snapshot.hasError) return _error();
      final bytes = snapshot.data;
      if (bytes == null) {
        return const SizedBox(
          height: 96,
          child: Center(child: CircularProgressIndicator()),
        );
      }
      return Image.memory(
        bytes,
        semanticLabel: widget.label,
        fit: BoxFit.contain,
        errorBuilder: (_, _, _) => _error(),
      );
    },
  );

  Widget _error() => Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      const Text('Could not load illustration.'),
      TextButton(
        onPressed: () => setState(() => _bytes = _read()),
        child: const Text('Retry illustration'),
      ),
    ],
  );
}
