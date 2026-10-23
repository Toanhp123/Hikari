/// Interaction intent, independent of feature data and navigation.
enum HikariPresentationPurpose { confirmation, selection, form }

/// Implemented transient families. Navigation drawers are not modal results.
enum HikariPresentationMode { dialog, bottomSheet }

/// Precise reasons require an observed event; ambiguous exits are routeDismissed.
enum HikariDismissReason {
  cancelAction,
  closeButton,
  back,
  barrier,
  drag,
  routeDismissed,
  routeDisposed,
}

/// A submitted value (including null) or a cancellation, never nullable signaling.
sealed class HikariPresentationResult<T> {
  const HikariPresentationResult();
}

/// Explicit successful interaction, not business authorization.
final class HikariCompleted<T> extends HikariPresentationResult<T> {
  const HikariCompleted(this.value);

  final T value;
}

/// Interaction ended without submitting a value.
final class HikariCancelled<T> extends HikariPresentationResult<T> {
  const HikariCancelled(this.reason);

  final HikariDismissReason reason;
}
