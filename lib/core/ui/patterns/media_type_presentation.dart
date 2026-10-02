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

Color mediaTypeBadgeColor(HikariColors colors, MediaType type) =>
    switch (type) {
      MediaType.anime => colors.badgeVideo,
      MediaType.manga => colors.badgeManga,
      MediaType.lightNovel => colors.badgeNovel,
    };
