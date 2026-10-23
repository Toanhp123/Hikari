import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_controller.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';

/// Internal lifecycle shared by native hosts, not a third surface family.
class HikariPresentationSession<T> extends StatefulWidget {
  const HikariPresentationSession({
    super.key,
    required this.controller,
    required this.contentBuilder,
  });
  final HikariPresentationController<T> controller;
  final HikariContentBuilder<T> contentBuilder;

  @override
  State<HikariPresentationSession<T>> createState() =>
      _HikariPresentationSessionState<T>();
}

class _HikariPresentationSessionState<T>
    extends State<HikariPresentationSession<T>> {
  bool _closing = false;
  late final Widget _content;

  @override
  void initState() {
    super.initState();
    _content = Builder(
      builder: (context) => widget.contentBuilder(context, widget.controller),
    );
    widget.controller.addListener(_changed);
  }

  void _changed() {
    scheduleMicrotask(() {
      if (!mounted) return;
      if (widget.controller.isSettled && !_closing) {
        _closing = true;
        final route = ModalRoute.of(context)!;
        final navigator = route.navigator;
        if (navigator != null && route.isActive) {
          if (route.isCurrent) {
            navigator.pop();
          } else {
            navigator.removeRoute(route);
          }
        }
      }
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    widget.controller.removeListener(_changed);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => PopScope<void>(
    canPop: widget.controller.isSettled || widget.controller.canDismiss,
    onPopInvokedWithResult: (didPop, _) {
      if (!didPop) widget.controller.cancel(HikariDismissReason.back);
    },
    child: CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.escape): () =>
            widget.controller.cancel(HikariDismissReason.back),
      },
      child: FocusTraversalGroup(child: _content),
    ),
  );
}
