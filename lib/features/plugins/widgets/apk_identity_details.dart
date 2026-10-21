import 'package:flutter/material.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';

/// Displays inspected identity evidence without changing trust/consent.
final class ApkIdentityDetails extends StatelessWidget {
  const ApkIdentityDetails({super.key, required this.identity});
  final ApkPluginIdentity identity;

  @override
  Widget build(BuildContext context) {
    final row = identity;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SelectableText(
          '${pluginEcosystemLabel(row.id.ecosystem)} · ${row.id.upstreamId}',
        ),
        Text('Version: ${row.version.name} (${row.version.code})'),
        Text(
          'Compatibility: ${apkCompatibilityLabel(row.compatibility)} · native trust: ${apkTrustLabel(row.trust)}',
        ),
        SelectableText('Current signers: ${row.currentSigners.join(', ')}'),
        SelectableText(
          'Signer history (not consent): ${row.signerHistory.join(', ')}',
        ),
      ],
    );
  }
}
