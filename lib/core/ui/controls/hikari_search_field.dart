import 'package:flutter/material.dart';

/// Search presentation only. Callers own debounce, requests and stale results.
/// Caller controllers/focus nodes are borrowed; absent nodes are field-owned.
class HikariSearchField extends StatefulWidget {
  const HikariSearchField({
    super.key,
    this.controller,
    this.focusNode,
    this.initialQuery = '',
    this.onChanged,
    this.onSubmitted,
    this.onCleared,
    this.hintText = 'Search anime, manga, novels...',
    this.autofocus = false,
    this.enabled = true,
  });

  final TextEditingController? controller;
  final FocusNode? focusNode;
  final String initialQuery;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;

  /// Clear-button event, after [onChanged] receives the empty text.
  final VoidCallback? onCleared;
  final String hintText;
  final bool autofocus;
  final bool enabled;

  @override
  State<HikariSearchField> createState() => _HikariSearchFieldState();
}

class _HikariSearchFieldState extends State<HikariSearchField> {
  late TextEditingController _controller;
  late FocusNode _focus;
  late String _text;

  @override
  void initState() {
    super.initState();
    _controller =
        widget.controller ?? TextEditingController(text: widget.initialQuery);
    _focus = widget.focusNode ?? FocusNode();
    _text = _controller.text;
    _controller.addListener(_changed);
  }

  @override
  void didUpdateWidget(HikariSearchField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      _controller.removeListener(_changed);
      if (oldWidget.controller == null) _controller.dispose();
      _controller =
          widget.controller ?? TextEditingController(text: widget.initialQuery);
      _text = _controller.text;
      _controller.addListener(_changed);
    }
    if (oldWidget.focusNode != widget.focusNode) {
      if (oldWidget.focusNode == null) _focus.dispose();
      _focus = widget.focusNode ?? FocusNode();
    }
  }

  void _changed() {
    if (_text == _controller.text) return;
    setState(() => _text = _controller.text);
  }

  @override
  void dispose() {
    _controller.removeListener(_changed);
    if (widget.controller == null) _controller.dispose();
    if (widget.focusNode == null) _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => InputDecorationTheme(
    data: InputDecorationTheme.of(context)
        .copyWith(constraints: const BoxConstraints()),
    child: SearchBar(
      controller: _controller,
      focusNode: _focus,
      autoFocus: widget.autofocus,
      enabled: widget.enabled,
      hintText: widget.hintText,
      textInputAction: widget.onSubmitted == null
          ? null
          : TextInputAction.search,
      onChanged: widget.onChanged,
      onSubmitted: widget.onSubmitted,
      leading: const Icon(Icons.search_rounded),
      trailing: _text.isEmpty
          ? null
          : [
              if (widget.onSubmitted != null)
                IconButton(
                  tooltip: 'Submit search',
                  onPressed: widget.enabled
                      ? () => widget.onSubmitted!(_controller.text)
                      : null,
                  icon: const Icon(Icons.arrow_forward_rounded),
                ),
              IconButton(
                tooltip: 'Clear search',
                onPressed: widget.enabled
                    ? () {
                        _controller.clear();
                        widget.onChanged?.call('');
                        widget.onCleared?.call();
                      }
                    : null,
                icon: const Icon(Icons.close_rounded),
              ),
            ],
    ),
  );
}
