package app.openstory.settings

enum class SettingsDiagnosticCode {
    PREFERENCES_READ_FAILED,
    PREFERENCES_CORRUPTED,
    PREFERENCES_WRITE_FAILED,
}

fun interface SettingsDiagnosticSink {
    fun onDiagnostic(code: SettingsDiagnosticCode)
}
