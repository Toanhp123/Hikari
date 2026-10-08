import 'dart:async';

import 'package:flutter/material.dart';

/// Search behavior wrapper using the canonical Material 3 SearchBar.
/// Debounce remains behavior-owned; styling is inherited from the root theme.
class HikariSearchBar extends StatefulWidget {
  const HikariSearchBar({
    super.key,
    this.controller,
    this.initialQuery = '',
    required this.onChanged,
    this.onSubmitted,
    this.hintText = 'Search anime, manga, novels...',
    this.debounceDuration = const Duration(milliseconds: 300),
    this.autofocus = false,
  });

  final TextEditingController? controller;
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
  late TextEditingController _controller;
  Timer? _debounceTimer;

  @override
  void initState() {
    super.initState();
    _controller =
        widget.controller ?? TextEditingController(text: widget.initialQuery);
  }

  @override
  void didUpdateWidget(HikariSearchBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller == widget.controller) return;
    _debounceTimer?.cancel();
    if (oldWidget.controller == null) _controller.dispose();
    _controller =
        widget.controller ?? TextEditingController(text: widget.initialQuery);
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    if (widget.controller == null) {
      _controller.dispose();
    }
    super.dispose();
  }

  void _onTextChange(String value) {
    _debounceTimer?.cancel();
    _debounceTimer = Timer(widget.debounceDuration, () {
      if (mounted) widget.onChanged(value);
    });
    setState(() {});
  }

  void _submit(String value) {
    _debounceTimer?.cancel();
    widget.onChanged(value);
    widget.onSubmitted?.call(value);
  }

  void _onClear() {
    _controller.clear();
    _debounceTimer?.cancel();
    widget.onChanged('');
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return SearchBar(
      controller: _controller,
      autoFocus: widget.autofocus,
      hintText: widget.hintText,
      textInputAction: widget.onSubmitted == null
          ? null
          : TextInputAction.search,
      onChanged: _onTextChange,
      onSubmitted: widget.onSubmitted == null ? null : _submit,
      leading: const Icon(Icons.search_rounded),
      trailing: _controller.text.isEmpty
          ? null
          : [
              if (widget.onSubmitted != null)
                IconButton(
                  tooltip: 'Submit search',
                  onPressed: () => _submit(_controller.text),
                  icon: const Icon(Icons.arrow_forward_rounded),
                ),
              IconButton(
                tooltip: 'Clear search',
                onPressed: _onClear,
                icon: const Icon(Icons.close_rounded),
              ),
            ],
    );
  }
}
