package com.unplug.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The home screen. Registered as a HOME activity, so it can be the phone's launcher. */
class MainActivity : ComponentActivity() {

    private lateinit var apps: AppsModel
    private lateinit var settings: SettingsStore

    /** Time (elapsedRealtime) the blocker last sent the user back here, why, and for which app. */
    private var blockedAt by mutableLongStateOf(0L)
    private var blockedReason by mutableStateOf(BlockerService.REASON_APP)
    private var blockedPackage by mutableStateOf<String?>(null)

    /** Time the Home button was pressed while we were already on top. */
    private var homePressedAt by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apps = AppsModel(applicationContext)
        settings = SettingsStore(applicationContext)
        handleIntent(intent)
        setContent {
            LauncherRoot(
                apps = apps,
                settingsStore = settings,
                blockedAt = blockedAt,
                blockedReason = blockedReason,
                blockedPackage = blockedPackage,
                homePressedAt = homePressedAt,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val fromBlocker = handleIntent(intent)
        if (!fromBlocker && intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePressedAt = SystemClock.elapsedRealtime()
        }
    }

    /** Returns true if this intent came from the blocker. */
    private fun handleIntent(intent: Intent?): Boolean {
        val reason = intent?.getStringExtra(EXTRA_BLOCKED) ?: return false
        blockedReason = reason
        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE)
        blockedAt = SystemClock.elapsedRealtime()
        intent.removeExtra(EXTRA_BLOCKED)
        intent.removeExtra(EXTRA_PACKAGE)
        return true
    }

    companion object {
        const val EXTRA_BLOCKED = "blocked"
        const val EXTRA_PACKAGE = "package"
    }
}
