package com.tgx.react

import android.content.Context

/** User settings for the keyboard. */
object KeyboardPrefs {
    const val MIN_HEIGHT_DP = 160
    const val MAX_HEIGHT_DP = 480
    const val DEFAULT_HEIGHT_DP = 250
    private const val KEY_HEIGHT = "height_dp"

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Height of the image grid in dp (the bottom bar is extra). */
    fun heightDp(context: Context): Int =
        prefs(context).getInt(KEY_HEIGHT, DEFAULT_HEIGHT_DP).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)

    fun setHeightDp(context: Context, dp: Int) =
        prefs(context).edit().putInt(KEY_HEIGHT, dp.coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)).apply()
}
