import 'package:flutter/material.dart';
import 'package:hikari/core/ui/media/hikari_artwork_decode_policy.dart';

/// Generic rendering only. Features own provider selection and byte loading.
class HikariArtwork extends StatelessWidget {
  const HikariArtwork({
    super.key,
    this.image,
    this.semanticLabel,
    this.fit = BoxFit.cover,
    this.placeholder,
  });

  final ImageProvider? image;
  final String? semanticLabel;
  final BoxFit fit;
  final Widget? placeholder;

  @override
  Widget build(BuildContext context) {
    final fallback =
        placeholder ??
        ColoredBox(
          color: Theme.of(context).colorScheme.surfaceContainerHighest,
          child: const Center(child: Icon(Icons.image_not_supported_outlined)),
        );
    final provider = image;
    if (provider == null) {
      return Semantics(
        label: semanticLabel,
        image: semanticLabel != null,
        child: ExcludeSemantics(child: fallback),
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        final target = decodeTargetSize(
          logicalSize: Size(constraints.maxWidth, constraints.maxHeight),
          devicePixelRatio: MediaQuery.devicePixelRatioOf(context),
        );
        final resized = provider is ResizeImage || target == null
            ? provider
            : ResizeImage(provider, width: target.width);
        return Image(
          image: resized,
          fit: fit,
          width: double.infinity,
          height: double.infinity,
          semanticLabel: semanticLabel,
          excludeFromSemantics: semanticLabel == null,
          frameBuilder: (_, child, frame, synchronous) =>
              synchronous || frame != null
              ? child
              : Semantics(
                  label: semanticLabel,
                  image: semanticLabel != null,
                  child: ExcludeSemantics(child: fallback),
                ),
          errorBuilder: (_, _, _) => Semantics(
            label: semanticLabel,
            image: semanticLabel != null,
            child: ExcludeSemantics(child: fallback),
          ),
        );
      },
    );
  }
}
