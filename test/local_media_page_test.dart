import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/media/local_media_scan_result.dart';

import 'package:hikari/core/ui/patterns/media_metadata_view.dart';
import 'package:hikari/core/ui/patterns/media_poster.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/local_media/local_media_page.dart';

void main() {
  const media = Media(
    title: 'Spirited Away',
    type: MediaType.anime,
    source: SourceMediaRef(sourceId: SourceId.local, itemId: 'movie-1'),
  );
  const replacement = Media(
    title: 'Chapter one',
    type: MediaType.lightNovel,
    source: SourceMediaRef(sourceId: SourceId.local, itemId: 'novel-1'),
  );

  Widget host({
    required Future<List<Media>?> Function() scanSelectedRoot,
    required Future<bool> Function() chooseRoot,
    required void Function(BuildContext, Media) openMedia,
    bool supported = true,
    int scanRevision = 0,
    String rootName = 'Media folder',
    Map<SourceMediaRef, SourceMediaRef> artwork = const {},
    Future<Uint8List?> Function(SourceMediaRef)? readArtwork,
  }) {
    return MaterialApp(
      theme: HikariTheme.darkTheme(),
      home: LocalMediaPage(
        scanSelectedRoot: () async {
          final media = await scanSelectedRoot();
          return media == null
              ? null
              : LocalMediaScanResult(
                  rootName: rootName,
                  media: media,
                  artwork: artwork,
                );
        },
        chooseRoot: chooseRoot,
        openMedia: openMedia,
        supported: supported,
        scanRevision: scanRevision,
        readArtwork: readArtwork,
      ),
    );
  }

  testWidgets('no saved root restores once then shows choose prompt', (
    tester,
  ) async {
    var scans = 0;
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async {
          scans++;
          return null;
        },
        chooseRoot: () async => false,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();

    expect(scans, 1);
    expect(find.text('Choose a folder to find local media.'), findsOneWidget);
    expect(find.text('Choose folder'), findsOneWidget);
    expect(find.byType(RefreshIndicator), findsNothing);
    expect(find.byTooltip('Rescan folder'), findsNothing);

    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async {
          scans++;
          return null;
        },
        chooseRoot: () async => false,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();
    expect(scans, 1);
  });

  testWidgets('root revision replaces even an in-flight restored scan', (
    tester,
  ) async {
    final old = Completer<List<Media>?>();
    var scans = 0;
    Future<List<Media>?> scan() =>
        ++scans == 1 ? old.future : Future.value([replacement]);
    Widget page(int revision) => host(
      scanSelectedRoot: scan,
      chooseRoot: () async => false,
      openMedia: (_, _) {},
      scanRevision: revision,
    );
    await tester.pumpWidget(page(0));
    await tester.pumpWidget(page(1));
    await tester.pumpAndSettle();
    expect(find.text('Chapter one'), findsOneWidget);
    old.complete([media]);
    await tester.pumpAndSettle();
    expect(find.text('Spirited Away'), findsNothing);
    expect(scans, 2);
  });

  for (final size in [const Size(375, 812), const Size(812, 375)]) {
    testWidgets('Local no-root fits $size with large text', (tester) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context)
                .copyWith(textScaler: TextScaler.linear(1.6)),
            child: child!,
          ),
          home: LocalMediaPage(
            scanSelectedRoot: () async => null,
            chooseRoot: () async => false,
            openMedia: (_, _) {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.text('Choose folder'), findsOneWidget);
    });
  }

  testWidgets('large scans keep media rows lazily rendered', (tester) async {
    final largeScan = List<Media>.generate(
      500,
      (index) => Media(
        title: 'Item $index',
        type: MediaType.anime,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'item-$index'),
      ),
    );

    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async => largeScan,
        chooseRoot: () async => false,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Item 0'), findsOneWidget);
    expect(find.text('Item 499'), findsNothing);
    expect(find.byType(ListTile).evaluate().length, lessThan(500));
  });

  testWidgets('saved root restores results automatically', (tester) async {
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async => <Media>[media],
        chooseRoot: () async => false,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Spirited Away'), findsOneWidget);
    expect(find.text('Media folder'), findsOneWidget);
    expect(find.text('Anime'), findsWidgets);
    expect(find.text('Choose folder'), findsOneWidget);
    expect(find.byType(RefreshIndicator), findsOneWidget);
    expect(find.byTooltip('Rescan folder'), findsOneWidget);
  });

  testWidgets('explicit reselection replaces current results', (tester) async {
    var scans = 0;
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async =>
            scans++ == 0 ? <Media>[media] : <Media>[replacement],
        chooseRoot: () async => true,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Choose folder'));
    await tester.pumpAndSettle();

    expect(find.text('Spirited Away'), findsNothing);
    expect(find.text('Chapter one'), findsOneWidget);
    expect(find.text('Light Novel'), findsWidgets);
  });

  testWidgets('picker cancellation preserves previous results', (tester) async {
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async => <Media>[media],
        chooseRoot: () async => false,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Choose folder'));
    await tester.pumpAndSettle();

    expect(find.text('Spirited Away'), findsOneWidget);
    expect(find.textContaining('Could not scan local media.'), findsNothing);
  });

  testWidgets('failed rescan keeps stale results through picker cancellation', (
    tester,
  ) async {
    var scans = 0;
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async {
          scans++;
          if (scans == 1) return <Media>[media];
          throw StateError('permission revoked');
        },
        chooseRoot: () async => false,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();

    final refresh = tester
        .state<RefreshIndicatorState>(find.byType(RefreshIndicator))
        .show();

    await tester.pump();
    await tester.pumpAndSettle();
    await refresh;
    await tester.pumpAndSettle();

    expect(
      find.textContaining('Could not rescan this folder.'),
      findsOneWidget,
    );
    expect(find.text('Spirited Away'), findsOneWidget);

    await tester.tap(find.text('Choose folder'));
    await tester.pumpAndSettle();

    expect(
      find.textContaining('Could not rescan this folder.'),
      findsOneWidget,
    );
    expect(find.text('Spirited Away'), findsOneWidget);
  });

  testWidgets('failed rescan preserves a known-empty folder snapshot', (
    tester,
  ) async {
    var scans = 0;
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async {
          if (scans++ == 0) return <Media>[];
          throw StateError('permission revoked');
        },
        chooseRoot: () async => false,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No local media found.'), findsOneWidget);

    final refresh = tester
        .state<RefreshIndicatorState>(find.byType(RefreshIndicator))
        .show();
    await tester.pump();
    await tester.pumpAndSettle();
    await refresh;
    await tester.pumpAndSettle();

    expect(
      find.textContaining('Could not rescan this folder.'),
      findsOneWidget,
    );
    expect(find.text('No local media found.'), findsOneWidget);
    expect(find.text('Folder unavailable'), findsNothing);
  });

  testWidgets('restore failure is recoverable and does not auto retry', (
    tester,
  ) async {
    var scans = 0;
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async {
          scans++;
          if (scans == 1) throw StateError('permission revoked');
          return <Media>[replacement];
        },
        chooseRoot: () async => true,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();

    expect(scans, 1);
    expect(find.textContaining('Could not scan local media.'), findsOneWidget);
    expect(find.text('Try Again'), findsOneWidget);
    expect(find.text('Choose folder'), findsOneWidget);
    expect(find.byType(RefreshIndicator), findsNothing);
    expect(find.byTooltip('Rescan folder'), findsOneWidget);

    await tester.pump();
    await tester.pump();
    expect(scans, 1);

    await tester.tap(find.text('Choose folder'));
    await tester.pumpAndSettle();
    expect(find.text('Chapter one'), findsOneWidget);
  });

  testWidgets('failed new-root scan never restores old-root results', (
    tester,
  ) async {
    var selections = 0;
    var scans = 0;
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async {
          if (scans++ == 0) return <Media>[media];
          throw StateError('provider busy');
        },
        chooseRoot: () async {
          selections++;
          return selections == 1;
        },
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Choose folder'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Could not scan local media.'), findsOneWidget);

    await tester.tap(find.text('Choose folder'));
    await tester.pumpAndSettle();

    expect(find.text('Spirited Away'), findsNothing);
    expect(find.textContaining('Could not scan local media.'), findsOneWidget);
  });

  testWidgets('new-root scan error retries without reopening picker', (
    tester,
  ) async {
    var scans = 0;
    var selections = 0;
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async {
          scans++;
          if (scans == 1) return <Media>[media];
          if (scans == 2) throw StateError('provider busy');
          return <Media>[replacement];
        },
        chooseRoot: () async {
          selections++;
          return true;
        },
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Choose folder'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Could not scan local media.'), findsOneWidget);

    await tester.tap(find.text('Try Again'));
    await tester.pumpAndSettle();

    expect(scans, 3);
    expect(selections, 1);
    expect(find.text('Chapter one'), findsOneWidget);
  });

  testWidgets('explicit rescan action refreshes the selected root', (
    tester,
  ) async {
    var scans = 0;
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async =>
            scans++ == 0 ? <Media>[media] : <Media>[replacement],
        chooseRoot: () async => false,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Rescan folder'));
    await tester.pumpAndSettle();

    expect(scans, 2);
    expect(find.text('Spirited Away'), findsNothing);
    expect(find.text('Chapter one'), findsOneWidget);
  });

  testWidgets('loading then empty results render deterministically', (
    tester,
  ) async {
    final pending = Completer<List<Media>?>();
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () => pending.future,
        chooseRoot: () async => false,
        openMedia: (_, _) {},
      ),
    );

    await tester.pump();
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    pending.complete(<Media>[]);
    await tester.pumpAndSettle();
    expect(find.text('No local media found.'), findsOneWidget);
  });

  testWidgets('all classification labels render', (tester) async {
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async => [
          for (final type in MediaType.values)
            Media(title: type.name, type: type, source: media.source),
        ],
        chooseRoot: () async => false,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();

    for (final label in ['Anime', 'Manga', 'Light Novel']) {
      expect(find.text(label), findsWidgets);
    }
  });

  testWidgets('unsupported state does not restore or scan', (tester) async {
    var called = false;

    await tester.pumpWidget(
      host(
        supported: false,
        scanSelectedRoot: () async {
          called = true;
          return <Media>[];
        },
        chooseRoot: () async {
          called = true;
          return true;
        },
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('Local media is not supported on this device.'),
      findsOneWidget,
    );
    expect(called, isFalse);
  });

  testWidgets('opens selected media from restored results', (tester) async {
    BuildContext? openedContext;
    Media? openedMedia;

    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async => <Media>[media],
        chooseRoot: () async => false,
        openMedia: (context, selected) {
          openedContext = context;
          openedMedia = selected;
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tapAt(tester.getCenter(find.text('Spirited Away')));

    expect(openedContext, isNotNull);
    expect(openedMedia, media);
  });
  testWidgets('Local uses one standard app bar with contextual rescan', (
    tester,
  ) async {
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async => <Media>[media],
        chooseRoot: () async => false,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Local'), findsOneWidget);
    expect(
      find.ancestor(of: find.text('Local'), matching: find.byType(AppBar)),
      findsOneWidget,
    );
    expect(
      find.ancestor(
        of: find.byTooltip('Rescan folder'),
        matching: find.byType(AppBar),
      ),
      findsOneWidget,
    );
    expect(find.text('Choose folder'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'media filter keeps the root context and recovers from no match',
    (tester) async {
      await tester.pumpWidget(
        host(
          rootName: 'Comics & books',
          scanSelectedRoot: () async => [media, replacement],
          chooseRoot: () async => false,
          openMedia: (_, _) {},
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Comics & books'), findsOneWidget);
      await tester.tap(find.byType(FilterChip).at(2)); // Manga has no match.
      await tester.pumpAndSettle();
      expect(find.text('No matching local media'), findsOneWidget);
      expect(find.text('Spirited Away'), findsNothing);
      await tester.tap(find.text('Show all types'));
      await tester.pumpAndSettle();
      expect(find.text('Spirited Away'), findsOneWidget);
      expect(find.text('Chapter one'), findsOneWidget);
    },
  );

  testWidgets('access loss asks to choose the folder again', (tester) async {
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async => throw const LocalMediaAccessException(),
        chooseRoot: () async => false,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Folder access lost'), findsOneWidget);
    expect(find.byTooltip('Rescan folder'), findsNothing);
    expect(find.text('Try Again'), findsNothing);
    expect(find.text('Choose folder'), findsOneWidget);
  });

  testWidgets('scan errors retain the last root name and media filter', (
    tester,
  ) async {
    var scans = 0;
    await tester.pumpWidget(
      host(
        rootName: 'Offline comics',
        scanSelectedRoot: () async {
          if (scans++ == 0) return [media, replacement];
          throw StateError('Provider temporarily unavailable');
        },
        chooseRoot: () async => false,
        openMedia: (_, _) {},
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byType(FilterChip).last);
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Rescan folder'));
    await tester.pumpAndSettle();
    expect(find.text('Offline comics'), findsOneWidget);
    expect(find.text('Chapter one'), findsOneWidget);
    expect(find.text('Spirited Away'), findsNothing);
    expect(
      find.textContaining('Could not rescan this folder.'),
      findsOneWidget,
    );
  });

  testWidgets(
    'picker error retains last scanned root instead of losing its media',
    (tester) async {
      await tester.pumpWidget(
        host(
          rootName: 'Offline library',
          scanSelectedRoot: () async => [media],
          chooseRoot: () async => throw PlatformException(
            code: 'storage',
            message: 'Provider is busy',
          ),
          openMedia: (_, _) {},
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Choose folder'));
      await tester.pumpAndSettle();
      expect(find.text('Offline library'), findsOneWidget);
      expect(find.text('Spirited Away'), findsOneWidget);
      expect(
        find.textContaining('Could not choose another folder.'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'changing the filter during refresh is not overwritten by its result',
    (tester) async {
      final rescan = Completer<List<Media>?>();
      var scans = 0;
      await tester.pumpWidget(
        host(
          scanSelectedRoot: () =>
              scans++ == 0 ? Future.value([media, replacement]) : rescan.future,
          chooseRoot: () async => false,
          openMedia: (_, _) {},
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Rescan folder'));
      await tester.pump();
      await tester.tap(find.byType(FilterChip).last);
      await tester.pump();
      rescan.complete([media, replacement]);
      await tester.pumpAndSettle();
      expect(find.text('Chapter one'), findsOneWidget);
      expect(find.text('Spirited Away'), findsNothing);
    },
  );

  testWidgets('local cards use shared artwork and poster widgets', (
    tester,
  ) async {
    const previewA = SourceMediaRef(
      sourceId: SourceId.local,
      itemId: 'preview-a',
    );
    const previewB = SourceMediaRef(
      sourceId: SourceId.local,
      itemId: 'preview-b',
    );
    final reads = <SourceMediaRef>[];
    Future<Uint8List?> read(SourceMediaRef ref) async {
      reads.add(ref);
      return null; // Missing artwork keeps the standard poster fallback.
    }

    Widget page(SourceMediaRef preview, int revision) => host(
      scanSelectedRoot: () async => [media],
      chooseRoot: () async => false,
      openMedia: (_, _) {},
      artwork: {media.source: preview},
      // Mimics a new closure on a normal parent rebuild.
      readArtwork: (ref) => read(ref),
      scanRevision: revision,
    );

    await tester.pumpWidget(page(previewA, 0));
    await tester.pumpAndSettle();
    expect(find.byType(SourceArtwork), findsOneWidget);
    expect(find.byType(MediaPoster), findsOneWidget);
    expect(find.text('Spirited Away'), findsOneWidget);
    expect(reads, [previewA]);

    // Rebuilding the parent with the same preview should retain its Future.
    await tester.pumpWidget(page(previewA, 0));
    await tester.pumpAndSettle();
    expect(reads, [previewA]);

    // Replacing the selected scan changes the preview without changing Media.
    await tester.pumpWidget(page(previewB, 1));
    await tester.pumpAndSettle();
    expect(reads, [previewA, previewB]);
  });

  testWidgets('only lazily built posters request artwork', (tester) async {
    final items = List<Media>.generate(
      300,
      (index) => Media(
        title: 'Comic $index',
        type: MediaType.manga,
        source: SourceMediaRef(
          sourceId: SourceId.local,
          itemId: 'comic-$index',
        ),
      ),
    );
    final artwork = <SourceMediaRef, SourceMediaRef>{
      for (var index = 0; index < items.length; index++)
        items[index].source: SourceMediaRef(
          sourceId: SourceId.local,
          itemId: 'preview-$index',
        ),
    };
    var reads = 0;
    await tester.pumpWidget(
      host(
        scanSelectedRoot: () async => items,
        chooseRoot: () async => false,
        openMedia: (_, _) {},
        artwork: artwork,
        readArtwork: (_) async {
          reads++;
          return null;
        },
      ),
    );
    await tester.pumpAndSettle();
    expect(reads, greaterThan(0));
    expect(reads, lessThan(items.length));
    expect(find.text('Comic 299'), findsNothing);
  });
}
