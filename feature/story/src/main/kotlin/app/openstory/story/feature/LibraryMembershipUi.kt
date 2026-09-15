package app.openstory.story.feature

internal sealed interface LibraryMembershipUi {
    data object NotSaved : LibraryMembershipUi
    data object Saving : LibraryMembershipUi
    data object Saved : LibraryMembershipUi
    data object Removing : LibraryMembershipUi
}
