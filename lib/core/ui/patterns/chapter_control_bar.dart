import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

/// Control bar for chapter lists featuring count badge, sort toggle, and expandable search.
class ChapterControlBar extends StatefulWidget {
  const ChapterControlBar({
    super.key,
    required this.totalChapters,
    this.filteredChapters,
    required this.reverseSourceOrder,
    required this.onToggleSourceOrder,
    required this.isSearchExpanded,
    required this.onToggleSearchExpanded,
    required this.searchQuery,
    required this.onSearchChanged,
    required this.onClearSearch,
    this.padding = const EdgeInsets.symmetric(
      horizontal: HikariSpacing.md,
      vertical: HikariSpacing.xs,
    ),
  });

  final int totalChapters;
  final int? filteredChapters;
  final bool reverseSourceOrder;
  final VoidCallback onToggleSourceOrder;
  final bool isSearchExpanded;
  final VoidCallback onToggleSearchExpanded;
  final String searchQuery;
  final ValueChanged<String> onSearchChanged;
  final VoidCallback onClearSearch;
  final EdgeInsetsGeometry padding;

  @override
  State<ChapterControlBar> createState() => _ChapterControlBarState();
}

class _ChapterControlBarState extends State<ChapterControlBar> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.searchQuery);
    _controller.addListener(_handleTextChange);
  }

  @override
  void didUpdateWidget(ChapterControlBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.searchQuery != widget.searchQuery &&
        _controller.text != widget.searchQuery) {
      _controller.value = TextEditingValue(
        text: widget.searchQuery,
        selection: TextSelection.collapsed(offset: widget.searchQuery.length),
      );
    }
  }

  @override
  void dispose() {
    _controller.removeListener(_handleTextChange);
    _controller.dispose();
    super.dispose();
  }

  void _handleTextChange() {
    setState(() {});
  }

  void _handleClear() {
    _controller.clear();
    widget.onClearSearch();
  }

  String get _countLabel {
    final suffix = widget.totalChapters == 1 ? 'chapter' : 'chapters';
    if (widget.filteredChapters != null) {
      return '${widget.filteredChapters} of ${widget.totalChapters} $suffix';
    }
    return '${widget.totalChapters} $suffix';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Padding(
      padding: widget.padding,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Wrap(
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: HikariSpacing.sm,
                  vertical: HikariSpacing.xs,
                ),
                decoration: BoxDecoration(
                  color: colorScheme.surfaceContainerHigh,
                  borderRadius: HikariRadius.borderSm,
                ),
                child: Text(
                  _countLabel,
                  style: theme.textTheme.labelMedium?.copyWith(
                    color: colorScheme.onSurfaceVariant,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              IconButton(
                tooltip: widget.reverseSourceOrder
                    ? 'Restore source order'
                    : 'Reverse chapter order',
                onPressed: widget.onToggleSourceOrder,
                icon: Icon(
                  widget.reverseSourceOrder
                      ? Icons.arrow_upward_rounded
                      : Icons.arrow_downward_rounded,
                  color: widget.reverseSourceOrder
                      ? colorScheme.primary
                      : colorScheme.onSurfaceVariant,
                ),
              ),
              IconButton(
                tooltip: widget.isSearchExpanded
                    ? 'Close search'
                    : 'Search chapters',
                onPressed: widget.onToggleSearchExpanded,
                icon: Icon(
                  widget.isSearchExpanded
                      ? Icons.close_rounded
                      : Icons.search_rounded,
                  color: widget.isSearchExpanded
                      ? colorScheme.primary
                      : colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
          if (widget.isSearchExpanded) ...[
            const SizedBox(height: HikariSpacing.xs),
            TextField(
              controller: _controller,
              autofocus: true,
              textInputAction: TextInputAction.search,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: colorScheme.onSurface,
              ),
              decoration: InputDecoration(
                hintText: 'Search chapters...',
                hintStyle: theme.textTheme.bodyMedium?.copyWith(
                  color: colorScheme.onSurfaceVariant.withValues(alpha: 0.7),
                ),
                prefixIcon: Icon(
                  Icons.search_rounded,
                  size: 20,
                  color: colorScheme.onSurfaceVariant,
                ),
                prefixIconConstraints: const BoxConstraints(
                  minWidth: 40,
                  minHeight: 48,
                ),
                suffixIcon:
                    (_controller.text.isNotEmpty ||
                        widget.searchQuery.isNotEmpty)
                    ? IconButton(
                        tooltip: 'Clear search',
                        icon: const Icon(Icons.clear_rounded, size: 18),
                        onPressed: _handleClear,
                      )
                    : null,
                suffixIconConstraints: const BoxConstraints(
                  minWidth: 40,
                  minHeight: 48,
                ),
                isDense: true,
                filled: true,
                fillColor: colorScheme.surfaceContainerHigh,
                border: const OutlineInputBorder(
                  borderRadius: HikariRadius.borderMd,
                  borderSide: BorderSide.none,
                ),
                enabledBorder: const OutlineInputBorder(
                  borderRadius: HikariRadius.borderMd,
                  borderSide: BorderSide.none,
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: HikariRadius.borderMd,
                  borderSide: BorderSide(
                    color: colorScheme.primary,
                    width: HikariRadius.borderWidth,
                  ),
                ),
                contentPadding: const EdgeInsets.symmetric(
                  horizontal: HikariSpacing.md,
                  vertical: HikariSpacing.sm,
                ),
              ),
              onChanged: widget.onSearchChanged,
            ),
          ],
        ],
      ),
    );
  }
}
