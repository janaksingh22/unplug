package com.unplug.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// ================================================================
// Options
// ================================================================

enum class ThemeMode(val label: String) { AUTO("Auto"), DARK("Dark"), LIGHT("Light") }

enum class ClockFace(val label: String) { RING("Ring"), ANALOG("Analog"), DIGITAL("Digital"), ORBIT("Orbit") }

enum class HourFormat(val label: String) { SYSTEM("System"), H12("12h"), H24("24h") }

enum class FontChoice(val label: String) {
    MODERN("Modern"),
    CONDENSED("Condensed"),
    SERIF("Serif"),
    MONO("Mono"),
    SCRIPT("Script"),

    /** Bundled font, only offered when `assets/fonts/SpaceGrotesk.ttf` is in the app. */
    SPACE("Space"),
}

enum class ClockWeight(val label: String, val weight: FontWeight) {
    THIN("Thin", FontWeight.Thin),
    LIGHT("Light", FontWeight.Light),
    REGULAR("Regular", FontWeight.Normal),
    BOLD("Bold", FontWeight.SemiBold),
}

enum class IconStyle(val label: String) { GRAYSCALE("Mono"), COLOR("Color"), HIDDEN("Off") }

enum class HomeTextSize(val label: String, val sp: Int) { SMALL("S", 20), MEDIUM("M", 24), LARGE("L", 30) }

/**
 * A recurring focus window, e.g. 22:00 → 06:00 every night.
 * [days] is a bitmask, Monday = bit 0 … Sunday = bit 6, for the day the window *starts*.
 */
data class Schedule(
    val id: Long,
    val startMinute: Int,
    val endMinute: Int,
    val days: Int,
    val enabled: Boolean = true,
) {
    /** Length in minutes; windows that end "earlier" than they start run past midnight. */
    val lengthMinutes: Int
        get() = ((endMinute - startMinute) + 24 * 60) % (24 * 60)

    fun encode(): String = "$id|$startMinute|$endMinute|$days|${if (enabled) 1 else 0}"

    companion object {
        const val EVERY_DAY = 0b1111111
        const val WEEKDAYS = 0b0011111

        fun decode(s: String): Schedule? = runCatching {
            val p = s.split('|')
            Schedule(p[0].toLong(), p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4] == "1")
        }.getOrNull()
    }
}

data class LauncherSettings(
    // Appearance
    val theme: ThemeMode = ThemeMode.DARK,
    val font: FontChoice = FontChoice.MODERN,
    val iconStyle: IconStyle = IconStyle.GRAYSCALE,
    val homeTextSize: HomeTextSize = HomeTextSize.MEDIUM,
    val hiddenApps: Set<String> = emptySet(),
    // Watch
    val clockFace: ClockFace = ClockFace.RING,
    val hourFormat: HourFormat = HourFormat.SYSTEM,
    val clockWeight: ClockWeight = ClockWeight.THIN,
    val showSeconds: Boolean = true,
    val showDate: Boolean = true,
    val showBattery: Boolean = true,
    // Focus
    val exceptions: Set<String> = emptySet(),
    val schedules: List<Schedule> = emptyList(),
    val blockPowerMenu: Boolean = true,
    /** Below this battery % (and not charging) the power menu is left alone. 0 = always lock. */
    val lowBatteryPercent: Int = 5,
    val silenceNotifications: Boolean = true,
    val emergencyUnlock: Boolean = false,
    val emergencyNumber: String = "112",
    // Wellbeing
    /** Package → minutes per day. */
    val appLimits: Map<String, Int> = emptyMap(),
    val mindfulApps: Set<String> = emptySet(),
    val pauseSeconds: Int = 10,
    /** After "Open anyway", don't ask again for this many minutes. */
    val pauseGraceMinutes: Int = 5,
    val dailyGoalMinutes: Int = 30,
    // General
    val haptics: Boolean = true,
    val onboarded: Boolean = false,
)

val LocalSettings = staticCompositionLocalOf { LauncherSettings() }

// ================================================================
// Storage (SharedPreferences, on the phone only)
// ================================================================

class SettingsStore(private val context: Context) {

