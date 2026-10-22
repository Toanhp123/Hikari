import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/content/hikari_section_header.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';
import 'package:hikari/features/plugins/plugin_manager_view_model.dart';
import 'package:hikari/features/plugins/widgets/plugin_notice.dart';

String _version(PluginVersion value) => switch (value) {
  ApkPluginVersion() => '${value.name} (${value.code})',
  NovelPluginVersion() => value.value,
};

/// Presentation-only detail/ownership choice; no trust or package mutation.
final class PluginDetailsDialog extends StatelessWidget {
  const PluginDetailsDialog({
    super.key,
    required this.id,
    required this.owner,
    required this.repositories,
    required this.details,
    required this.name,
    required this.runtime,
    required this.busy,
    this.onOpenApkActions,
    this.onOpenNovelActions,
  });

  final PluginId id;
  final RepositoryId? owner;
  final List<PluginRepository> repositories;
  final PluginDetails details;
  final String name;
  final String runtime;
  final bool busy;
  final VoidCallback? onOpenApkActions;
  final VoidCallback? onOpenNovelActions;

  @override
  Widget build(BuildContext context) {
    final offers = details.offers;
    final apk = details.apk;
    final novel = details.novel;
    final runningApk = details.runningApk;
    final runningNovel = details.runningNovel;
    return AlertDialog(
      title: Text(name),
      content: SizedBox(
        width: 480,
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              const HikariSectionHeader(title: 'Identity'),
              SelectableText(
                '${pluginEcosystemLabel(id.ecosystem)} · ${id.upstreamId}',
              ),
              Text('Owner: ${owner ?? 'Not selected'}'),
              Text('Runtime: $runtime'),
              if (apk != null) ...[
                const SizedBox(height: HikariSpacing.lg),
                const HikariSectionHeader(title: 'Installed package'),
                Text('Installed: ${_version(apk.version)}'),
                Text('Native trust: ${apkTrustLabel(apk.trust)}'),
                Text(
                  'Compatibility: ${apkCompatibilityLabel(apk.compatibility)}',
                ),
                SelectableText(
                  'Current signers: ${apk.currentSigners.join(', ')}',
                ),
              ],
              if (novel != null) ...[
                const SizedBox(height: HikariSpacing.lg),
                const HikariSectionHeader(title: 'Stored bundle'),
                Text(
                  'Installed: ${_version(novel.current.metadata.version)} · ${novelStatusLabel(novel.status)}',
                ),
                Text(
                  'Admission: ${novelAdmissionLabel(novel.admission)}; provider compatibility unknown',
                ),
                SelectableText('Digest: ${novel.current.digest}'),
                SelectableText(
                  'Repository: ${novel.current.metadata.repositoryId} · site: ${novel.current.metadata.site} · origins: ${novel.current.metadata.origins.join(', ')}',
                ),
                if (novel.previous != null)
                  Text(
                    'Previous: ${_version(novel.previous!.metadata.version)}',
                  ),
              ],
              if (runningApk != null) ...[
                const SizedBox(height: HikariSpacing.lg),
                const HikariSectionHeader(title: 'Loaded runtime'),
                SelectableText(
                  'Loaded: ${runningApk.versionCode} · ${runningApk.signer}',
                ),
              ],
              if (runningNovel?.artifact != null) ...[
                const SizedBox(height: HikariSpacing.lg),
                const HikariSectionHeader(title: 'Loaded runtime'),
                Text(
                  'Loaded: ${_version(runningNovel!.artifact!.metadata.version)}',
                ),
                SelectableText(
                  'Loaded name: ${runningNovel.artifact!.metadata.name} · digest: ${runningNovel.artifact!.digest}',
                ),
                SelectableText(
                  'Loaded repository: ${runningNovel.artifact!.metadata.repositoryId}',
                ),
                SelectableText(
                  'Loaded site: ${runningNovel.artifact!.metadata.site}',
                ),
                SelectableText(
                  'Loaded origins: ${runningNovel.artifact!.metadata.origins.join(', ')}',
                ),
              ],
              if (offers.isNotEmpty) ...[
                const SizedBox(height: HikariSpacing.lg),
                const HikariSectionHeader(title: 'Repository offers'),
              ],
              for (final offer in offers)
                Padding(
                  padding: const EdgeInsets.only(top: HikariSpacing.sm),
                  child: Card(
                    child: Padding(
                      padding: const EdgeInsets.all(HikariSpacing.md),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Available: ${_version(offer.version)}',
                            style: Theme.of(context).textTheme.titleSmall,
                          ),
                          const SizedBox(height: HikariSpacing.xs),
                          SelectableText('Repository: ${offer.repositoryId}'),
                          if (id.ecosystem != PluginEcosystem.novel)
                            SelectableText(
                              'Repository signer expectation: ${repositories.where((r) => r.id == offer.repositoryId).firstOrNull?.snapshot?.signerFingerprint ?? 'Not declared'}',
                            ),
                          Text(
                            'Offer compatibility: ${pluginCompatibilityLabel(offer.compatibility)}',
                          ),
                          const SizedBox(height: HikariSpacing.sm),
                          if (owner != offer.repositoryId)
                            OutlinedButton(
                              onPressed: busy
                                  ? null
                                  : () => Navigator.pop(
                                      context,
                                      offer.repositoryId,
                                    ),
                              child: const Text('Select repository owner'),
                            )
                          else
                            const Text('Selected owner'),
                          if (offer.sources.isNotEmpty)
                            ExpansionTile(
                              tilePadding: EdgeInsets.zero,
                              childrenPadding: const EdgeInsets.only(
                                bottom: HikariSpacing.sm,
                              ),
                              shape: const Border(),
                              collapsedShape: const Border(),
                              title: Text('Sources (${offer.sources.length})'),
                              children: [
                                for (final source in offer.sources)
                                  Align(
                                    alignment: AlignmentDirectional.centerStart,
                                    child: Text(
                                      '${source.name} · ${source.language} · ${source.upstreamId}',
                                    ),
                                  ),
                              ],
                            ),
                        ],
                      ),
                    ),
                  ),
                ),
              if (id.ecosystem != PluginEcosystem.novel &&
                  onOpenApkActions != null)
                OutlinedButton(
                  onPressed: () {
                    Navigator.pop(context);
                    onOpenApkActions?.call();
                  },
                  child: const Text('APK actions'),
                ),
              if (id.ecosystem == PluginEcosystem.novel &&
                  onOpenNovelActions != null)
                OutlinedButton(
                  onPressed: () {
                    Navigator.pop(context);
                    onOpenNovelActions?.call();
                  },
                  child: const Text('Novel actions'),
                ),
              const SizedBox(height: HikariSpacing.md),
              const PluginNotice(
                message: 'Repository metadata is not execution trust. Package actions require separate explicit consent. Existing readers keep their frozen sources.',
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Close'),
        ),
      ],
    );
  }
}
