package com.eslcall.app

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Keeps branded headers clear of status icons, camera cutouts, and notches. */
fun View.applyStatusBarInset() {
    val originalTopPadding = paddingTop
    val originalHeight = layoutParams.height
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val topInset = windowInsets.getInsets(
            WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.displayCutout(),
        ).top
        view.setPadding(
            view.paddingLeft,
            originalTopPadding + topInset,
            view.paddingRight,
            view.paddingBottom,
        )
        if (originalHeight > 0) {
            view.layoutParams = view.layoutParams.apply {
                height = originalHeight + topInset
            }
        }
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
