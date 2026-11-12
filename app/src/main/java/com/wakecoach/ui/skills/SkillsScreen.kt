package com.wakecoach.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wakecoach.R
import com.wakecoach.core.challenge.Challenge
import com.wakecoach.core.challenge.ChallengeGenerator
import com.wakecoach.core.model.ChallengeType
import com.wakecoach.core.model.Intensity
import com.wakecoach.core.skill.SkillConfig
import com.wakecoach.core.skill.Skills
import com.wakecoach.data.ProfileRepository
import com.wakecoach.data.SkillPresetEntity
import com.wakecoach.ui.components.ConfirmDeleteDialog
import com.wakecoach.ui.ringing.ChallengeChain
import com.wakecoach.ui.theme.BigTouch
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The practice area: every wake-up skill, light to heavy, with the user's saved versions under
 * each. Tap a skill to try it and tune it; saved versions can go into profiles.
 */
@Composable
fun SkillsScreen(repo: ProfileRepository, onOpen: (ChallengeType, Long?) -> Unit, onBack: () -> Unit) {
    val presets by repo.presets.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf<SkillPresetEntity?>(null) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(20.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.skills_title), fontSize = 28.sp)
            Text(stringResource(R.string.skills_explain), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Skills.all.forEach { type ->
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
                        .clickable { onOpen(type, null) }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(skillEmoji(type) + " " + stringResource(skillName(type)), fontSize = 20.sp)
                    Text(stringResource(skillDescription(type)), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    presets.mapNotNull { p -> SkillConfig.decode(p.config)?.takeIf { it.type == type }?.let { p to it } }
                        .forEach { (p, config) ->
                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .clickable { onOpen(type, p.id) }.padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text("⭐ " + p.name, fontSize = 17.sp)
                                    Text(skillSummary(config), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { deleting = p }) { Text("🗑", fontSize = 18.sp) }
                            }
                        }
                }
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = BigTouch)) {
            Text(stringResource(R.string.done), fontSize = 20.sp)
        }
    }

    deleting?.let { p ->
        ConfirmDeleteDialog(
            what = p.name,
            detail = stringResource(R.string.confirm_delete_preset),
            onConfirm = { scope.launch { repo.deletePreset(p) }; deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

/** Sliders and switches for each setting of a skill. */
@Composable
fun SkillParamsEditor(config: SkillConfig, onChange: (SkillConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Skills.specs(config.type).forEach { spec ->
            val v = config[spec.param]
            val label = stringResource(paramName(config.type, spec.param))
            if (spec.min == 0 && spec.max == 1) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Switch(checked = v == 1, onCheckedChange = { onChange(config.with(spec.param, if (it) 1 else 0)) })
                }
            } else {
                Text("$label: " + paramValue(spec.param, v), fontSize = 17.sp)
                Slider(
                    value = v.toFloat(),
                    onValueChange = { f ->
                        val stepped = spec.min + ((f - spec.min) / spec.step).roundToInt() * spec.step
                        onChange(config.with(spec.param, spec.clamp(stepped)))
                    },
                    valueRange = spec.min.toFloat()..spec.max.toFloat(),
                    steps = ((spec.max - spec.min) / spec.step - 1).coerceAtLeast(0),
                )
            }
        }
    }
}

/**
 * Try a skill and tune it. [preset] edits a saved version; otherwise starts from the defaults
 * for [type]. Saving asks for a name.
 */
@Composable
fun SkillEditScreen(
    type: ChallengeType,
    preset: SkillPresetEntity?,
    repo: ProfileRepository,
    onNeedPermission: (List<ChallengeType>) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var config by remember(preset) {
        mutableStateOf(preset?.let { SkillConfig.decode(it.config) } ?: Skills.defaults(type, Intensity.BALANCED))
    }
    var name by remember(preset) { mutableStateOf(preset?.name.orEmpty()) }
    var practicing by remember { mutableStateOf<List<Challenge>?>(null) }
    var deleting by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding().padding(20.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(skillEmoji(type) + " " + stringResource(skillName(type)), fontSize = 28.sp)
                Text(stringResource(skillDescription(type)), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.skill_level_from), fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Intensity.entries.forEach { level ->
                        FilterChip(
                            selected = false, onClick = { config = Skills.defaults(type, level) },
                            label = { Text(stringResource(intensityName(level)), fontSize = 14.sp) },
                        )
                    }
                }
                SkillParamsEditor(config) { config = it }
                OutlinedButton(
                    onClick = {
                        onNeedPermission(listOf(type))
                        practicing = listOf(ChallengeGenerator().fromConfig(config, emptyList()))
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) { Text("▶ " + stringResource(R.string.skill_practice), fontSize = 18.sp) }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.skill_save_name)) },
                    placeholder = { Text(stringResource(R.string.skill_save_name_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (preset != null) {
                    OutlinedButton(onClick = { deleting = true }, modifier = Modifier.heightIn(min = BigTouch)) {
                        Text(stringResource(R.string.delete))
                    }
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = BigTouch)) {
                    Text(stringResource(R.string.cancel), fontSize = 18.sp)
                }
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            repo.savePreset(
                                SkillPresetEntity(
                                    id = preset?.id ?: 0, name = name.trim(), config = config.encode(),
                                    createdAt = preset?.createdAt ?: System.currentTimeMillis(),
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

    if (deleting && preset != null) {
        ConfirmDeleteDialog(
            what = preset.name,
            detail = stringResource(R.string.confirm_delete_preset),
            onConfirm = { scope.launch { repo.deletePreset(preset); onBack() }; deleting = false },
            onDismiss = { deleting = false },
        )
    }
}

/** Runs [chain] like a real morning, without sound, and shows how long it took. */
@Composable
fun PracticeOverlay(chain: List<Challenge>, onClose: () -> Unit) {
    val started = remember(chain) { System.currentTimeMillis() }
    var doneIn by remember(chain) { mutableLongStateOf(-1L) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🎯 " + stringResource(R.string.practice_title), fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(stringResource(R.string.practice_exit), fontSize = 17.sp) }
        }
        Spacer(Modifier.weight(0.3f))
        if (doneIn >= 0) {
            Text("🎉", fontSize = 64.sp)
            Text(
                stringResource(R.string.practice_done, (doneIn / 1000).toInt()), fontSize = 24.sp, textAlign = TextAlign.Center,
            )
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = BigTouch)) {
                Text(stringResource(R.string.done), fontSize = 20.sp)
            }
        } else {
            val solved = { doneIn = System.currentTimeMillis() - started }
            if (chain.all { it is Challenge.None }) LaunchedEffect(Unit) { solved() }
            else ChallengeChain(chain, onSolved = solved)
        }
        Spacer(Modifier.weight(1f))
    }
}

@androidx.annotation.StringRes
fun intensityName(level: Intensity): Int = when (level) {
    Intensity.GENTLE -> R.string.intensity_gentle
    Intensity.BALANCED -> R.string.intensity_balanced
    Intensity.FIRM -> R.string.intensity_firm
    Intensity.HARDCORE -> R.string.intensity_hardcore
}

/** Helper for screens that show "skill name · summary" for a config. */
@Composable
fun skillTitle(config: SkillConfig): String = skillEmoji(config.type) + " " + stringResource(skillName(config.type))

