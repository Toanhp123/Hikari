import 'package:flutter/painting.dart';

/// Width-only physical decode target. Null leaves unconstrained input unchanged.
({int width, int? height})? decodeTargetSize({
  required Size logicalSize,
  required double devicePixelRatio,
}) {
  final width = logicalSize.width;
  if (!width.isFinite ||
      width <= 0 ||
      !devicePixelRatio.isFinite ||
      devicePixelRatio <= 0) {
    return null;
  }
  final pixels = width * devicePixelRatio;
  if (!pixels.isFinite) return null;
  final bucket = ((pixels / 64).ceil() * 64).clamp(64, 2048);
  return (width: bucket, height: null);
}
