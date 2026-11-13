package com.unplug.app

import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// ================================================================
// Installed apps
// ================================================================

data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
) {
    val key: String get() = "$packageName/$activityName"
    val component: ComponentName get() = ComponentName(packageName, activityName)
}

/** First letter used for drawer sections; anything that isn't A–Z goes under '#'. */
fun letterOf(label: String): Char =
    label.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

/** Loads launchable apps and caches their icons, all off the main thread. */
class AppsModel(private val context: Context) {

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    private val icons = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun refresh() {
        val list = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            )
                .asSequence()
                .filter { it.activityInfo.packageName != context.packageName }
                .map {
                    AppEntry(
                        label = it.loadLabel(pm).toString().trim(),
                        packageName = it.activityInfo.packageName,
                        activityName = it.activityInfo.name,
                    )
                }
                .distinctBy { it.key }
                // '#' group first, then A–Z, so every section appears exactly once.
                .sortedWith(compareBy({ if (letterOf(it.label) == '#') 0 else 1 }, { it.label.lowercase() }))
                .toList()
        }
        apps = list
        // Warm the icon cache so scrolling the drawer stays smooth.
        withContext(Dispatchers.IO) { list.forEach { loadIcon(it) } }
    }

    fun cachedIcon(entry: AppEntry): ImageBitmap? = icons[entry.key]

    suspend fun icon(entry: AppEntry): ImageBitmap? =
        icons[entry.key] ?: withContext(Dispatchers.IO) { loadIcon(entry) }

    fun byKey(key: String): AppEntry? = apps.firstOrNull { it.key == key }

    private fun loadIcon(entry: AppEntry): ImageBitmap? {
        icons[entry.key]?.let { return it }
        return runCatching {
            context.packageManager.getActivityIcon(entry.component)
                .toBitmap(ICON_PX, ICON_PX)
                .asImageBitmap()
        }.getOrNull()?.also { icons[entry.key] = it }
    }

    private companion object {
        const val ICON_PX = 108
    }
}

// ================================================================
// Pinned apps on the home screen
// ================================================================

object Favorites {
    const val MAX = 6
    private const val PREFS = "launcher"
    private const val KEY = "favorites"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(c: Context): List<String> =
        prefs(c).getString(KEY, null).orEmpty().split('\n').filter { it.isNotBlank() }

    fun save(c: Context, keys: List<String>) {
        prefs(c).edit().putString(KEY, keys.joinToString("\n")).apply()
    }
}

// ================================================================
// Default home app
// ================================================================

object HomeRole {
    fun isDefault(c: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val info = c.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == c.packageName
    }

    /** The system "set as default home app" prompt (Android 10+), or null if unavailable. */
    fun requestIntent(c: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = c.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return null
    }
}

// ================================================================
// Actions
// ================================================================

private fun Context.safeStart(intent: Intent, options: Bundle? = null) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
    } catch (e: Exception) {
        Toast.makeText(this, "Can't open that", Toast.LENGTH_SHORT).show()
    }
}

/** Opens an app, growing it out of the row that was tapped. */
fun launchApp(context: Context, view: View, entry: AppEntry, from: Rect) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(entry.component)
        .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    val options = if (from.width > 0f && from.height > 0f) {
        ActivityOptions.makeClipRevealAnimation(
            view, from.left.toInt(), from.top.toInt(), from.width.toInt(), from.height.toInt()
        ).toBundle()
    } else null
    context.safeStart(intent, options)
}

fun openPhone(context: Context) = context.safeStart(Intent(Intent.ACTION_DIAL))

fun openWhatsApp(context: Context) {
    val launch = AllowList.WHATSAPP.firstNotNullOfOrNull {
        context.packageManager.getLaunchIntentForPackage(it)
    }
    if (launch == null) {
        Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
    } else {
        context.safeStart(launch)
    }
}

fun openClock(context: Context) = context.safeStart(Intent(AlarmClock.ACTION_SHOW_ALARMS))

fun openAppInfo(context: Context, packageName: String) = context.safeStart(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
)

fun uninstallApp(context: Context, packageName: String) = context.safeStart(
    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
)

fun openAccessibilitySettings(context: Context) =
    context.safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

fun openHomeSettings(context: Context) = context.safeStart(Intent(Settings.ACTION_HOME_SETTINGS))

fun openPhoneSettings(context: Context) = context.safeStart(Intent(Settings.ACTION_SETTINGS))

/** e.g. "84%" or "84% CHARGING". Read locally from the battery service. */
fun batteryText(context: Context): String {
    val bm = context.getSystemService(BatteryManager::class.java) ?: return ""
    val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    if (level !in 0..100) return ""
    return if (bm.isCharging) "$level% charging" else "$level%"
}
