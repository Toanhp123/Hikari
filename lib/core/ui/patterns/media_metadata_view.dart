import 'dart:typed_data';

import 'package:flutter/material.dart';

import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';

/// Presentation component for source-provided media metadata in series details.
class MediaMetadataView extends StatefulWidget {
  const MediaMetadataView({
    super.key,
    required this.metadata,
    required this.sourceName,
    this.readArtwork,
  });

  final MediaMetadata metadata;
  final String sourceName;
  final Future<Uint8List?> Function(SourceMediaRef)? readArtwork;

  @override
  State<MediaMetadataView> createState() => _MediaMetadataViewState();
}

class _MediaMetadataViewState extends State<MediaMetadataView>
    with AutomaticKeepAliveClientMixin {
  @override
  bool get wantKeepAlive => true;
  bool _summaryExpanded = false;

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final metadata = widget.metadata;
    final hasArtwork = metadata.cover != null && widget.readArtwork != null;

    return LayoutBuilder(
      builder: (context, constraints) {
        final isWide = constraints.maxWidth >= 600;

        return Padding(
          padding: const EdgeInsets.all(HikariSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Top Identity & Summary Row / Header
              if (isWide)
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (hasArtwork) ...[
                      _buildArtwork(context, width: 140, height: 200),
                      const SizedBox(width: HikariSpacing.xl),
                    ],
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _buildSourceAndStatusBadges(context),
                          const SizedBox(height: HikariSpacing.sm),
                          _buildPeopleAndInfo(context),
                          if (metadata.summary != null &&
                              metadata.summary!.trim().isNotEmpty) ...[
                            const SizedBox(height: HikariSpacing.md),
                            _buildSummary(context),
                          ],
                        ],
                      ),
                    ),
                  ],
                )
              else
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (hasArtwork) ...[
                      _buildArtwork(context, width: 104, height: 152),
                      const SizedBox(width: HikariSpacing.md),
                    ],
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _buildSourceAndStatusBadges(context),
                          const SizedBox(height: HikariSpacing.xs),
                          _buildPeopleAndInfo(context),
                        ],
                      ),
                    ),
                  ],
                ),

              // Summary in compact mode appears below the artwork row
              if (!isWide &&
                  metadata.summary != null &&
                  metadata.summary!.trim().isNotEmpty) ...[
                const SizedBox(height: HikariSpacing.md),
                _buildSummary(context),
              ],

              // Genres and Tags Chips
              if (metadata.genres.isNotEmpty || metadata.tags.isNotEmpty) ...[
                const SizedBox(height: HikariSpacing.md),
                Wrap(
                  spacing: HikariSpacing.xs,
                  runSpacing: HikariSpacing.xs,
                  children: [
                    for (final genre in metadata.genres)
                      _MetadataTag(label: genre),
                    for (final tag in metadata.tags)
                      _MetadataTag(label: tag, isTag: true),
                  ],
                ),
              ],
            ],
          ),
        );
      },
    );
  }

  Widget _buildArtwork(
    BuildContext context, {
    required double width,
    required double height,
  }) {
    return SizedBox(
      width: width,
      height: height,
      child: SourceArtwork(
        key: ValueKey(widget.metadata.cover),
        resource: widget.metadata.cover!,
        read: widget.readArtwork!,
      ),
    );
  }

  Widget _buildSourceAndStatusBadges(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final metadata = widget.metadata;

    return Wrap(
      spacing: HikariSpacing.xs,
      runSpacing: HikariSpacing.xs,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        // Source Name Badge
        DecoratedBox(
          decoration: BoxDecoration(
            color: colors.surfaceContainerHighest,
            borderRadius: HikariRadius.borderSm,
            border: Border.all(
              color: colors.outlineVariant.withValues(alpha: 0.5),
            ),
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            child: Text(
              widget.sourceName,
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: colors.onSurface,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ),

        // Status Badge
        if (metadata.rawStatus != null ||
            metadata.status != PublicationStatus.unknown)
          _StatusBadge(status: metadata.status, rawStatus: metadata.rawStatus),

        // Rating Badge
        if (metadata.rating != null) ...[
          DecoratedBox(
            decoration: BoxDecoration(
              color: colors.surfaceContainer,
              borderRadius: HikariRadius.borderSm,
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.star_rounded, size: 15, color: colors.primary),
                  const SizedBox(width: 3),
                  Flexible(
                    child: Text(
                      'Rating: ${metadata.rating}${metadata.ratingMax == null ? '' : ' / ${metadata.ratingMax}'}',
                      style: Theme.of(context).textTheme.labelSmall?.copyWith(
                        color: colors.onSurface,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildPeopleAndInfo(BuildContext context) {
    final metadata = widget.metadata;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (metadata.authors.isNotEmpty)
          _InfoLine(label: 'Author', value: metadata.authors.join(', ')),
        if (metadata.artists.isNotEmpty)
          _InfoLine(label: 'Artist', value: metadata.artists.join(', ')),
        if (metadata.language != null && metadata.language!.isNotEmpty)
          _InfoLine(label: 'Language', value: metadata.language!),
        if (metadata.publisher != null && metadata.publisher!.isNotEmpty)
          _InfoLine(label: 'Publisher', value: metadata.publisher!),
      ],
    );
  }

  Widget _buildSummary(BuildContext context) {
    final summary = widget.metadata.summary!;
    final colors = Theme.of(context).colorScheme;
    final canExpand = summary.length > 160;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          summary,
          maxLines: _summaryExpanded ? null : 4,
          overflow: _summaryExpanded ? TextOverflow.visible : TextOverflow.fade,
          style: Theme.of(context).textTheme.bodyMedium
              ?.copyWith(color: colors.onSurfaceVariant, height: 1.5),
        ),
        if (canExpand)
          Padding(
            padding: const EdgeInsets.only(top: HikariSpacing.xs),
            child: TextButton(
              onPressed: () =>
                  setState(() => _summaryExpanded = !_summaryExpanded),
              child: Text(_summaryExpanded ? 'Show less' : 'Show more'),
            ),
          ),
      ],
    );
  }
}

class _InfoLine extends StatelessWidget {
  const _InfoLine({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;

    return Padding(
      padding: const EdgeInsets.only(top: 2),
      child: Text.rich(
        TextSpan(
          text: '$label: ',
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            color: colors.onSurfaceVariant,
            fontWeight: FontWeight.w500,
          ),
          children: [
            TextSpan(
              text: value,
              style: TextStyle(
                color: colors.onSurface,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.status, this.rawStatus});

  final PublicationStatus status;
  final String? rawStatus;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final label = rawStatus ?? _publicationStatusLabel(status);

    final (badgeBg, badgeFg) = switch (status) {
      PublicationStatus.ongoing => (
        colors.primaryContainer.withValues(alpha: 0.6),
        colors.onPrimaryContainer,
      ),
      PublicationStatus.completed => (
        colors.tertiaryContainer.withValues(alpha: 0.6),
        colors.onTertiaryContainer,
      ),
      PublicationStatus.cancelled || PublicationStatus.onHiatus => (
        colors.errorContainer.withValues(alpha: 0.6),
        colors.onErrorContainer,
      ),
      _ => (colors.surfaceContainerHighest, colors.onSurfaceVariant),
    };

    return DecoratedBox(
      decoration: BoxDecoration(
        color: badgeBg,
        borderRadius: HikariRadius.borderSm,
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
        child: Text(
          label,
          style: Theme.of(context).textTheme.labelSmall
              ?.copyWith(color: badgeFg, fontWeight: FontWeight.w600),
        ),
      ),
    );
  }
}

class _MetadataTag extends StatelessWidget {
  const _MetadataTag({required this.label, this.isTag = false});

  final String label;
  final bool isTag;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;

    return DecoratedBox(
      decoration: BoxDecoration(
        color: isTag ? colors.surfaceContainer : colors.surfaceContainerHigh,
        borderRadius: HikariRadius.borderXl,
        border: Border.all(color: colors.outlineVariant.withValues(alpha: 0.5)),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        child: Text(
          label,
          style: Theme.of(context).textTheme.labelSmall?.copyWith(
            color: colors.onSurfaceVariant,
            fontWeight: FontWeight.w600,
          ),
        ),
      ),
    );
  }
}

/// Loads optional source-owned artwork without exposing provider details to UI.
///
/// The future is retained while [resource] is unchanged. Missing, empty, or
/// failed artwork resolves to null so presentation can fall back gracefully.
class SourceArtwork extends StatefulWidget {
  const SourceArtwork({
    super.key,
    required this.resource,
    required this.read,
    this.builder,
  });

  final SourceMediaRef resource;
  final Future<Uint8List?> Function(SourceMediaRef) read;
  final Widget Function(BuildContext, Uint8List?, bool)? builder;

  @override
  State<SourceArtwork> createState() => _SourceArtworkState();
}

class _SourceArtworkState extends State<SourceArtwork> {
  late Future<Uint8List?> _bytes = _read();

  Future<Uint8List?> _read() async {
    try {
      final bytes = await widget.read(widget.resource);
      return bytes == null || bytes.isEmpty ? null : bytes;
    } catch (_) {
      return null;
    }
  }

  @override
  void didUpdateWidget(SourceArtwork oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.resource != widget.resource) {
      _bytes = _read();
    }
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<Uint8List?>(
    future: _bytes,
    builder: (context, snapshot) {
      final loading = snapshot.connectionState != ConnectionState.done;
      final builder = widget.builder;
      if (builder != null) return builder(context, snapshot.data, loading);

      return Container(
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surfaceContainer,
          borderRadius: HikariRadius.borderMd,
          border: Border.all(
            color: Theme.of(context).colorScheme.outlineVariant
                .withValues(alpha: 0.5),
          ),
        ),
        clipBehavior: Clip.antiAlias,
        child: _defaultArtwork(snapshot.data, loading),
      );
    },
  );

  Widget _defaultArtwork(Uint8List? bytes, bool loading) {
    if (loading) {
      return const Center(child: CircularProgressIndicator.adaptive());
    }
    if (bytes == null) {
      return const Center(
        child: Icon(
          Icons.broken_image_rounded,
          semanticLabel: 'Cover unavailable',
        ),
      );
    }
    return Image.memory(
      bytes,
      fit: BoxFit.cover,
      semanticLabel: 'Cover',
      errorBuilder: (_, _, _) => const Center(
        child: Icon(
          Icons.broken_image_rounded,
          semanticLabel: 'Cover unavailable',
        ),
      ),
    );
  }
}

String _publicationStatusLabel(PublicationStatus status) => switch (status) {
  PublicationStatus.unknown => 'Unknown',
  PublicationStatus.ongoing => 'Ongoing',
  PublicationStatus.completed => 'Completed',
  PublicationStatus.licensed => 'Licensed',
  PublicationStatus.publishingFinished => 'Publishing finished',
  PublicationStatus.cancelled => 'Cancelled',
  PublicationStatus.onHiatus => 'On hiatus',
};
