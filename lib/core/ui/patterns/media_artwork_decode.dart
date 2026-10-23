import 'package:flutter/widgets.dart';
import 'package:hikari/core/ui/media/hikari_artwork_decode_policy.dart';

/// Rounds the requested image decode width to stable 64-physical-pixel buckets.
/// Keeps small posters sharp on high-DPI devices without decoding full originals.
int? mediaArtworkCacheWidth(BuildContext context, double logicalWidth) {
  // Transitional legacy entrypoint; removed with remaining callers in 07/01.
  return decodeTargetSize(
    logicalSize: Size(logicalWidth, double.infinity),
    devicePixelRatio: MediaQuery.devicePixelRatioOf(context),
  )?.width;
}
