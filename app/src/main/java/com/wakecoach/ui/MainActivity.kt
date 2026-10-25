package com.wakecoach.ui

import com.wakecoach.data.profile
import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakecoach.container
import com.wakecoach.core.model.ChallengeType
import com.wakecoach.ui.edit.EditAlarmScreen
import com.wakecoach.ui.evening.EveningScreen
import com.wakecoach.ui.home.HomeScreen
import com.wakecoach.ui.home.SettingsScreen
import com.wakecoach.core.update.UpdateCheck
import com.wakecoach.ui.ringing.RingingActivity
import com.wakecoach.ui.update.UpdateBanner
import com.wakecoach.ui.update.UpdateDialog
import com.wakecoach.update.UpdateState
import kotlinx.coroutines.launch
import com.wakecoach.ui.theme.WakeCoachTheme

sealed interface Screen {
    data object Home : Screen
    data class Edit(val alarmId: Long?) : Screen
    data object Evening : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var screen by mutableStateOf<Screen>(Screen.Home)
    // Bumped on every resume so permission-dependent UI re-reads system state after visiting Settings.
    private var resumes by mutableIntStateOf(0)
    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            // Wait for the stored theme so a forced light/dark choice does not flash the system one.
            val stored by container.settings.settings.collectAsStateWithLifecycle(null)
            val mode = stored?.themeMode ?: return@setContent
            WakeCoachTheme(mode) {
                val alarms by vm.alarms.collectAsStateWithLifecycle()
                val ringing by container.ringController.state.collectAsStateWithLifecycle()
                // If notifications are blocked the full-screen intent never shows; open the ring UI from here.
                LaunchedEffect(ringing != null) {
                    if (ringing != null) startActivity(Intent(this@MainActivity, RingingActivity::class.java))
                }
                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

                val updater = container.updater
                val update by updater.state.collectAsStateWithLifecycle()
                var showUpdate by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                // Quiet check on open, at most every few hours; the banner appears only if there is something new.
                LaunchedEffect(Unit) {
                    val s = container.settings.current()
                    val now = System.currentTimeMillis()
                    if (s.autoCheckUpdates && UpdateCheck.dueForAutoCheck(s.lastUpdateCheckMs, now)) {
                        updater.check()
                        container.settings.update { it.copy(lastUpdateCheckMs = now) }
                    }
                }
                if (showUpdate) {
                    UpdateDialog(
                        state = update,
                        needsInstallPermission = resumes.let { updater.needsInstallPermission() },
                        onUpdate = {
                            (update as? UpdateState.Available)?.let { scope.launch { updater.downloadAndInstall(it.release) } }
                        },
                        onGrantPermission = { startActivity(updater.installPermissionIntent()) },
                        onDismiss = { showUpdate = false; updater.dismissError() },
                    )
                }

                when (val s = screen) {
                    Screen.Home -> HomeScreen(
                        alarms = alarms,
                        leftHanded = settings.leftHanded,
                        needsFullScreenPermission = !canUseFullScreenIntent() || !notificationsEnabled(),
                        onFixFullScreen = ::openAlarmDisplaySettings,
                        onAdd = { screen = Screen.Edit(null) },
                        onEdit = { screen = Screen.Edit(it.id) },
                        onToggle = vm::setEnabled,
                        onEvening = { screen = Screen.Evening },
                        onSettings = { screen = Screen.Settings },
                        updateBanner = { UpdateBanner(update) { showUpdate = true } },
                    )
                    is Screen.Edit -> EditAlarmScreen(
                        initial = alarms.firstOrNull { it.id == s.alarmId },
                        leftHanded = settings.leftHanded,
                        onSave = {
                            if (ChallengeType.STEPS in it.profile.challenges && Build.VERSION.SDK_INT >= 29) {
                                askPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            }
                            vm.save(it); screen = Screen.Home
                        },
                        onDelete = { vm.delete(it); screen = Screen.Home },
                        onCancel = { screen = Screen.Home },
                    )
                    Screen.Evening -> EveningScreen(
                        load = vm::tomorrowPriority,
                        onSave = { vm.saveTomorrowPriority(it); screen = Screen.Home },
                        onCancel = { screen = Screen.Home },
                    )
                    Screen.Settings -> SettingsScreen(
                        settings = settings,
                        onChange = vm::updateSettings,
                        onBack = { screen = Screen.Home },
                        version = updater.installedVersion,
                        onCheckUpdates = {
                            showUpdate = true
                            scope.launch { updater.check() }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumes++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_EVENING, false) == true) screen = Screen.Evening
    }

    private fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun notificationsEnabled() = NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun openAlarmDisplaySettings() {
        if (!notificationsEnabled()) {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        } else if (Build.VERSION.SDK_INT >= 34) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
            )
        }
    }

    companion object {
        const val EXTRA_OPEN_EVENING = "open_evening"
    }
}
