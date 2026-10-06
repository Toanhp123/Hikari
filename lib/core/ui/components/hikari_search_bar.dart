import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/design_system/design_system.dart';

/// Search behavior wrapper using the canonical Material 3 design-system bar.
///
/// Existing debounce behavior stays in this wrapper during the scoped
/// migration; it is not a design-system token. The visual subtree is
/// intentionally scoped to [HikariDesignTheme] while the app root still uses
/// the legacy theme during incremental migration.
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
  late final TextEditingController _controller;
  Timer? _debounceTimer;
  ThemeData? _scopedTheme;
  Color? _accentSeed;

  @override
  void initState() {
    super.initState();
    _controller =
        widget.controller ?? TextEditingController(text: widget.initialQuery);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final accentSeed = Theme.of(context).colorScheme.primary;
    if (_scopedTheme == null || _accentSeed != accentSeed) {
      _accentSeed = accentSeed;
      // Search roles use the same raised surface in dark and OLED modes. During
      // migration only the semantic accent needs to bridge from the live root.
      _scopedTheme = HikariDesignTheme.dark(accentSeed: accentSeed);
    }
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

  void _onClear() {
    _controller.clear();
    _debounceTimer?.cancel();
    widget.onChanged('');
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Theme(
      data: _scopedTheme!,
      child: SearchBar(
        controller: _controller,
        autoFocus: widget.autofocus,
        hintText: widget.hintText,
        textInputAction: widget.onSubmitted == null
            ? null
            : TextInputAction.search,
        onChanged: _onTextChange,
        onSubmitted: widget.onSubmitted,
        leading: const Icon(Icons.search_rounded),
        trailing: _controller.text.isEmpty
            ? null
            : [
                IconButton(
                  tooltip: 'Clear search',
                  onPressed: _onClear,
                  icon: const Icon(Icons.close_rounded),
                ),
              ],
      ),
    );
  }
}
