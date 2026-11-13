package com.unplug.app

import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/**
 * Which packages may be in the foreground during a focus session:
 * Phone (dialer, in-call screen, contacts), WhatsApp, and the bits of the system
 * needed for those to work (home screen, keyboard, permission dialogs).
 */
object AllowList {
    val WHATSAPP = listOf("com.whatsapp", "com.whatsapp.w4b")

    /** Common dialer / in-call / contacts packages across Pixel, Samsung, Xiaomi, OnePlus, Oppo, etc. */
    private val PHONE = setOf(
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.contacts",
        "com.google.android.contacts",
        "com.samsung.android.app.contacts",
        "com.samsung.android.contacts",
        "com.miui.contacts",
        "com.oneplus.dialer",
        "com.oplus.dialer",
        "com.coloros.dialer",
        "com.huawei.contacts",
    )

    /** System surfaces: status bar / notification shade, system dialogs, permission prompts. */
    private val SYSTEM = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
    )

    /**
     * Never allowed, not even as a user exception: Settings (it could switch the blocker off)
     * and the package installer (it could uninstall Unplug).
     */
    val NEVER = setOf(
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
    )

    /** Allowed no matter what — shown as fixed entries in the exceptions picker. */
    fun isAlwaysAllowed(packageName: String): Boolean = packageName in WHATSAPP || packageName in PHONE

    fun build(context: Context): Set<String> {
        val pm = context.packageManager
        val allowed = mutableSetOf(context.packageName)
        allowed += WHATSAPP
        allowed += PHONE
        allowed += SYSTEM
        // Apps the user added as exceptions in Launcher settings.
        allowed += SettingsStore.load(context).exceptions

        // Whatever this phone uses as its default dialer.
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()?.let { allowed += it }

        // Every app that can handle "dial a number".
        pm.queryIntentActivities(Intent(Intent.ACTION_DIAL), 0)
            .forEach { allowed += it.activityInfo.packageName }

        // Home screen launchers, so pressing Home isn't a loop.
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).forEach { allowed += it.activityInfo.packageName }

        // Keyboards, so you can type in WhatsApp.
        runCatching {
            context.getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList
                ?.forEach { allowed += it.packageName }
        }

        return allowed - NEVER
    }

    /**
     * Windows that pop up *over* the app you're using (keyboard, status bar, system dialogs,
     * home screen). They don't count as "switching app" for daily limits and pauses.
     */
    fun transientPackages(context: Context): Set<String> {
        val out = mutableSetOf<String>()
        out += SYSTEM
        runCatching {
            context.getSystemService(InputMethodManager::class.java)
                ?.enabledInputMethodList
                ?.forEach { out += it.packageName }
        }
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).forEach { out += it.activityInfo.packageName }
        return out
    }
}