    var value by mutableStateOf(load(context))
        private set

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        val next = transform(value)
        if (next == value) return
        value = next
        save(context, next)
    }

    companion object {
        private const val PREFS = "settings"

        private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun load(c: Context): LauncherSettings {
            val p = prefs(c)
            val d = LauncherSettings()
            return LauncherSettings(
                theme = p.enum("theme", d.theme),
                font = p.enum("font", d.font),
                iconStyle = p.enum("iconStyle", d.iconStyle),
                homeTextSize = p.enum("homeTextSize", d.homeTextSize),
                hiddenApps = p.set("hiddenApps"),
                clockFace = p.enum("clockFace", d.clockFace),
                hourFormat = p.enum("hourFormat", d.hourFormat),
                clockWeight = p.enum("clockWeight", d.clockWeight),
                showSeconds = p.getBoolean("showSeconds", d.showSeconds),
                showDate = p.getBoolean("showDate", d.showDate),
                showBattery = p.getBoolean("showBattery", d.showBattery),
                exceptions = p.set("exceptions"),
                schedules = p.getString("schedules", null).orEmpty()
                    .split(';').filter { it.isNotBlank() }.mapNotNull { Schedule.decode(it) },
                blockPowerMenu = p.getBoolean("blockPowerMenu", d.blockPowerMenu),
                lowBatteryPercent = p.getInt("lowBatteryPercent", d.lowBatteryPercent),
                silenceNotifications = p.getBoolean("silenceNotifications", d.silenceNotifications),
                emergencyUnlock = p.getBoolean("emergencyUnlock", d.emergencyUnlock),
                emergencyNumber = p.getString("emergencyNumber", null) ?: d.emergencyNumber,
                appLimits = p.set("appLimits").mapNotNull { entry ->
                    val i = entry.lastIndexOf('=')
                    if (i <= 0) null else entry.substring(i + 1).toIntOrNull()?.let { entry.substring(0, i) to it }
                }.toMap(),
                mindfulApps = p.set("mindfulApps"),
                pauseSeconds = p.getInt("pauseSeconds", d.pauseSeconds),
                pauseGraceMinutes = p.getInt("pauseGraceMinutes", d.pauseGraceMinutes),
                dailyGoalMinutes = p.getInt("dailyGoalMinutes", d.dailyGoalMinutes),
                haptics = p.getBoolean("haptics", d.haptics),
                onboarded = p.getBoolean("onboarded", d.onboarded),
            )
        }

        fun save(c: Context, s: LauncherSettings) {
            prefs(c).edit()
                .putString("theme", s.theme.name)
                .putString("font", s.font.name)
                .putString("iconStyle", s.iconStyle.name)
                .putString("homeTextSize", s.homeTextSize.name)
                .putStringSet("hiddenApps", s.hiddenApps.toSet())
                .putString("clockFace", s.clockFace.name)
                .putString("hourFormat", s.hourFormat.name)
                .putString("clockWeight", s.clockWeight.name)
                .putBoolean("showSeconds", s.showSeconds)
                .putBoolean("showDate", s.showDate)
                .putBoolean("showBattery", s.showBattery)
                .putStringSet("exceptions", s.exceptions.toSet())
                .putString("schedules", s.schedules.joinToString(";") { it.encode() })
                .putBoolean("blockPowerMenu", s.blockPowerMenu)
                .putInt("lowBatteryPercent", s.lowBatteryPercent)
                .putBoolean("silenceNotifications", s.silenceNotifications)
                .putBoolean("emergencyUnlock", s.emergencyUnlock)
                .putString("emergencyNumber", s.emergencyNumber)
                .putStringSet("appLimits", s.appLimits.map { (k, v) -> "$k=$v" }.toSet())
                .putStringSet("mindfulApps", s.mindfulApps.toSet())
                .putInt("pauseSeconds", s.pauseSeconds)
                .putInt("pauseGraceMinutes", s.pauseGraceMinutes)
                .putInt("dailyGoalMinutes", s.dailyGoalMinutes)
                .putBoolean("haptics", s.haptics)
                .putBoolean("onboarded", s.onboarded)
                .apply()
        }
    }
}

private inline fun <reified T : Enum<T>> SharedPreferences.enum(key: String, default: T): T =
    getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

private fun SharedPreferences.set(key: String): Set<String> = getStringSet(key, null)?.toSet() ?: emptySet()

// ================================================================
// Fonts — built into Android, plus one optional bundled font
// ================================================================

@OptIn(ExperimentalTextApi::class)
private val CondensedFamily: FontFamily = FontFamily(
    listOf(FontWeight.Thin, FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)
        .map { Font(DeviceFontFamilyName("sans-serif-condensed"), it) }
)

private const val SPACE_ASSET = "fonts/SpaceGrotesk.ttf"
private var spaceFamily: FontFamily? = null
private var spaceChecked = false

/** Loads the optional bundled font once; null if the file isn't in the app. */
private fun loadSpace(context: Context): FontFamily? {
    if (!spaceChecked) {
        spaceChecked = true
        spaceFamily = runCatching {
            FontFamily(Typeface.createFromAsset(context.assets, SPACE_ASSET))
        }.getOrNull()
    }
    return spaceFamily
}

fun isSpaceFontAvailable(context: Context): Boolean = loadSpace(context) != null

fun FontChoice.family(context: Context): FontFamily = when (this) {
    FontChoice.MODERN -> FontFamily.SansSerif
    FontChoice.CONDENSED -> CondensedFamily
    FontChoice.SERIF -> FontFamily.Serif
    FontChoice.MONO -> FontFamily.Monospace
    FontChoice.SCRIPT -> FontFamily.Cursive
    FontChoice.SPACE -> loadSpace(context) ?: FontFamily.SansSerif
}
