package app.openstory.catalog.ui.feedback

internal fun catalogFailureMessage(codeOrMessage: String, fallback: String): String =
    if (MACHINE_FAILURE_CODE.matches(codeOrMessage)) fallback else codeOrMessage

private val MACHINE_FAILURE_CODE = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)+")
