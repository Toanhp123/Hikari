package app.openstory.artwork

fun interface ArtworkPolicyResolver {
    fun policyFor(authority: ArtworkAuthorityKey): ArtworkPolicy?
}
