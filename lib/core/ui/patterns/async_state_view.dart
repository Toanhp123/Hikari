import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';

enum AsyncViewStatus { initial, loading, content, empty, error }

/// Unified UX pattern for managing async loading, empty, content, and error states.
class AsyncStateView extends StatelessWidget {
  const AsyncStateView({
    super.key,
    required this.status,
    required this.contentBuilder,
    this.emptyTitle = 'No items found',
    this.emptyMessage = 'There is currently nothing to display here.',
    this.emptyIcon = Icons.inbox_rounded,
    this.emptyAction,
    this.errorMessage,
    this.errorTitle = 'Something went wrong',
    this.onRetry,
    this.loadingWidget,
  });

  final AsyncViewStatus status;
  final WidgetBuilder contentBuilder;
  final String emptyTitle;
  final String emptyMessage;
  final IconData emptyIcon;
  final Widget? emptyAction;
  final String? errorMessage;
  final String errorTitle;
  final VoidCallback? onRetry;
  final Widget? loadingWidget;

  @override
  Widget build(BuildContext context) {
    switch (status) {
      case AsyncViewStatus.initial:
        return const SizedBox.shrink();
      case AsyncViewStatus.loading:
        return loadingWidget ?? _defaultLoadingShimmer(context);
      case AsyncViewStatus.empty:
        return _buildEmptyState(context);
      case AsyncViewStatus.error:
        return _buildErrorState(context);
      case AsyncViewStatus.content:
        return contentBuilder(context);
    }
  }

  Widget _defaultLoadingShimmer(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          SizedBox(
            width: 32,
            height: 32,
            child: CircularProgressIndicator(
              strokeWidth: 3,
              valueColor: AlwaysStoppedAnimation<Color>(colors.primary),
            ),
          ),
          const SizedBox(height: HikariSpacing.md),
          Text(
            'Loading content...',
            style: Theme.of(context).textTheme.bodySmall
                ?.copyWith(color: colors.onSurfaceVariant),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(HikariSpacing.xl),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: colors.surfaceContainer,
                shape: BoxShape.circle,
                border: Border.all(color: colors.outline),
              ),
              child: Icon(emptyIcon, size: 36, color: colors.onSurfaceVariant),
            ),
            const SizedBox(height: HikariSpacing.lg),
            Text(
              emptyTitle,
              style: Theme.of(context).textTheme.titleMedium
                  ?.copyWith(color: colors.onSurface),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: HikariSpacing.xs),
            Text(
              emptyMessage,
              style: Theme.of(context).textTheme.bodySmall
                  ?.copyWith(color: colors.onSurfaceVariant),
              textAlign: TextAlign.center,
            ),
            if (emptyAction != null) ...[
              const SizedBox(height: HikariSpacing.lg),
              emptyAction!,
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildErrorState(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(HikariSpacing.xl),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                color: colors.error.withValues(alpha: 0.12),
                shape: BoxShape.circle,
                border: Border.all(color: colors.error.withValues(alpha: 0.4)),
              ),
              child: Icon(
                Icons.error_outline_rounded,
                size: 36,
                color: colors.error,
              ),
            ),
            const SizedBox(height: HikariSpacing.lg),
            Text(
              errorTitle,
              style: Theme.of(context).textTheme.titleMedium
                  ?.copyWith(color: colors.onSurface),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: HikariSpacing.xs),
            Text(
              errorMessage ?? 'An error occurred while loading data.',
              style: Theme.of(context).textTheme.bodySmall
                  ?.copyWith(color: colors.onSurfaceVariant),
              textAlign: TextAlign.center,
            ),
            if (onRetry != null) ...[
              const SizedBox(height: HikariSpacing.lg),
              HikariButton(
                label: 'Try Again',
                icon: const Icon(Icons.refresh_rounded, size: 16),
                variant: HikariButtonVariant.secondary,
                onPressed: onRetry,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
