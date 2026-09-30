import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

enum HikariIconButtonVariant { standard, filled, glass, primary }

/// A tactile icon button with press-scale feedback and accessible 48x48dp target.
class HikariIconButton extends StatefulWidget {
  const HikariIconButton({
    super.key,
    required this.icon,
    required this.onPressed,
    this.tooltip,
    this.variant = HikariIconButtonVariant.standard,
    this.size = 48.0,
    this.iconSize = 22.0,
    this.color,
    this.hasBadge = false,
  });

  final Widget icon;
  final VoidCallback? onPressed;
  final String? tooltip;
  final HikariIconButtonVariant variant;
  final double size;
  final double iconSize;
  final Color? color;
  final bool hasBadge;

  @override
  State<HikariIconButton> createState() => _HikariIconButtonState();
}

class _HikariIconButtonState extends State<HikariIconButton>
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
      end: 0.95,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeOutCubic));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onTapDown(TapDownDetails _) {
    if (widget.onPressed != null) _controller.forward();
  }

  void _onTapUp(TapUpDetails _) {
    if (widget.onPressed != null) _controller.reverse();
  }

  void _onTapCancel() {
    if (widget.onPressed != null) _controller.reverse();
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
    final isEnabled = widget.onPressed != null;

    Color bg;
    Border? border;
    Color iconColor =
        widget.color ?? (isEnabled ? colors.textPrimary : colors.textMuted);

    switch (widget.variant) {
      case HikariIconButtonVariant.standard:
        bg = Colors.transparent;
        break;
      case HikariIconButtonVariant.filled:
        bg = colors.surfaceContainer;
        border = Border.all(color: colors.border);
        break;
      case HikariIconButtonVariant.glass:
        bg = colors.surfaceElevated.withValues(alpha: 0.7);
        border = Border.all(color: colors.borderSubtle);
        break;
      case HikariIconButtonVariant.primary:
        bg = colors.primary;
        iconColor = colors.onPrimary;
        break;
    }

    Widget content = Container(
      width: widget.size,
      height: widget.size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: bg,
        borderRadius: HikariRadius.borderMd,
        border: border,
      ),
      child: Stack(
        alignment: Alignment.center,
        children: [
          IconTheme(
            data: IconThemeData(size: widget.iconSize, color: iconColor),
            child: widget.icon,
          ),
          if (widget.hasBadge)
            Positioned(
              top: 6,
              right: 6,
              child: Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  color: colors.secondary,
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: colors.secondary.withValues(alpha: 0.5),
                      blurRadius: 4,
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );

    if (widget.tooltip != null) {
      content = Tooltip(message: widget.tooltip!, child: content);
    }

    return ScaleTransition(
      scale: _scaleAnimation,
      child: GestureDetector(
        onTapDown: _onTapDown,
        onTapUp: _onTapUp,
        onTapCancel: _onTapCancel,
        onTap: widget.onPressed,
        behavior: HitTestBehavior.opaque,
        child: content,
      ),
    );
  }
}
