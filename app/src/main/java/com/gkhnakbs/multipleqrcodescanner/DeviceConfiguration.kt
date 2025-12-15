package com.gkhnakbs.multipleqrcodescanner

/**
 * Created by Gökhan Akbaş on 16/10/2025.
 */

import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass

enum class DeviceConfiguration {
    MOBILE_PORTRAIT,
    MOBILE_LANDSCAPE,
    TABLET_PORTRAIT,
    TABLET_LANDSCAPE,
    DESKTOP;

    companion object {
        fun fromWindowSizeClass(windowSizeClass: WindowSizeClass): DeviceConfiguration {
            val widthClass = windowSizeClass.windowWidthSizeClass
            val heightClass = windowSizeClass.windowHeightSizeClass

            return when (widthClass) {
                WindowWidthSizeClass.COMPACT if heightClass == WindowHeightSizeClass.MEDIUM -> MOBILE_PORTRAIT
                WindowWidthSizeClass.COMPACT if heightClass == WindowHeightSizeClass.EXPANDED -> MOBILE_PORTRAIT
                WindowWidthSizeClass.EXPANDED if heightClass == WindowHeightSizeClass.COMPACT -> MOBILE_LANDSCAPE
                WindowWidthSizeClass.MEDIUM if heightClass == WindowHeightSizeClass.EXPANDED -> TABLET_PORTRAIT
                WindowWidthSizeClass.EXPANDED if heightClass == WindowHeightSizeClass.MEDIUM -> TABLET_LANDSCAPE
                else -> DESKTOP
            }
        }
    }
}