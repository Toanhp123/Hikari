import 'package:flutter/material.dart';
import 'package:hikari/core/ui/hikari_ui.dart';

/// Interaction-only samples. They never authorize or mutate application data.
class SurfacePreview extends StatefulWidget {
  const SurfacePreview({super.key});

  @override
  State<SurfacePreview> createState() => _SurfacePreviewState();
}

class _SurfacePreviewState extends State<SurfacePreview> {
  String _lastResult = 'none';

  void _record(HikariPresentationResult<bool> result) {
    if (!mounted) return;
    setState(() {
      _lastResult = result is HikariCompleted<bool> ? 'completed' : 'cancelled';
    });
  }

  Future<void> _openDialog() async {
    final result = await showHikariDialog<bool>(
      context: context,
      useRootNavigator: true,
      contentBuilder: (_, controller) => HikariDialog(
        title: const Text('Dialog preview'),
        body: const Text('Typed completion is different from cancellation.'),
        onClose: () => controller.cancel(HikariDismissReason.closeButton),
        actions: [
          HikariButton(
            label: 'Complete preview',
            onPressed: () => controller.complete(true),
          ),
        ],
      ),
    );
    _record(result);
  }

  Future<void> _openSheet() async {
    final result = await showHikariBottomSheet<bool>(
      context: context,
      useRootNavigator: true,
      contentBuilder: (_, controller) => HikariBottomSheet(
        title: const Text('Sheet preview'),
        body: const Text('A bounded native sheet with a close action.'),
        onClose: () => controller.cancel(HikariDismissReason.closeButton),
        actions: [
          HikariButton(
            label: 'Complete preview',
            onPressed: () => controller.complete(true),
          ),
        ],
      ),
    );
    _record(result);
  }

  Future<void> _openConfirmation() async {
    final result = await showHikariConfirmationDialog(
      context: context,
      useRootNavigator: true,
      title: 'Confirm preview',
      content: const Text('No application data is changed.'),
      confirmLabel: 'Confirm',
    );
    _record(result);
  }

  Future<void> _openAdaptive() async {
    final result = await showHikariAdaptive<bool>(
      context: context,
      purpose: HikariPresentationPurpose.selection,
      useRootNavigator: true,
      contentBuilder: (_, controller) => Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('Adaptive selection preview'),
          const SizedBox(height: HikariSpacing.lg),
          HikariButton(
            label: 'Choose sample',
            onPressed: () => controller.complete(true),
          ),
          TextButton(
            onPressed: () =>
                controller.cancel(HikariDismissReason.cancelAction),
            child: const Text('Cancel'),
          ),
        ],
      ),
    );
    _record(result);
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      const HikariSectionHeader(
        title: 'Surfaces',
        description: 'Canonical dialog, bottom sheet, confirmation and adaptive selection.',
      ),
      Wrap(
        spacing: HikariSpacing.sm,
        runSpacing: HikariSpacing.sm,
        children: [
          HikariButton(label: 'Dialog preview', onPressed: _openDialog),
          HikariButton(label: 'Sheet preview', onPressed: _openSheet),
          HikariButton(
            label: 'Confirmation preview',
            onPressed: _openConfirmation,
          ),
          HikariButton(label: 'Adaptive preview', onPressed: _openAdaptive),
        ],
      ),
      const SizedBox(height: HikariSpacing.md),
      Text('Last interaction: $_lastResult'),
    ],
  );
}
