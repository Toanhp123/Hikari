import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_controller.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_policy.dart';
import 'package:hikari/core/ui/surfaces/bottom_sheet/hikari_bottom_sheet.dart';
import 'package:hikari/core/ui/surfaces/dialog/hikari_dialog.dart';

/// Chooses a native family once. Active route/content reflow without reopening.
/// Feature content owns its list viewport; host owns insets and route lifetime.
/// No automatic state restoration: reopen only from validated feature state.
Future<HikariPresentationResult<T>> showHikariAdaptive<T>({
  required BuildContext context,
  required HikariPresentationPurpose purpose,
  required bool useRootNavigator,
  required HikariContentBuilder<T> contentBuilder,
  HikariPresentationPolicy? policy,
}) {
  final media = MediaQuery.of(context);
  final mode = resolvePresentationMode(
    HikariPresentationRequest(
      purpose: purpose,
      availableWidth: media.size.width,
      availableHeight: media.size.height,
      keyboardInset: media.viewInsets.bottom,
    ),
    policy: policy,
  );
  return switch (mode) {
    HikariPresentationMode.dialog => showHikariDialog<T>(
      context: context,
      useRootNavigator: useRootNavigator,
      contentBuilder: (context, controller) => Dialog(
        constraints: const BoxConstraints(
          maxWidth: HikariBreakpoints.dialogMaxWidth,
        ),
        child: Padding(
          padding: const EdgeInsets.all(HikariSpacing.lg),
          child: contentBuilder(context, controller),
        ),
      ),
    ),
    HikariPresentationMode.bottomSheet => showHikariBottomSheet<T>(
      context: context,
      useRootNavigator: useRootNavigator,
      contentBuilder: (context, controller) => Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: SafeArea(
          top: false,
          child: Padding(
            padding: const EdgeInsets.all(HikariSpacing.lg),
            child: contentBuilder(context, controller),
          ),
        ),
      ),
    ),
  };
}
