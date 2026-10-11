import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/playback/video_playback.dart';
import 'package:hikari/features/manga_reader/widgets/manga_reader_chrome.dart';
import 'package:hikari/features/player/widgets/player_controls.dart';

void main() {
  testWidgets('Manga page scrubber identifies page and position', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    int? selectedPage;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: MangaReaderPageControls(
            pageIndex: 2,
            pageCount: 12,
            loading: false,
            onPrevious: () {},
            onNext: () {},
            onJumpToPage: (page) => selectedPage = page,
          ),
        ),
      ),
    );

    expect(find.bySemanticsLabel('Reading page'), findsOneWidget);
    final slider = tester.widget<Slider>(find.byType(Slider));
    expect(slider.semanticFormatterCallback!(3), 'Page 3 of 12');
    expect(slider.semanticFormatterCallback!(12), 'Page 12 of 12');
    expect(slider.value, 3);
    expect(selectedPage, isNull);
    semantics.dispose();
  });

  testWidgets('Video scrubber identifies playback progress', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: PlayerControls(
            controls: _FakePlaybackControls(),
            onTogglePlayPause: () {},
            onSeekRelative: (_) {},
            onSeekToProgress: (_) {},
          ),
        ),
      ),
    );

    expect(find.bySemanticsLabel('Playback position'), findsOneWidget);
    final slider = tester.widget<Slider>(find.byType(Slider));
    expect(slider.value, closeTo(0.25, 0.001));
    expect(slider.semanticFormatterCallback!(0.25), '25 percent');
    expect(slider.semanticFormatterCallback!(1), '100 percent');
    semantics.dispose();
  });
}

class _FakePlaybackControls implements VideoPlaybackControls {
  @override
  Stream<void> get changes => const Stream<void>.empty();

  @override
  bool get playing => false;

  @override
  Duration get position => const Duration(seconds: 30);

  @override
  Duration get duration => const Duration(minutes: 2);

  @override
  Future<void> play() async {}

  @override
  Future<void> pause() async {}

  @override
  Future<void> seek(Duration position) async {}
}
