import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';

/// Snapshot of available logical dimensions before opening one route.
final class HikariPresentationRequest {
  HikariPresentationRequest({
    required this.purpose,
    required this.availableWidth,
    required this.availableHeight,
    required this.keyboardInset,
    Set<HikariPresentationMode> supportedModes = const {
      HikariPresentationMode.dialog,
      HikariPresentationMode.bottomSheet,
    },
  }) : supportedModes = Set.unmodifiable(supportedModes);

  final HikariPresentationPurpose purpose;
  final double availableWidth;
  final double availableHeight;
  final double keyboardInset;
  final Set<HikariPresentationMode> supportedModes;
}

/// Optional caller-approved policy, constrained to supported hosts.
typedef HikariPresentationPolicy = HikariPresentationMode Function(
  HikariPresentationRequest request,
);

/// Pure initial-mode choice. An active session never reruns this on resize.
HikariPresentationMode resolvePresentationMode(
  HikariPresentationRequest request, {
  HikariPresentationPolicy? policy,
}) {
  for (final dimension in {
    'availableWidth': request.availableWidth,
    'availableHeight': request.availableHeight,
    'keyboardInset': request.keyboardInset,
  }.entries) {
    if (!dimension.value.isFinite || dimension.value < 0) {
      throw ArgumentError.value(
        dimension.value,
        dimension.key,
        'Must be finite and nonnegative',
      );
    }
  }
  if (request.keyboardInset > request.availableHeight ||
      request.supportedModes.isEmpty) {
    throw ArgumentError(
      'Keyboard must fit height and a host must be supported.',
    );
  }
  // Below 360dp usable height, a centered bounded surface avoids anchoring a
  // selection list beneath the keyboard. Both hosts still enforce live bounds.
  final preferred =
      policy?.call(request) ??
      (request.purpose != HikariPresentationPurpose.selection ||
              request.availableWidth >= HikariBreakpoints.expandedMin ||
              request.availableHeight - request.keyboardInset < 360
          ? HikariPresentationMode.dialog
          : HikariPresentationMode.bottomSheet);
  if (request.purpose == HikariPresentationPurpose.confirmation &&
      preferred != HikariPresentationMode.dialog) {
    throw ArgumentError.value(
      preferred,
      'mode',
      'Confirmation requires dialog',
    );
  }
  if (request.supportedModes.contains(preferred)) return preferred;
  if (policy != null ||
      request.purpose == HikariPresentationPurpose.confirmation) {
    throw ArgumentError.value(
      preferred,
      'mode',
      'Unsupported presentation mode',
    );
  }
  return request.supportedModes.first;
}
