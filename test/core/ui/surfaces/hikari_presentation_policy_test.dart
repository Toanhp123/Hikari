import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_controller.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_policy.dart';

void main() {
  HikariPresentationRequest request({
    HikariPresentationPurpose purpose = HikariPresentationPurpose.selection,
    double width = 375,
    double height = 640,
    double keyboard = 0,
    Set<HikariPresentationMode> modes = const {
      HikariPresentationMode.dialog,
      HikariPresentationMode.bottomSheet,
    },
  }) => HikariPresentationRequest(
    purpose: purpose,
    availableWidth: width,
    availableHeight: height,
    keyboardInset: keyboard,
    supportedModes: modes,
  );

  test('selection compact sheet and expanded dialog', () {
    for (final width in <double>[375, 599, 600, 601, 839]) {
      expect(
        resolvePresentationMode(request(width: width)),
        HikariPresentationMode.bottomSheet,
      );
    }
    for (final width in <double>[840, 841, 1200, 1440]) {
      expect(
        resolvePresentationMode(request(width: width)),
        HikariPresentationMode.dialog,
      );
    }
  });
  test('confirmation always dialog and form stays bounded', () {
    for (final purpose in [
      HikariPresentationPurpose.confirmation,
      HikariPresentationPurpose.form,
    ]) {
      expect(
        resolvePresentationMode(request(purpose: purpose)),
        HikariPresentationMode.dialog,
      );
    }
  });
  test('short height and IME choose implemented dialog', () {
    expect(
      resolvePresentationMode(request(height: 300)),
      HikariPresentationMode.dialog,
    );
    expect(
      resolvePresentationMode(request(height: 640, keyboard: 350)),
      HikariPresentationMode.dialog,
    );
  });
  test('supported modes and explicit override cannot invent hosts', () {
    expect(
      resolvePresentationMode(request(modes: {HikariPresentationMode.dialog})),
      HikariPresentationMode.dialog,
    );
    expect(
      resolvePresentationMode(
        request(),
        policy: (_) => HikariPresentationMode.dialog,
      ),
      HikariPresentationMode.dialog,
    );
    expect(
      () => resolvePresentationMode(request(modes: {})),
      throwsArgumentError,
    );
    expect(
      () => resolvePresentationMode(
        request(modes: {HikariPresentationMode.bottomSheet}),
        policy: (_) => HikariPresentationMode.dialog,
      ),
      throwsArgumentError,
    );
    expect(
      () => resolvePresentationMode(
        request(
          purpose: HikariPresentationPurpose.confirmation,
          modes: {HikariPresentationMode.bottomSheet},
        ),
      ),
      throwsArgumentError,
    );
  });
  test('confirmation rejects a sheet override', () {
    expect(
      () => resolvePresentationMode(
        request(purpose: HikariPresentationPurpose.confirmation),
        policy: (_) => HikariPresentationMode.bottomSheet,
      ),
      throwsArgumentError,
    );
  });
  test('invalid dimensions rejected predictably', () {
    for (final value in [-1.0, double.nan, double.infinity]) {
      expect(
        () => resolvePresentationMode(request(width: value)),
        throwsArgumentError,
      );
      expect(
        () => resolvePresentationMode(request(height: value)),
        throwsArgumentError,
      );
      expect(
        () => resolvePresentationMode(request(keyboard: value)),
        throwsArgumentError,
      );
    }
    expect(
      () => resolvePresentationMode(request(height: 200, keyboard: 201)),
      throwsArgumentError,
    );
    expect(
      resolvePresentationMode(request(width: 0, height: 0)),
      HikariPresentationMode.dialog,
    );
  });
  test(
    'Completed null differs from Cancelled and settles exactly once',
    () async {
      final controller = HikariPresentationController<String?>();
      var notifications = 0;
      controller.addListener(() => notifications++);
      expect(controller.complete(null), isTrue);
      expect(controller.cancel(HikariDismissReason.back), isFalse);
      expect(controller.complete('late'), isFalse);
      final result = await controller.result;
      expect(result, isA<HikariCompleted<String?>>());
      expect((result as HikariCompleted<String?>).value, isNull);
      expect(notifications, 1);
      controller.dispose();
    },
  );
  test('pre-pop state and synchronous guard protect nested workflow', () async {
    final controller = HikariPresentationController<int>();
    var attempts = 0;
    controller.setDismissalPolicy(
      canDismiss: false,
      guard: (reason) {
        expect(reason, HikariDismissReason.back);
        attempts++;
        controller.setDismissalPolicy(canDismiss: true);
        return false;
      },
    );
    expect(controller.canDismiss, isFalse);
    expect(controller.cancel(HikariDismissReason.back), isFalse);
    expect(attempts, 1);
    expect(controller.isSettled, isFalse);
    expect(controller.canDismiss, isTrue);
    expect(controller.cancel(HikariDismissReason.cancelAction), isTrue);
    expect(
      (await controller.result as HikariCancelled<int>).reason,
      HikariDismissReason.cancelAction,
    );
    controller.dispose();
  });
  test('dispose resolves pending Future and ignores late completion', () async {
    final controller = HikariPresentationController<int>();
    controller.setDismissalPolicy(canDismiss: false);
    controller.dispose();
    controller.dispose();
    expect(controller.complete(42), isFalse);
    expect(controller.cancel(HikariDismissReason.back), isFalse);
    expect(
      (await controller.result as HikariCancelled<int>).reason,
      HikariDismissReason.routeDisposed,
    );
  });
  test('external dismissal bypasses guard without guessing cause', () async {
    final controller = HikariPresentationController<int>();
    controller.setDismissalPolicy(canDismiss: false);
    controller.didDismiss();
    expect(
      (await controller.result as HikariCancelled<int>).reason,
      HikariDismissReason.routeDismissed,
    );
    controller.dispose();
  });
}
