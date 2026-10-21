import 'package:flutter/material.dart';
import 'package:hikari/domain/plugins/novel_plugins.dart';

/// Exact artifact tuple shown at consent boundaries; presentation only.
final class NovelArtifactDetails extends StatelessWidget {
  const NovelArtifactDetails({super.key, required this.artifact});
  final NovelArtifact artifact;

  @override
  Widget build(BuildContext context) {
    final m = artifact.metadata;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SelectableText('ID: ${m.id.upstreamId}'),
        SelectableText('Name: ${m.name}'),
        SelectableText('Version: ${m.version.value}'),
        SelectableText('Repository: ${m.repositoryId}'),
        SelectableText('Digest: ${artifact.digest}'),
        SelectableText('Site: ${m.site}'),
        for (final origin in m.origins) SelectableText('Exact origin: $origin'),
      ],
    );
  }
}
