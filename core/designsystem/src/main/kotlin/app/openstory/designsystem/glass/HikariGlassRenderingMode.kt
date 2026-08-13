package app.openstory.designsystem.glass

import android.os.Build

enum class HikariGlassRenderingMode { TRANSLUCENT, BLUR }

fun glassRenderingMode(sdkInt: Int): HikariGlassRenderingMode =
    if (sdkInt >= Build.VERSION_CODES.S) HikariGlassRenderingMode.BLUR
    else HikariGlassRenderingMode.TRANSLUCENT
