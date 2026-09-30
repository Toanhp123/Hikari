import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

/// A debounced search bar with glassmorphic styling and clear action.
class HikariSearchBar extends StatefulWidget {
  const HikariSearchBar({
    super.key,
    this.initialQuery = '',
    required this.onChanged,
    this.onSubmitted,
    this.hintText = 'Search anime, manga, novels...',
    this.debounceDuration = const Duration(milliseconds: 300),
    this.autofocus = false,
  });

  final String initialQuery;
  final ValueChanged<String> onChanged;
  final ValueChanged<String>? onSubmitted;
  final String hintText;
  final Duration debounceDuration;
  final bool autofocus;

  @override
  State<HikariSearchBar> createState() => _HikariSearchBarState();
}

class _HikariSearchBarState extends State<HikariSearchBar> {
  late final TextEditingController _controller;
  Timer? _debounceTimer;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery);
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    _controller.dispose();
    super.dispose();
  }

  void _onTextChange(String value) {
    _debounceTimer?.cancel();
    _debounceTimer = Timer(widget.debounceDuration, () {
      if (mounted) widget.onChanged(value);
    });
    setState(() {});
  }

  void _onClear() {
    _controller.clear();
    _debounceTimer?.cancel();
    widget.onChanged('');
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;

    return Container(
      height: 48,
      decoration: BoxDecoration(
        color: colors.surfaceContainer,
        borderRadius: HikariRadius.borderMd,
        border: Border.all(color: colors.border),
      ),
      padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.md),
      alignment: Alignment.center,
      child: Row(
        children: [
          Icon(Icons.search_rounded, size: 20, color: colors.textSecondary),
          const SizedBox(width: HikariSpacing.sm),
          Expanded(
            child: TextField(
              controller: _controller,
              autofocus: widget.autofocus,
              onChanged: _onTextChange,
              onSubmitted: widget.onSubmitted,
              style: TextStyle(fontSize: 14, color: colors.textPrimary),
              decoration: InputDecoration(
                hintText: widget.hintText,
                hintStyle: TextStyle(fontSize: 14, color: colors.textMuted),
                border: InputBorder.none,
                isDense: true,
                contentPadding: EdgeInsets.zero,
              ),
            ),
          ),
          if (_controller.text.isNotEmpty)
            GestureDetector(
              onTap: _onClear,
              behavior: HitTestBehavior.opaque,
              child: Padding(
                padding: const EdgeInsets.all(4.0),
                child: Icon(
                  Icons.close_rounded,
                  size: 18,
                  color: colors.textSecondary,
                ),
              ),
            ),
        ],
      ),
    );
  }
}
