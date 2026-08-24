package app.openstory.settings

import android.util.Log

class RedactedSettingsDiagnosticSink : SettingsDiagnosticSink {
    override fun onDiagnostic(code: SettingsDiagnosticCode) {
        Log.w(TAG, code.name)
    }

    private companion object {
        const val TAG = "SettingsPolicy"
    }
}
