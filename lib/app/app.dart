import 'package:flutter/material.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/local_media/local_media_page.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';
import 'package:hikari/features/novel_reader/novel_reader_page.dart';
import 'package:hikari/features/player/player_page.dart';
import 'package:hikari/infrastructure/local_media/local_media_source.dart';
import 'package:hikari/infrastructure/playback/local_video.dart';

class HikariApp extends StatelessWidget {
  const HikariApp({super.key});

  @override
  Widget build(BuildContext context) {
    final source = LocalMediaSource();
    return MaterialApp(
      title: 'Hikari',
      theme: ThemeData(colorSchemeSeed: Colors.indigo),
      darkTheme: ThemeData(
        colorSchemeSeed: Colors.indigo,
        brightness: Brightness.dark,
      ),
      home: LocalMediaPage(
        supported: source.supported,
        scanSelectedRoot: source.scanSelectedRoot,
        chooseRoot: source.chooseRoot,
        openMedia: (context, media) {
          final page = switch (media.type) {
            MediaType.anime => PlayerPage(
              title: media.title,
              playback: LocalVideo(locator: media.source.itemId),
            ),
            MediaType.manga => MangaReaderPage(
              title: media.title,
              loadPages: () => source.pages(media.source),
              readPage: source.image,
            ),
            MediaType.lightNovel => NovelReaderPage(
              title: media.title,
              loadText: () => source.text(media.source),
            ),
          };
          Navigator.of(context)
              .push(MaterialPageRoute<void>(builder: (_) => page));
        },
      ),
    );
  }
}
