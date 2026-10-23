import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_session.dart';
import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_controller.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';

/// Native sheet; unobserved drag/Back/barrier exits remain routeDismissed.
/// Caller chooses Navigator explicitly; this is not a restoration registration.
Future<HikariPresentationResult<T>> showHikariBottomSheet<T>({
  required BuildContext context,
  required bool useRootNavigator,
  required HikariContentBuilder<T> contentBuilder,
  bool isDismissible = true,
  bool enableDrag = true,
}) {
  final navigator = Navigator.of(context, rootNavigator: useRootNavigator);
  final controller = HikariPresentationController<T>();
  final route = _HikariSheetRoute<T>(
    presentationController: controller,
    builder: (_) => HikariPresentationSession<T>(
      controller: controller,
      contentBuilder: contentBuilder,
    ),
    capturedThemes: InheritedTheme.capture(
      from: context,
      to: navigator.context,
    ),
    isDismissible: isDismissible,
    enableDrag: enableDrag,
    barrierLabel: MaterialLocalizations.of(context).modalBarrierDismissLabel,
  );
  navigator.push<void>(route).then((_) => controller.didDismiss());
  return route.completed.then((_) => controller.result);
}

class _HikariSheetRoute<T> extends ModalBottomSheetRoute<void> {
  _HikariSheetRoute({
    required this.presentationController,
    required super.builder,
    required super.capturedThemes,
    required super.isDismissible,
    required super.enableDrag,
    required super.barrierLabel,
  }) : super(
         isScrollControlled: true,
         useSafeArea: true,
         requestFocus: true,
         constraints: const BoxConstraints(
           maxWidth: HikariBreakpoints.sheetMaxWidth,
         ),
       ) {
    presentationController.addListener(_policyChanged);
  }

  final HikariPresentationController<T> presentationController;

  @override
  bool get enableDrag => super.enableDrag && presentationController.canDismiss;

  // Native handle has its own drag detector when enableDrag=false. Hide both
  // while protected so the handle cannot bypass feature pre-pop policy.
  @override
  bool get showDragHandle => enableDrag;

  @override
  bool get barrierDismissible =>
      super.barrierDismissible && presentationController.canDismiss;

  void _policyChanged() {
    // Route policy can change while feature content builds. Rebuild native
    // gesture handlers before the next frame, never mutate overlay mid-build.
    scheduleMicrotask(() {
      if (isActive) changedExternalState();
    });
  }

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

/// One IME/safe-bottom owner. Default scrolling includes footer for short/3x
/// windows; list content may set scrollable=false to own its single viewport.
class HikariBottomSheet extends StatelessWidget {
  const HikariBottomSheet({
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
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
    child: SafeArea(
      top: false,
      child: LayoutBuilder(
        builder: (context, constraints) {
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
                        style: Theme.of(context).textTheme.titleLarge!,
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
                const SizedBox(height: HikariSpacing.lg),
                Wrap(
                  alignment: WrapAlignment.end,
                  spacing: HikariSpacing.sm,
                  runSpacing: HikariSpacing.sm,
                  children: actions,
                ),
              ],
            ],
          );
          return ConstrainedBox(
            constraints: BoxConstraints(maxHeight: constraints.maxHeight * .94),
            child: Padding(
              padding: const EdgeInsets.all(HikariSpacing.lg),
              child: scrollable
                  ? SingleChildScrollView(child: content)
                  : content,
            ),
          );
        },
      ),
    ),
  );
}
