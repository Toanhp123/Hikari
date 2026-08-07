package app.openstory.plugin.host.selector

import app.openstory.network.RequestBudget

data class SelectorLimits(
    val maxOperations: Int = 64,
    val maxDocumentCharacters: Int = 2_000_000,
    val maxDocumentNodes: Int = 50_000,
    val maxWallClockMillis: Long = 10_000,
    val requestBudget: RequestBudget = RequestBudget(),
) {
    init {
        require(maxOperations > 0) {
            "Maximum operation count must be positive."
        }
        require(maxDocumentCharacters > 0) {
            "Maximum document size must be positive."
        }
        require(maxDocumentNodes > 0) {
            "Maximum document node count must be positive."
        }
        require(maxWallClockMillis > 0) {
            "Maximum wall-clock duration must be positive."
        }
    }
}
