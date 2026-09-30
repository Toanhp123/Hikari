import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

enum HikariButtonVariant { primary, secondary, ghost, danger }

enum HikariButtonSize {
  small(height: 36, horizontalPadding: 12, fontSize: 12),
  medium(height: 44, horizontalPadding: 16, fontSize: 14),
  large(height: 52, horizontalPadding: 24, fontSize: 16);

  const HikariButtonSize({
    required this.height,
    required this.horizontalPadding,
    required this.fontSize,
  });

  final double height;
  final double horizontalPadding;
  final double fontSize;
}

/// A tactile button component with press-scale feedback for Cinematic Neo-Material design.
class HikariButton extends StatefulWidget {
  const HikariButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.variant = HikariButtonVariant.primary,
    this.size = HikariButtonSize.medium,
    this.isLoading = false,
    this.isFullWidth = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final Widget? icon;
  final HikariButtonVariant variant;
  final HikariButtonSize size;
  final bool isLoading;
  final bool isFullWidth;

  @override
  State<HikariButton> createState() => _HikariButtonState();
}

class _HikariButtonState extends State<HikariButton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _scaleAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: HikariMotion.fast,
      reverseDuration: HikariMotion.fast,
    );
    _scaleAnimation = Tween<double>(
      begin: 1.0,
      end: 0.97,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeOutCubic));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onTapDown(TapDownDetails _) {
    if (widget.onPressed != null && !widget.isLoading) {
      _controller.forward();
    }
  }

  void _onTapUp(TapUpDetails _) {
    if (widget.onPressed != null && !widget.isLoading) {
      _controller.reverse();
    }
  }

  void _onTapCancel() {
    if (widget.onPressed != null && !widget.isLoading) {
      _controller.reverse();
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final isEnabled = widget.onPressed != null && !widget.isLoading;

    Color bg;
    Color fg;
    Border? border;
    List<BoxShadow>? shadows;

    switch (widget.variant) {
      case HikariButtonVariant.primary:
        bg = isEnabled ? colors.primary : colors.surfaceElevated;
        fg = isEnabled ? colors.onPrimary : colors.textMuted;
        if (isEnabled) {
          shadows = [
            BoxShadow(
              color: colors.primary.withValues(alpha: 0.35),
              blurRadius: 12,
              offset: const Offset(0, 4),
            ),
          ];
        }
        break;
      case HikariButtonVariant.secondary:
        bg = isEnabled ? colors.surfaceContainer : colors.surface;
        fg = isEnabled ? colors.textPrimary : colors.textMuted;
        border = Border.all(color: colors.border);
        break;
      case HikariButtonVariant.ghost:
        bg = Colors.transparent;
        fg = isEnabled ? colors.textSecondary : colors.textMuted;
        break;
      case HikariButtonVariant.danger:
        bg = isEnabled ? colors.error : colors.surfaceElevated;
        fg = Colors.white;
        break;
    }

    final content = Row(
      mainAxisSize: widget.isFullWidth ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (widget.isLoading) ...[
          SizedBox(
            width: widget.size.fontSize + 2,
            height: widget.size.fontSize + 2,
            child: CircularProgressIndicator(
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation<Color>(fg),
            ),
          ),
          const SizedBox(width: HikariSpacing.xs),
        ] else if (widget.icon != null) ...[
          widget.icon!,
          const SizedBox(width: HikariSpacing.xs),
        ],
        Flexible(
          child: Text(
            widget.label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: widget.size.fontSize,
              fontWeight: FontWeight.w600,
              color: fg,
            ),
          ),
        ),
      ],
    );

    return ScaleTransition(
      scale: _scaleAnimation,
      child: GestureDetector(
        onTapDown: _onTapDown,
        onTapUp: _onTapUp,
        onTapCancel: _onTapCancel,
        onTap: isEnabled ? widget.onPressed : null,
        behavior: HitTestBehavior.opaque,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            minHeight: 48, // ensure accessible touch target
            minWidth: widget.isFullWidth ? double.infinity : 48,
          ),
          child: Container(
            height: widget.size.height,
            padding: EdgeInsets.symmetric(
              horizontal: widget.size.horizontalPadding,
            ),
            decoration: BoxDecoration(
              color: bg,
              borderRadius: HikariRadius.borderMd,
              border: border,
              boxShadow: shadows,
            ),
            alignment: Alignment.center,
            child: content,
          ),
        ),
      ),
    );
  }
}
