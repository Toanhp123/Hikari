package app.openstory.catalog.domain.asset

internal fun Char.isHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

internal fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

internal fun Char.isAsciiLetterOrDigit(): Boolean = isAsciiLetter() || this in '0'..'9'
