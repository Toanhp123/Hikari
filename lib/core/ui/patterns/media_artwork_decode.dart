import 'package:flutter/widgets.dart';

/// Rounds the requested image decode width to stable 64-physical-pixel buckets.
/// Keeps small posters sharp on high-DPI devices without decoding full originals.
int? mediaArtworkCacheWidth(BuildContext context, double logicalWidth) {
  if (!logicalWidth.isFinite || logicalWidth <= 0) return null;
  final pixels = logicalWidth * MediaQuery.devicePixelRatioOf(context);
  if (!pixels.isFinite || pixels <= 0) return null;
  final rounded = (pixels / 64).ceil() * 64;
  if (rounded < 64) return 64;
  return rounded > 2048 ? 2048 : rounded;
}
