import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/playback/video_playback.dart';
import 'package:hikari/features/manga_reader/widgets/manga_reader_chrome.dart';
import 'package:hikari/features/player/widgets/player_controls.dart';
import 'package:hikari/features/player/player_page.dart';

void main() {
  testWidgets(
    'slow video scrub keeps controls until release then restarts hide timer',
    (tester) async {
      final controls = _FakePlaybackControls(playing: true);
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: PlayerPage(
            title: 'Video',
            playback: const SizedBox(),
            controls: controls,
          ),
        ),
      );
      await tester.pump(const Duration(seconds: 2));
      final slider = find.byType(Slider);
      final gesture = await tester.startGesture(tester.getCenter(slider));
      await gesture.moveBy(const Offset(40, 0));
      await tester.pump(const Duration(seconds: 4));
      expect(slider, findsOneWidget);
      expect(controls.seeks, isEmpty);
      await gesture.moveBy(const Offset(40, 0));
      await gesture.up();
      await tester.pump();
      expect(controls.seeks, hasLength(1));
      await tester.pump(const Duration(seconds: 2));
      expect(slider, findsOneWidget);
      await tester.pump(const Duration(seconds: 1));
      expect(slider, findsNothing);
      await tester.pumpWidget(const SizedBox());
    },
  );
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
  _FakePlaybackControls({this.playing = false});
  final seeks = <Duration>[];
  @override
  Stream<void> get changes => const Stream<void>.empty();

  @override
  final bool playing;

  @override
  Duration get position => const Duration(seconds: 30);

  @override
  Duration get duration => const Duration(minutes: 2);

  @override
  Future<void> play() async {}

  @override
  Future<void> pause() async {}

  @override
  Future<void> seek(Duration position) async {
    seeks.add(position);
  }
}
