import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/media/media.dart';

String mediaTypeLabel(MediaType type) => switch (type) {
  MediaType.anime => 'Anime',
  MediaType.manga => 'Manga',
  MediaType.lightNovel => 'Light Novel',
};

String mediaTypeFilterLabel(MediaType type) => switch (type) {
  MediaType.anime => 'Anime',
  MediaType.manga => 'Manga',
  MediaType.lightNovel => 'Light Novels',
};

String mediaTypeBadgeLabel(MediaType type) => switch (type) {
  MediaType.anime => 'ANIME',
  MediaType.manga => 'MANGA',
  MediaType.lightNovel => 'NOVEL',
};

/// Canonical container/on-container pair for media identity badges.
/// Controls that display only a small indicator should use [foreground].
({Color background, Color foreground}) mediaTypeBadgeColors(
  BuildContext context,
  MediaType type,
) {
  final scheme = Theme.of(context).colorScheme;
  final status = context.hikariStatusColors;
  return switch (type) {
    MediaType.anime => (
      background: scheme.primaryContainer,
      foreground: scheme.onPrimaryContainer,
    ),
    MediaType.manga => (
      background: status.warningContainer,
      foreground: status.onWarningContainer,
    ),
    MediaType.lightNovel => (
      background: status.infoContainer,
      foreground: status.onInfoContainer,
    ),
  };
}
