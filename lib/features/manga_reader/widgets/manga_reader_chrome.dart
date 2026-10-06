import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';

class MangaReaderTopBar extends StatelessWidget {
  const MangaReaderTopBar({
    super.key,
    required this.title,
    required this.onBack,
    this.credit,
    this.onPreviousChapter,
    this.onNextChapter,
    this.chapterNavigationLoading = false,
  });

  final String title;
  final String? credit;
  final VoidCallback onBack;
  final VoidCallback? onPreviousChapter;
  final VoidCallback? onNextChapter;
  final bool chapterNavigationLoading;

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    return ClipRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
        child: Container(
          padding: EdgeInsets.only(
            top: MediaQuery.paddingOf(context).top + 4,
            bottom: 8,
            left: HikariSpacing.sm,
            right: HikariSpacing.sm,
          ),
          decoration: BoxDecoration(
            color: colors.background.withValues(alpha: 0.85),
            border: Border(bottom: BorderSide(color: colors.borderSubtle)),
          ),
          child: Row(
            children: [
              HikariIconButton(
                icon: const Icon(Icons.arrow_back_rounded),
                tooltip: 'Back',
                onPressed: onBack,
              ),
              const SizedBox(width: HikariSpacing.xs),
              if (onPreviousChapter != null || onNextChapter != null) ...[
                if (onPreviousChapter != null)
                  IconButton(
                    tooltip: 'Previous chapter',
                    onPressed: chapterNavigationLoading
                        ? null
                        : onPreviousChapter,
                    icon: const Icon(Icons.skip_previous),
                    constraints: const BoxConstraints(
                      minWidth: 48,
                      minHeight: 48,
                    ),
                  ),
                if (onNextChapter != null)
                  IconButton(
                    tooltip: 'Next chapter',
                    onPressed: chapterNavigationLoading ? null : onNextChapter,
                    icon: const Icon(Icons.skip_next),
                    constraints: const BoxConstraints(
                      minWidth: 48,
                      minHeight: 48,
                    ),
                  ),
              ],
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: colors.textPrimary,
                      ),
                    ),
                    if (credit != null)
                      Text(
                        credit!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 11,
                          color: colors.textSecondary,
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class MangaReaderPageControls extends StatefulWidget {
  const MangaReaderPageControls({
    super.key,
    required this.pageIndex,
    required this.pageCount,
    required this.loading,
    required this.onPrevious,
    required this.onNext,
    required this.onJumpToPage,
  });

  final int pageIndex;
  final int pageCount;
  final bool loading;
  final VoidCallback onPrevious;
  final VoidCallback onNext;
  final ValueChanged<int> onJumpToPage;

  @override
  State<MangaReaderPageControls> createState() =>
      _MangaReaderPageControlsState();
}

class _MangaReaderPageControlsState extends State<MangaReaderPageControls> {
  double? _dragPage;

  @override
  void didUpdateWidget(MangaReaderPageControls oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.pageIndex != widget.pageIndex ||
        oldWidget.pageCount != widget.pageCount) {
      _dragPage = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    return ClipRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
        child: Container(
          padding: EdgeInsets.only(
            bottom: MediaQuery.paddingOf(context).bottom + 4,
            top: 4,
            left: HikariSpacing.md,
            right: HikariSpacing.md,
          ),
          decoration: BoxDecoration(
            color: colors.background.withValues(alpha: 0.85),
            border: Border(top: BorderSide(color: colors.borderSubtle)),
          ),
          child: Row(
            children: [
              IconButton(
                tooltip: 'Previous page',
                onPressed: widget.loading || widget.pageIndex == 0
                    ? null
                    : widget.onPrevious,
                icon: const Icon(Icons.chevron_left_rounded),
              ),
              Expanded(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      'Page ${widget.pageIndex + 1} of ${widget.pageCount}',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: colors.textPrimary,
                      ),
                    ),
                    SliderTheme(
                      data: SliderThemeData(
                        activeTrackColor: colors.primary,
                        inactiveTrackColor: colors.surfaceHighlight,
                        thumbColor: colors.primaryGlow,
                        overlayColor: colors.primary.withValues(alpha: 0.2),
                        trackHeight: 3,
                        thumbShape: const RoundSliderThumbShape(
                          enabledThumbRadius: 6,
                        ),
                      ),
                      child: Slider(
                        value: _dragPage ?? (widget.pageIndex + 1).toDouble(),
                        min: 1,
                        max: widget.pageCount.toDouble(),
                        divisions: widget.pageCount > 1
                            ? widget.pageCount - 1
                            : 1,
                        onChanged: widget.loading
                            ? null
                            : (value) => setState(() => _dragPage = value),
                        onChangeEnd: widget.loading
                            ? null
                            : (value) {
                                setState(() => _dragPage = null);
                                widget.onJumpToPage(value.round() - 1);
                              },
                      ),
                    ),
                  ],
                ),
              ),
              IconButton(
                tooltip: 'Next page',
                onPressed:
                    widget.loading || widget.pageIndex == widget.pageCount - 1
                    ? null
                    : widget.onNext,
                icon: const Icon(Icons.chevron_right_rounded),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class MangaReaderFailure extends StatelessWidget {
  const MangaReaderFailure({
    super.key,
    required this.message,
    required this.onRetry,
  });

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              message,
              style: TextStyle(color: context.hikariColors.textPrimary),
            ),
            const SizedBox(height: HikariSpacing.sm),
            TextButton(onPressed: onRetry, child: const Text('Try again')),
          ],
        ),
      ),
    );
  }
}
