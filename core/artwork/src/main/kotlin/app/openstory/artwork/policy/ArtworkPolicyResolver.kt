package app.openstory.artwork.policy

import app.openstory.artwork.request.ArtworkAuthorityKey

fun interface ArtworkPolicyResolver {
    fun policyFor(authority: ArtworkAuthorityKey): ArtworkPolicy?
}
