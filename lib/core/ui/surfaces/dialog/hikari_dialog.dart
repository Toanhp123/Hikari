import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_controller.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_session.dart';

/// Opens exactly one dialog on the explicitly selected Navigator.
///
/// Uninstrumented framework pops report routeDismissed. This helper does not
/// register restoration state; callers must restore their own feature workflow.
Future<HikariPresentationResult<T>> showHikariDialog<T>({
  required BuildContext context,
  required bool useRootNavigator,
  required HikariContentBuilder<T> contentBuilder,
  bool barrierDismissible = true,
}) {
  final navigator = Navigator.of(context, rootNavigator: useRootNavigator);
  final controller = HikariPresentationController<T>();
  final route = _HikariDialogRoute<T>(
    context: context,
    presentationController: controller,
    contentBuilder: contentBuilder,
    barrierDismissible: barrierDismissible,
    themes: InheritedTheme.capture(from: context, to: navigator.context),
  );
  navigator.push<void>(route).then((_) => controller.didDismiss());
  return route.completed.then((_) => controller.result);
}

class _HikariDialogRoute<T> extends DialogRoute<void> {
  _HikariDialogRoute({
    required super.context,
    required this.presentationController,
    required HikariContentBuilder<T> contentBuilder,
    required super.barrierDismissible,
    required super.themes,
  }) : super(
         requestFocus: true,
         traversalEdgeBehavior: TraversalEdgeBehavior.closedLoop,
         builder: (context) => HikariPresentationSession<T>(
           controller: presentationController,
           contentBuilder: contentBuilder,
         ),
       ) {
    presentationController.addListener(_policyChanged);
  }

  final HikariPresentationController<T> presentationController;

  @override
  bool get barrierDismissible =>
      super.barrierDismissible && presentationController.canDismiss;

  void _policyChanged() {
    scheduleMicrotask(() {
      if (isActive) changedInternalState();
    });
  }

  @override
  Widget buildModalBarrier() => ModalBarrier(
    color: barrierColor,
    dismissible: barrierDismissible,
    onDismiss: () => presentationController.cancel(HikariDismissReason.barrier),
    semanticsLabel: barrierLabel,
    barrierSemanticsDismissible: barrierDismissible,
  );

  @override
  bool didPop(void result) {
    final popped = super.didPop(result);
    if (popped) presentationController.didDismiss();
    return popped;
  }

  @override
  void dispose() {
    presentationController.removeListener(_policyChanged);
    presentationController.dispose();
    super.dispose();
  }
}

/// Bounded dialog chrome. Body owns scrolling only when [scrollable] is false.
/// Default scroll includes header/actions so 3x text in short windows remains
/// reachable rather than reserving an oversized fixed footer.
class HikariDialog extends StatelessWidget {
  const HikariDialog({
    super.key,
    required this.title,
    required this.body,
    this.actions = const [],
    this.onClose,
    this.scrollable = true,
  });

  final Widget title;
  final Widget body;
  final List<Widget> actions;
  final VoidCallback? onClose;
  final bool scrollable;

  @override
  Widget build(BuildContext context) {
    final content = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Semantics(
                namesRoute: true,
                header: true,
                child: DefaultTextStyle(
                  style: Theme.of(context).textTheme.headlineSmall!,
                  child: title,
                ),
              ),
            ),
            if (onClose != null)
              IconButton(
                tooltip: 'Close',
                onPressed: onClose,
                icon: const Icon(Icons.close),
              ),
          ],
        ),
        const SizedBox(height: HikariSpacing.lg),
        if (scrollable) body else Flexible(child: body),
        if (actions.isNotEmpty) ...[
          const SizedBox(height: HikariSpacing.xl),
          Wrap(
            alignment: WrapAlignment.end,
            spacing: HikariSpacing.sm,
            runSpacing: HikariSpacing.sm,
            children: actions,
          ),
        ],
      ],
    );
    return Dialog(
      constraints: const BoxConstraints(
        maxWidth: HikariBreakpoints.dialogMaxWidth,
      ),
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.all(HikariSpacing.xl),
        child: scrollable ? SingleChildScrollView(child: content) : content,
      ),
    );
  }
}
