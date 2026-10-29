package dev.pogo.pocket

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * What lets the 15-minute background sync run. Android's battery saver may
 * delay it for hours, and Xiaomi's HyperOS blocks it altogether unless the app
 * may autostart, so the widgets would only update when the app is opened.
 */
object BackgroundSync {
    fun isUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Shows the system dialog that exempts the app from battery optimization. */
    @SuppressLint("BatteryLife") // Background sync is what keeps the widgets current.
    fun requestUnrestricted(context: Context) {
        val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        start(context, ask) || start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    val isXiaomi: Boolean get() = Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)

    /** Opens MIUI/HyperOS's autostart list, or the app's settings page if it's missing. */
    fun openAutostart(context: Context) {
        val autostart = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        start(context, autostart) || start(context, appDetails(context))
    }

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}
