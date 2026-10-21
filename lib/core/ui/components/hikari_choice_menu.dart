import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

/// A presentation-only choice. Null is a valid option (for example, "All").
@immutable
final class HikariChoiceOption<T> {
  const HikariChoiceOption({
    required this.value,
    required this.label,
    this.shortLabel,
  });

  final T? value;
  final String label;

  /// Optional shorter text in the trigger; menu items show [label].
  final String? shortLabel;
}

/// Controlled single choice. Callers own values, options and validation.
/// Compose with FormField using field.value, field.didChange and field.errorText.
class HikariChoiceMenu<T> extends StatefulWidget {
  const HikariChoiceMenu({
    super.key,
    required this.label,
    required this.value,
    required this.options,
    required this.onChanged,
    this.icon,
    this.expanded = false,
    this.enabled = true,
    this.errorText,
  });

  final String label;
  final T? value;
  final List<HikariChoiceOption<T>> options;
  final ValueChanged<T?>? onChanged;
  final IconData? icon;
  final bool expanded;
  final bool enabled;
  final String? errorText;

  @override
  State<HikariChoiceMenu<T>> createState() => _HikariChoiceMenuState<T>();
}

class _HikariChoiceMenuState<T> extends State<HikariChoiceMenu<T>> {
  final _triggerFocus = FocusNode();

  @override
  void dispose() {
    _triggerFocus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final selected = widget.options
        .where((option) => option.value == widget.value)
        .firstOrNull;
    final selectedLabel = selected?.shortLabel ?? selected?.label;
    final buttonLabel = selectedLabel == null
        ? 'Select ${widget.label}'
        : '${widget.label}: $selectedLabel';
    final canSelect =
        widget.enabled && widget.onChanged != null && widget.options.isNotEmpty;
    final colors = Theme.of(context).colorScheme;
    final menu = MenuAnchor(
      childFocusNode: _triggerFocus,
      crossAxisUnconstrained: false,
      style: const MenuStyle(
        maximumSize: WidgetStatePropertyAll(Size(360, 320)),
      ),
      menuChildren: [
        for (final option in widget.options)
          Semantics(
            selected: option.value == widget.value,
            child: MenuItemButton(
              onPressed: canSelect
                  ? () => widget.onChanged!(option.value)
                  : null,
              trailingIcon: option.value == widget.value
                  ? const Icon(Icons.check_rounded)
                  : null,
              child: Text(option.label),
            ),
          ),
      ],
      builder: (context, controller, _) => Tooltip(
        message: 'Choose ${widget.label}',
        child: OutlinedButton(
          focusNode: _triggerFocus,
          onPressed: canSelect
              ? () => controller.isOpen ? controller.close() : controller.open()
              : null,
          style: OutlinedButton.styleFrom(
            minimumSize: const Size(0, HikariSize.touchTarget),
            padding: const EdgeInsets.symmetric(
              horizontal: HikariSpacing.md,
              vertical: HikariSpacing.sm,
            ),
            side: widget.errorText == null
                ? null
                : BorderSide(color: colors.error),
          ),
          child: Row(
            mainAxisSize: widget.expanded ? MainAxisSize.max : MainAxisSize.min,
            children: [
              if (widget.icon != null) ...[
                Icon(widget.icon, size: HikariSize.icon),
                const SizedBox(width: HikariSpacing.sm),
              ],
              Flexible(
                fit: widget.expanded ? FlexFit.tight : FlexFit.loose,
                child: Text(
                  buttonLabel,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: HikariSpacing.sm),
              const Icon(Icons.arrow_drop_down_rounded),
            ],
          ),
        ),
      ),
    );
    final trigger = widget.expanded
        ? SizedBox(width: double.infinity, child: menu)
        : menu;
    if (widget.errorText == null) return trigger;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        trigger,
        const SizedBox(height: HikariSpacing.xs),
        Semantics(
          liveRegion: true,
          child: Text(
            widget.errorText!,
            style: Theme.of(context).textTheme.bodySmall
                ?.copyWith(color: colors.error),
          ),
        ),
      ],
    );
  }
}
