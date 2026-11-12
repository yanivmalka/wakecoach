package com.wakecoach.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakecoach.R
import com.wakecoach.core.challenge.Challenge
import com.wakecoach.core.challenge.ChallengeGenerator
import com.wakecoach.core.model.ChallengeType
import com.wakecoach.core.model.Intensity
import com.wakecoach.core.model.Profile
import com.wakecoach.core.model.Profiles
import com.wakecoach.core.skill.SkillConfig
import com.wakecoach.core.skill.SkillMode
import com.wakecoach.core.skill.Skills
import com.wakecoach.data.CustomProfileEntity
import com.wakecoach.data.ProfileRepository
import com.wakecoach.data.toProfile
import com.wakecoach.ui.components.ConfirmDeleteDialog
import com.wakecoach.ui.profileDescription
import com.wakecoach.ui.profileName
import com.wakecoach.ui.skills.PracticeOverlay
import com.wakecoach.ui.skills.SkillParamsEditor
import com.wakecoach.ui.skills.intensityName
import com.wakecoach.ui.skills.skillEmoji
import com.wakecoach.ui.skills.skillName
import com.wakecoach.ui.skills.skillSummary
import com.wakecoach.ui.skills.skillTitle
import com.wakecoach.ui.theme.BigTouch
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Built-in profiles (copy to edit) and the user's own (edit, delete). */
@Composable
fun ProfilesScreen(
    repo: ProfileRepository,
    onEdit: (Long?) -> Unit,
    onCopy: (String) -> Unit,
    onBack: () -> Unit,
) {
    val custom by repo.custom.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<Pair<CustomProfileEntity, Int>?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(20.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.profiles_title), fontSize = 28.sp)
            Text(stringResource(R.string.profiles_explain), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onEdit(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("＋ " + stringResource(R.string.profile_new), fontSize = 18.sp)
            }
            if (custom.isNotEmpty()) Text(stringResource(R.string.profiles_mine), fontSize = 20.sp)
            custom.forEach { p ->
                val profile = p.toProfile()
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp))
                        .clickable { onEdit(p.id) }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text("👤 " + p.name, fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            profile.skills.joinToString(" → ") { skillEmoji(it.type) },
                            fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    TextButton(onClick = { scope.launch { deleting = p to repo.alarmsUsing(p) } }) { Text("🗑", fontSize = 18.sp) }
                }
            }
            Text(stringResource(R.string.profiles_builtin), fontSize = 20.sp)
            Profiles.all.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(stringResource(profileName(p.id)), fontSize = 19.sp)
                        Text(stringResource(profileDescription(p.id)), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onCopy(p.id) }) { Text(stringResource(R.string.profile_copy)) }
                }
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = BigTouch)) {
            Text(stringResource(R.string.done), fontSize = 20.sp)
        }
    }

    deleting?.let { (p, uses) ->
        ConfirmDeleteDialog(
            what = p.name,
            detail = if (uses > 0) stringResource(R.string.confirm_delete_profile_used, uses) else null,
            onConfirm = { scope.launch { repo.deleteProfile(p) }; deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

/** A built-in profile as a starting point for an editable copy: its skills light to heavy. */
fun Profile.asEditable(name: String): CustomProfileEntity {
    val skills = Skills.lightToHeavy(challenges.filter { it in Skills.all }.distinct()) { it }
        .map { Skills.defaults(it, intensity) }
        .ifEmpty { Skills.suggestedPlan(intensity) }
    return CustomProfileEntity(
        name = name, intensity = intensity.name, maxSnoozes = snooze.maxSnoozes, snoozeMinutes = snooze.snoozeMinutes,
        shrinkMinutes = snooze.shrinkMinutes, crescendoSeconds = crescendoSeconds, vibrate = vibrate,
        skills = SkillConfig.encodeList(skills), mode = SkillMode.CHAIN.name,
    )
}

/** Edits a user profile: settings, then the skills in the order they run. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditScreen(
    initial: CustomProfileEntity,
    repo: ProfileRepository,
    onNeedPermission: (List<ChallengeType>) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val presets by repo.presets.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(initial.name) }
    var intensity by remember { mutableStateOf(Intensity.entries.firstOrNull { it.name == initial.intensity } ?: Intensity.BALANCED) }
    var maxSnoozes by remember { mutableIntStateOf(initial.maxSnoozes) }
    var snoozeMinutes by remember { mutableIntStateOf(initial.snoozeMinutes) }
    var shrink by remember { mutableIntStateOf(initial.shrinkMinutes) }
    var crescendo by remember { mutableIntStateOf(initial.crescendoSeconds) }
    var vibrate by remember { mutableStateOf(initial.vibrate) }
    var mode by remember { mutableStateOf(SkillMode.entries.firstOrNull { it.name == initial.mode } ?: SkillMode.CHAIN) }
    var skills by remember { mutableStateOf(SkillConfig.decodeList(initial.skills)) }

    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var removing by remember { mutableStateOf<Int?>(null) }
    var confirmPlan by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Int?>(null) }
    var practicing by remember { mutableStateOf<List<Challenge>?>(null) }

    fun move(i: Int, by: Int) {
        val j = i + by
        if (j !in skills.indices) return
        skills = skills.toMutableList().also { l -> l[i] = l[j].also { l[j] = l[i] } }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding().padding(20.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(if (initial.id == 0L) R.string.profile_new else R.string.profile_edit), fontSize = 28.sp)
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profile_name)) },
                    placeholder = { Text(stringResource(R.string.profile_name_hint)) },
                    singleLine = true, isError = name.isBlank(), modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.profile_strength), fontSize = 18.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Intensity.entries.forEach { level ->
                        FilterChip(
                            selected = intensity == level, onClick = { intensity = level },
                            label = { Text(stringResource(intensityName(level)), fontSize = 16.sp) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }

                // The skills, in the order they run.
                Text(stringResource(R.string.profile_skills), fontSize = 20.sp)
                Text(stringResource(R.string.profile_skills_explain), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (skills.isEmpty()) {
                    Text(stringResource(R.string.profile_no_skills), fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                }
                skills.forEachIndexed { i, s ->
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                            .clickable { editing = i }.padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}.", fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                            Text(skillTitle(s), fontSize = 17.sp)
                            Text(skillSummary(s), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { move(i, -1) }, enabled = i > 0) { Text("▲") }
                        TextButton(onClick = { move(i, 1) }, enabled = i < skills.lastIndex) { Text("▼") }
                        TextButton(onClick = { removing = i }) { Text("✕") }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { adding = true }) { Text("＋ " + stringResource(R.string.profile_add_skill)) }
                    OutlinedButton(onClick = { if (skills.isEmpty()) skills = Skills.suggestedPlan(intensity) else confirmPlan = true }) {
                        Text("💡 " + stringResource(R.string.profile_suggest))
                    }
                    OutlinedButton(onClick = { skills = Skills.lightToHeavy(skills) { it.type } }, enabled = skills.size > 1) {
                        Text("↕ " + stringResource(R.string.profile_sort))
                    }
                    OutlinedButton(
                        onClick = {
                            onNeedPermission(skills.map { it.type })
                            val g = ChallengeGenerator()
                            practicing = skills.map { g.fromConfig(it, emptyList()) }
                        },
                        enabled = skills.isNotEmpty(),
                    ) { Text("▶ " + stringResource(R.string.profile_try)) }
                }

                Text(stringResource(R.string.profile_mode), fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == SkillMode.CHAIN, onClick = { mode = SkillMode.CHAIN },
                        label = { Text(stringResource(R.string.mode_chain)) }, modifier = Modifier.heightIn(min = 48.dp))
                    FilterChip(selected = mode == SkillMode.ESCALATE, onClick = { mode = SkillMode.ESCALATE },
                        label = { Text(stringResource(R.string.mode_escalate)) }, modifier = Modifier.heightIn(min = 48.dp))
                }
                Text(
                    stringResource(if (mode == SkillMode.CHAIN) R.string.mode_chain_explain else R.string.mode_escalate_explain),
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(stringResource(R.string.profile_snoozes, maxSnoozes), fontSize = 17.sp)
                IntSlider(maxSnoozes, 0, 5) { maxSnoozes = it }
                if (maxSnoozes > 0) {
                    Text(stringResource(R.string.profile_snooze_minutes, snoozeMinutes), fontSize = 17.sp)
                    IntSlider(snoozeMinutes, 1, 15) { snoozeMinutes = it }
                    Text(stringResource(R.string.profile_snooze_shrink, shrink), fontSize = 17.sp)
                    IntSlider(shrink, 0, 4) { shrink = it }
                }
                Text(stringResource(R.string.profile_crescendo, crescendo), fontSize = 17.sp)
                IntSlider(crescendo, 3, 120) { crescendo = it }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.profile_vibrate), fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }
            }

            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (initial.id != 0L) {
                    OutlinedButton(
                        onClick = { scope.launch { deleting = repo.alarmsUsing(initial) } },
                        modifier = Modifier.heightIn(min = BigTouch),
                    ) { Text(stringResource(R.string.delete)) }
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = BigTouch)) {
                    Text(stringResource(R.string.cancel), fontSize = 18.sp)
                }
                Button(
                    enabled = name.isNotBlank() && skills.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            repo.saveProfile(
                                initial.copy(
                                    name = name.trim(), intensity = intensity.name, maxSnoozes = maxSnoozes,
                                    snoozeMinutes = snoozeMinutes, shrinkMinutes = shrink, crescendoSeconds = crescendo,
                                    vibrate = vibrate, skills = SkillConfig.encodeList(skills), mode = mode.name,
                                )
                            )
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1.4f).heightIn(min = BigTouch),
                ) { Text(stringResource(R.string.save), fontSize = 20.sp) }
            }
        }
        practicing?.let { PracticeOverlay(it, onClose = { practicing = null }) }
    }

    if (adding) AlertDialog(
        onDismissRequest = { adding = false },
        title = { Text(stringResource(R.string.profile_add_skill)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Skills.all.forEach { type ->
                    TextButton(onClick = { skills = skills + Skills.defaults(type, intensity); adding = false }) {
                        Text(skillEmoji(type) + " " + stringResource(skillName(type)), fontSize = 17.sp, modifier = Modifier.fillMaxWidth())
                    }
                }
                val saved = presets.mapNotNull { p -> SkillConfig.decode(p.config)?.let { p.name to it } }
                if (saved.isNotEmpty()) {
                    Text(stringResource(R.string.profile_saved_skills), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    saved.forEach { (n, c) ->
                        TextButton(onClick = { skills = skills + c; adding = false }) {
                            Text("⭐ ${skillEmoji(c.type)} $n", fontSize = 17.sp, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } },
    )

    editing?.let { i ->
        var draft by remember(i) { mutableStateOf(skills[i]) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(skillTitle(draft)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { SkillParamsEditor(draft) { draft = it } } },
            confirmButton = {
                TextButton(onClick = { skills = skills.toMutableList().also { it[i] = draft }; editing = null }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    removing?.let { i ->
        ConfirmDeleteDialog(
            what = skillTitle(skills[i]),
            detail = stringResource(R.string.confirm_remove_skill),
            onConfirm = { skills = skills.filterIndexed { j, _ -> j != i }; removing = null },
            onDismiss = { removing = null },
        )
    }

    if (confirmPlan) AlertDialog(
        onDismissRequest = { confirmPlan = false },
        title = { Text(stringResource(R.string.profile_suggest)) },
        text = { Text(stringResource(R.string.profile_suggest_replace)) },
        confirmButton = {
            TextButton(onClick = { skills = Skills.suggestedPlan(intensity); confirmPlan = false }) {
                Text(stringResource(R.string.profile_suggest_use))
            }
        },
        dismissButton = { TextButton(onClick = { confirmPlan = false }) { Text(stringResource(R.string.cancel)) } },
    )

    deleting?.let { uses ->
        ConfirmDeleteDialog(
            what = initial.name,
            detail = if (uses > 0) stringResource(R.string.confirm_delete_profile_used, uses) else null,
            onConfirm = { scope.launch { repo.deleteProfile(initial); onBack() }; deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun IntSlider(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Slider(
        value = value.toFloat(), onValueChange = { onChange(it.roundToInt().coerceIn(min, max)) },
        valueRange = min.toFloat()..max.toFloat(), steps = (max - min - 1).coerceAtLeast(0),
    )
}
