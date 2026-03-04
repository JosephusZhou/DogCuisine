package com.dogcuisine.ui

import android.app.Activity
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

object ComposeSystemBarDelegate {

    @JvmStatic
    fun install(
        activity: AppCompatActivity,
        lightStatusBars: Boolean = true,
        @ColorInt statusBarColor: Int = Color.TRANSPARENT
    ): Controller {
        return Controller(activity, lightStatusBars, statusBarColor).also { controller ->
            activity.lifecycle.addObserver(controller)
            controller.apply()
        }
    }

    class Controller internal constructor(
        private val activity: Activity,
        private val lightStatusBars: Boolean,
        @ColorInt private val statusBarColor: Int
    ) : DefaultLifecycleObserver {

        override fun onStart(owner: LifecycleOwner) {
            apply()
        }

        override fun onResume(owner: LifecycleOwner) {
            apply()
        }

        fun apply() {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)?.let { controller ->
                controller.isAppearanceLightStatusBars = lightStatusBars
            }
            activity.window.statusBarColor = statusBarColor
        }
    }
}
