import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/media/hikari_card_metrics.dart';

/// One horizontal viewport; vertical scrolling remains with its parent.
/// [itemExtent] is the card width, not the full-card aspect ratio.
class HikariHorizontalShelf extends StatefulWidget {
  const HikariHorizontalShelf({
    super.key,
    required this.itemCount,
    required this.itemBuilder,
    this.itemKeyBuilder,
    this.header,
    this.itemExtent = 156,
    this.itemHeightBuilder,
    this.controller,
  }) : assert(itemCount >= 0),
       assert(itemExtent > 0 && itemExtent < double.infinity);

  final int itemCount;
  final IndexedWidgetBuilder itemBuilder;
  final Key Function(int)? itemKeyBuilder;
  final Widget? header;
  final double itemExtent;
  final double Function(BuildContext, double)? itemHeightBuilder;
  final ScrollController? controller;

  @override
  State<HikariHorizontalShelf> createState() => _HikariHorizontalShelfState();
}

class _HikariHorizontalShelfState extends State<HikariHorizontalShelf> {
  ScrollController? _ownedController;
  ScrollController get _controller =>
      widget.controller ?? (_ownedController ??= ScrollController());

  @override
  void dispose() {
    _ownedController?.dispose();
    super.dispose();
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (event is KeyUpEvent || !_controller.hasClients) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    if (key != LogicalKeyboardKey.arrowLeft &&
        key != LogicalKeyboardKey.arrowRight) {
      return KeyEventResult.ignored;
    }
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final forward = (key == LogicalKeyboardKey.arrowRight) != rtl;
    final position = _controller.position;
    final target = (position.pixels + (forward ? 1 : -1) * widget.itemExtent)
        .clamp(position.minScrollExtent, position.maxScrollExtent);
    if (MediaQuery.disableAnimationsOf(context)) {
      _controller.jumpTo(target);
    } else {
      _controller.animateTo(
        target,
        duration: const Duration(milliseconds: 150),
        curve: Curves.easeOut,
      );
    }
    return KeyEventResult.handled;
  }

  @override
  Widget build(BuildContext context) {
    final keys = widget.itemKeyBuilder;
    final indices = keys == null
        ? null
        : <Key, int>{for (var i = 0; i < widget.itemCount; i++) keys(i): i};
    assert(indices == null || indices.length == widget.itemCount);
    final height =
        widget.itemHeightBuilder?.call(context, widget.itemExtent) ??
        hikariCardExtent(context, width: widget.itemExtent, hasSubtitle: true);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (widget.header != null) ...[
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
            child: widget.header!,
          ),
          const SizedBox(height: HikariSpacing.sm),
        ],
        Focus(
          onKeyEvent: _onKey,
          child: SizedBox(
            height: height,
            child: ListView.custom(
              controller: _controller,
              primary: false,
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: HikariSpacing.lg),
              itemExtent: widget.itemExtent + HikariSpacing.md,
              childrenDelegate: SliverChildBuilderDelegate(
                (context, index) => KeyedSubtree(
                  key: keys?.call(index),
                  child: Padding(
                    padding: const EdgeInsetsDirectional.only(
                      end: HikariSpacing.md,
                    ),
                    child: widget.itemBuilder(context, index),
                  ),
                ),
                childCount: widget.itemCount,
                findChildIndexCallback: indices == null
                    ? null
                    : (key) => indices[key],
              ),
            ),
          ),
        ),
      ],
    );
  }
}
