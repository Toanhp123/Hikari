import 'package:flutter/material.dart';
import 'package:hikari/core/ui/media/hikari_media_card_body.dart';
import 'package:hikari/core/ui/foundation/hikari_aspect_ratios.dart';

class HikariLandscapeCard extends StatelessWidget {
  const HikariLandscapeCard({
    super.key,
    required this.title,
    required this.artwork,
    this.subtitle,
    this.badge,
    this.progress,
    this.onTap,
    this.heroTag,
  });
  final String title;
  final Widget artwork;
  final String? subtitle;
  final Widget? badge;
  final double? progress;
  final VoidCallback? onTap;
  final String? heroTag;
  @override
  Widget build(BuildContext context) => HikariMediaCardBody(
    title: title,
    artwork: artwork,
    aspectRatio: HikariAspectRatios.landscape,
    subtitle: subtitle,
    badge: badge,
    progress: progress,
    onTap: onTap,
    heroTag: heroTag,
  );
}
