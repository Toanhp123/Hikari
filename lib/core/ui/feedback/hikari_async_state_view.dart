import 'package:flutter/material.dart';

import '../controls/hikari_button.dart';
import '../foundation/hikari_spacing.dart';
import 'hikari_notice_banner.dart';

enum HikariAsyncStatus {
  initial,
  loading,
  loaded,
  empty,
  refreshing,
  recoverableError,
  fatalError,
}

/// Normalized presentation only. Features decide true empty, retained data and
/// recoverability; no fetching, domain objects or request state lives here.
class HikariAsyncStateView extends StatelessWidget {
  const HikariAsyncStateView({
    super.key,
    required this.status,
    required this.child,
    this.message,
    this.emptyTitle = 'No items found',
    this.emptyMessage = 'There is currently nothing to display here.',
    this.emptyAction,
    this.onRetry,
  });

  final HikariAsyncStatus status;
  final Widget child;
  final String? message;
  final String emptyTitle;
  final String emptyMessage;
  final Widget? emptyAction;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    if (status == HikariAsyncStatus.initial) return const SizedBox.shrink();
    final retained =
        status == HikariAsyncStatus.loaded ||
        status == HikariAsyncStatus.refreshing ||
        status == HikariAsyncStatus.recoverableError;
    if (retained) {
      // Stable child position preserves editor/scroll/focus state between updates.
      // Feedback reserves space instead of covering retained focus targets.
      return LayoutBuilder(
        builder: (context, constraints) => Column(
          children: [
            Expanded(child: child),
            if (status == HikariAsyncStatus.refreshing)
              Semantics(
                label: 'Refreshing',
                liveRegion: true,
                child: const LinearProgressIndicator(),
              ),
            if (status == HikariAsyncStatus.recoverableError)
              ConstrainedBox(
                constraints: BoxConstraints(
                  maxHeight: constraints.maxHeight / 2,
                ),
                child: SingleChildScrollView(
                  primary: false,
                  child: HikariNoticeBanner(
                    message: message ?? 'Could not refresh. Previous content remains available.',
                    tone: HikariNoticeTone.warning,
                    action: onRetry == null
                        ? null
                        : HikariButton(
                            label: 'Try Again',
                            onPressed: onRetry,
                            variant: HikariButtonVariant.secondary,
                          ),
                  ),
                ),
              ),
          ],
        ),
      );
    }
    return Center(
      child: SingleChildScrollView(
        primary: false,
        padding: const EdgeInsets.all(HikariSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (status == HikariAsyncStatus.loading) ...[
              const CircularProgressIndicator(),
              const SizedBox(height: HikariSpacing.md),
              const Text('Loading content...'),
            ] else if (status == HikariAsyncStatus.empty) ...[
              const Icon(Icons.inbox_rounded),
              Text(emptyTitle, textAlign: TextAlign.center),
              Text(emptyMessage, textAlign: TextAlign.center),
              ?emptyAction,
            ] else ...[
              HikariNoticeBanner(
                message: message ?? 'Something went wrong',
                tone: HikariNoticeTone.error,
              ),
              if (onRetry != null)
                HikariButton(label: 'Try Again', onPressed: onRetry),
            ],
          ],
        ),
      ),
    );
  }
}
