import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/hikari_ui.dart';

final _corrupt = MemoryImage(Uint8List.fromList([1, 2, 3]));
final _cover = MemoryImage(
  base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGOQNJvzHwADQgHrKgED3AAAAABJRU5ErkJggg==',
  ),
);

/// Synthetic fixtures only; no source registration, network or user storage.
class MediaPreview extends StatelessWidget {
  const MediaPreview({
    super.key,
    required this.width,
    required this.stress,
    required this.enabled,
  });
  final double width;
  final bool stress;
  final bool enabled;

  @override
  Widget build(BuildContext context) => Align(
    alignment: AlignmentDirectional.topStart,
    child: SizedBox(
      width: width,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const HikariSectionHeader(title: 'Media / Preview'),
          HikariHorizontalShelf(
            header: const Text('Missing / corrupt / decoded covers'),
            itemCount: stress ? 5000 : 6,
            itemKeyBuilder: (i) => ValueKey(('media-shelf', i)),
            itemBuilder: (context, i) => HikariPosterCard(
              title: 'Hành trình dài qua vùng đất العربية $i',
              subtitle: 'Source $i',
              artwork: HikariArtwork(
                image: switch (i % 3) {
                  0 => null,
                  1 => _corrupt,
                  _ => _cover,
                },
              ),
              badge: const Badge(label: Text('MANGA')),
              progress: i.isEven ? .42 : null,
              onTap: enabled ? () {} : null,
            ),
          ),
          HikariLandscapeCard(
            title: 'Landscape · Hành trình العربية',
            artwork: HikariArtwork(image: _cover),
            progress: .75,
            onTap: enabled ? () {} : null,
          ),
          HikariChapterRow(
            title: 'Chapter · العربية',
            subtitle: 'Team · 2026-10-23',
            enabled: enabled,
            isContinuation: true,
            onTap: () {},
          ),
          SizedBox(
            height: 400,
            child: HikariAdaptiveGrid.builder(
              itemCount: stress ? 5000 : 12,
              itemKeyBuilder: (i) => ValueKey(('media-grid', i)),
              itemExtentBuilder: (context, width) =>
                  hikariCardExtent(context, width: width, hasSubtitle: true),
              itemBuilder: (_, i) => HikariPosterCard(
                title: 'Hành trình qua vùng đất العربية $i',
                subtitle: 'Source $i',
                artwork: HikariArtwork(image: i.isEven ? null : _cover),
                onTap: enabled ? () {} : null,
              ),
            ),
          ),
        ],
      ),
    ),
  );
}
