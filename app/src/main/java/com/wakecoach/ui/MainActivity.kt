package com.wakecoach.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakecoach.R
import com.wakecoach.container
import com.wakecoach.core.health.AlarmHealth
import com.wakecoach.core.health.HealthItem
import com.wakecoach.core.model.AlarmKind
import com.wakecoach.data.AlarmEntity
import com.wakecoach.ui.health.HealthScreen
import com.wakecoach.ui.progress.ProgressScreen
import com.wakecoach.ui.guard.GuardScreen
import com.wakecoach.alarm.GuardNotifier
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import java.time.LocalDate
import java.time.LocalDateTime
import com.wakecoach.ui.recordings.InterviewScreen
import com.wakecoach.ui.recordings.RecordingsScreen
import kotlinx.coroutines.delay
import java.time.LocalTime
import com.wakecoach.core.model.ChallengeType
import com.wakecoach.ui.edit.EditAlarmScreen
import com.wakecoach.ui.evening.EveningScreen
import com.wakecoach.ui.home.HomeScreen
import com.wakecoach.ui.home.SettingsScreen
import com.wakecoach.ui.demo.DemoDataScreen
import com.wakecoach.ui.skills.SkillsScreen
import com.wakecoach.ui.skills.SkillEditScreen
import com.wakecoach.ui.profiles.ProfilesScreen
import com.wakecoach.ui.profiles.ProfileEditScreen
import com.wakecoach.ui.profiles.asEditable
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
    data object Health : Screen
    data object Recordings : Screen
    data object Interview : Screen
    data object Progress : Screen
    data object Demo : Screen
    data class Guard(val date: LocalDate) : Screen
    data object Skills : Screen
    data class SkillEdit(val type: ChallengeType, val presetId: Long?) : Screen
    data object Profiles : Screen
    /** [id] edits a saved profile; [copyOf] starts from a built-in one; both null is a new profile. */
    data class ProfileEdit(val id: Long?, val copyOf: String? = null) : Screen
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var screen by mutableStateOf<Screen>(Screen.Home)
    // Bumped on every resume so permission-dependent UI re-reads system state after visiting Settings.
    private var resumes by mutableIntStateOf(0)
    private var stepAsked = false
    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

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
            WakeCoachTheme(mode, stored?.themePack) {
                val alarms by vm.alarms.collectAsStateWithLifecycle()
                val ringing by container.ringController.state.collectAsStateWithLifecycle()
                // If notifications are blocked the full-screen intent never shows; open the ring UI from here.
                LaunchedEffect(ringing != null) {
                    if (ringing != null) startActivity(Intent(this@MainActivity, RingingActivity::class.java))
                }
                BackHandler(enabled = screen != Screen.Home) {
                    screen = when (screen) {
                        is Screen.SkillEdit -> Screen.Skills
                        is Screen.ProfileEdit -> Screen.Profiles
                        Screen.Skills, Screen.Profiles -> Screen.Settings
                        else -> Screen.Home
                    }
                }
                val customProfiles by container.profiles.custom.collectAsStateWithLifecycle()
                // Where "manage profiles" from the alarm editor returns to.
                var profilesFrom by remember { mutableStateOf<Screen>(Screen.Settings) }

                val disabling by vm.pendingDisable.collectAsStateWithLifecycle()
                disabling?.let { alarm ->
                    AlertDialog(
                        onDismissRequest = { vm.pendingDisable.value = null },
                        title = { Text(getString(R.string.disable_title)) },
                        text = { Text(getString(R.string.disable_text)) },
                        confirmButton = {
                            TextButton(onClick = { vm.skipNext(alarm) }) { Text(getString(R.string.disable_skip_next)) }
                        },
                        dismissButton = {
                            Row {
                                TextButton(onClick = { vm.pendingDisable.value = null }) { Text(getString(R.string.cancel)) }
                                TextButton(onClick = { vm.pendingDisable.value = null; vm.setEnabled(alarm, false) }) {
                                    Text(getString(R.string.disable_turn_off))
                                }
                            }
                        },
                    )
                }

                // Re-read phone settings on every resume (the user may be back from fixing one).
                val checks = remember(resumes, alarms, settings.samsungNeverSleepConfirmed, settings.wakeCheck) {
                    container.health.run(alarms.filter { it.enabled }, settings.samsungNeverSleepConfirmed, settings.wakeCheck)
                }
                val healthProblem = !AlarmHealth.summarize(checks).allGood
                var testScheduled by remember { mutableStateOf(false) }
                // First launch: walk the user through what can silence an alarm on this phone.
                LaunchedEffect(Unit) {
                    if (!container.settings.current().healthIntroShown) {
                        screen = Screen.Health
                        container.settings.update { it.copy(healthIntroShown = true) }
                    }
                }

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
                        healthProblem = healthProblem,
                        onOpenHealth = { screen = Screen.Health },
                        onAdd = { screen = Screen.Edit(null) },
                        onEdit = { screen = Screen.Edit(it.id) },
                        onToggle = vm::requestToggle,
                        onEvening = { screen = Screen.Evening },
                        onSettings = { screen = Screen.Settings },
                        onProgress = { screen = Screen.Progress },
                        updateBanner = { UpdateBanner(update) { showUpdate = true } },
                    )
                    is Screen.Edit -> EditAlarmScreen(
                        initial = alarms.firstOrNull { it.id == s.alarmId },
                        leftHanded = settings.leftHanded,
                        customProfiles = customProfiles,
                        onManageProfiles = { profilesFrom = s; screen = Screen.Profiles },
                        onSave = {
                            val skills = container.profiles.resolveLoaded(it.profileId).challenges
                            if (ChallengeType.STEPS in skills && Build.VERSION.SDK_INT >= 29) {
                                askPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            } else if (ChallengeType.VOICE in skills) {
                                // Asked now, not over the lock screen at 6am.
                                askPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                            vm.save(it); screen = Screen.Home
                        },
                        onDelete = { vm.delete(it); screen = Screen.Home },
                        onCancel = { screen = Screen.Home },
                    )
                    Screen.Evening -> EveningScreen(
                        load = vm::tomorrowPriority,
                        loadReminders = vm::tomorrowGoalReminders,
                        onSave = { text, times -> vm.saveTomorrowPriority(text, times); screen = Screen.Home },
                        onCancel = { screen = Screen.Home },
                    )
                    Screen.Settings -> SettingsScreen(
                        settings = settings,
                        onChange = vm::updateSettings,
                        onBack = { screen = Screen.Home },
                        version = updater.installedVersion,
                        onOpenHealth = { screen = Screen.Health },
                        onOpenRecordings = { screen = Screen.Recordings },
                        onOpenDemo = { screen = Screen.Demo },
                        calendarConnected = resumes.let { container.calendar.hasPermission() },
                        onConnectCalendar = { askPermission.launch(Manifest.permission.READ_CALENDAR) },
                        onWakeCheckChange = { on -> vm.updateSettings { it.copy(wakeCheck = on) } },
                        onEmergencyStop = { container.ringController.emergencyStop() },
                        onOpenSkills = { screen = Screen.Skills },
                        onOpenProfiles = { profilesFrom = Screen.Settings; screen = Screen.Profiles },
                        onAddressChange = { a ->
                            vm.updateSettings { it.copy(address = a) }
                            AddressApplier.apply(this@MainActivity, a)
                        },
                        onCheckGuard = { screen = Screen.Guard(GuardNotifier.window(LocalDateTime.now()).first) },
                        onCheckUpdates = {
                            showUpdate = true
                            scope.launch { updater.check() }
                        },
                    )
                    Screen.Recordings -> RecordingsScreen(
                        repo = container.recordings,
                        playRecordings = settings.playRecordings,
                        onPlayRecordingsChange = { on -> vm.updateSettings { it.copy(playRecordings = on) } },
                        onInterview = { screen = Screen.Interview },
                        onBack = { screen = Screen.Settings },
                    )
                    Screen.Interview -> InterviewScreen(
                        repo = container.recordings,
                        onDone = { screen = Screen.Recordings },
                    )
                    Screen.Skills -> SkillsScreen(
                        repo = container.profiles,
                        onOpen = { type, id -> screen = Screen.SkillEdit(type, id) },
                        onBack = { screen = Screen.Settings },
                    )
                    is Screen.SkillEdit -> SkillEditScreen(
                        type = s.type,
                        preset = s.presetId?.let { id -> container.profiles.presets.value.firstOrNull { it.id == id } },
                        repo = container.profiles,
                        onNeedPermission = ::askFor,
                        onBack = { screen = Screen.Skills },
                    )
                    Screen.Profiles -> ProfilesScreen(
                        repo = container.profiles,
                        onEdit = { screen = Screen.ProfileEdit(it) },
                        onCopy = { screen = Screen.ProfileEdit(null, copyOf = it) },
                        onBack = { screen = profilesFrom.also { profilesFrom = Screen.Settings } },
                    )
                    is Screen.ProfileEdit -> {
                        val copySuffix = getString(R.string.profile_copy_suffix)
                        val initial = remember(s) {
                            s.id?.let { id -> customProfiles.firstOrNull { it.id == id } }
                                ?: s.copyOf?.let { b ->
                                    val p = com.wakecoach.core.model.Profiles.byId(b)
                                    p.asEditable(getString(profileName(b)) + " " + copySuffix)
                                }
                                ?: com.wakecoach.core.model.Profiles.BALANCED.asEditable("").copy(
                                    skills = com.wakecoach.core.skill.SkillConfig.encodeList(
                                        com.wakecoach.core.skill.Skills.suggestedPlan(com.wakecoach.core.model.Intensity.BALANCED)
                                    ),
                                )
                        }
                        ProfileEditScreen(
                            initial = initial,
                            repo = container.profiles,
                            onNeedPermission = ::askFor,
                            onBack = { screen = Screen.Profiles },
                        )
                    }
                    Screen.Demo -> DemoDataScreen(container.demo, onBack = { screen = Screen.Settings })
                    Screen.Progress -> ProgressScreen(container.repository, onBack = { screen = Screen.Home })
                    is Screen.Guard -> GuardScreen(
                        date = s.date,
                        repo = container.guard,
                        onChanged = { container.scheduler.rescheduleAll() },
                        onBack = { screen = Screen.Home },
                    )
                    Screen.Health -> HealthScreen(
                        checks = checks,
                        testScheduled = testScheduled,
                        onFix = { item ->
                            when (item) {
                                HealthItem.ALARM_VOLUME -> { container.health.raiseAlarmVolume(); resumes++ }
                                HealthItem.NEXT_ALARM_REGISTERED ->
                                    scope.launch { container.scheduler.rescheduleAll(); resumes++ }
                                HealthItem.STEP_ACCESS -> if (Build.VERSION.SDK_INT >= 29) {
                                    if (shouldShowRequestPermissionRationale(Manifest.permission.ACTIVITY_RECOGNITION) ||
                                        !stepAsked
                                    ) {
                                        stepAsked = true
                                        askPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                    } else {
                                        container.health.openFix(this@MainActivity, HealthItem.BACKGROUND_RESTRICTED)
                                    }
                                }
                                else -> container.health.openFix(this@MainActivity, item)
                            }
                            Unit // the when above is a statement: its branches return different types
                        },
                        onConfirmSamsung = { vm.updateSettings { it.copy(samsungNeverSleepConfirmed = true) } },
                        onTestAlarm = {
                            testScheduled = true
                            scope.launch {
                                val now = LocalTime.now()
                                val test = AlarmEntity(
                                    hour = now.hour, minute = now.minute,
                                    label = getString(R.string.health_test_label), kind = AlarmKind.TEST.name,
                                )
                                val id = container.repository.alarms.upsert(test)
                                container.scheduler.scheduleTest(test.copy(id = id), seconds = 10)
                                delay(20_000)
                                testScheduled = false
                            }
                        },
                        onBack = { screen = Screen.Home },
                    )
                }
            }
        }
    }

    /** Practice needs the same permissions as a real morning; ask before, not over the lock screen. */
    private fun askFor(types: List<ChallengeType>) {
        val needed = types.mapNotNull { type ->
            when (type) {
                ChallengeType.VOICE -> Manifest.permission.RECORD_AUDIO
                ChallengeType.STEPS -> if (Build.VERSION.SDK_INT >= 29) Manifest.permission.ACTIVITY_RECOGNITION else null
                else -> null
            }
        }.distinct().filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) askPermissions.launch(needed.toTypedArray())
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
        intent?.getLongExtra(EXTRA_OPEN_GUARD, Long.MIN_VALUE)?.takeIf { it != Long.MIN_VALUE }?.let {
            screen = Screen.Guard(LocalDate.ofEpochDay(it))
        }
    }

    companion object {
        const val EXTRA_OPEN_EVENING = "open_evening"
        const val EXTRA_OPEN_GUARD = "open_guard"
    }
}
