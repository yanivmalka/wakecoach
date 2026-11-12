package com.wakecoach.data

import com.wakecoach.core.model.Intensity
import com.wakecoach.core.model.Profile
import com.wakecoach.core.model.Profiles
import com.wakecoach.core.model.SnoozePolicy
import com.wakecoach.core.skill.SkillConfig
import com.wakecoach.core.skill.SkillMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Built-in profiles plus the user's own, and the skills saved in the practice area. */
class ProfileRepository(private val db: AppDatabase, scope: CoroutineScope) {
    private val dao = db.skills()

    val custom: StateFlow<List<CustomProfileEntity>> =
        dao.observeProfiles().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val presets: StateFlow<List<SkillPresetEntity>> =
        dao.observePresets().stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The profile an alarm rings with. A deleted custom profile falls back to balanced. */
    suspend fun resolve(profileId: String): Profile {
        val id = CustomProfileEntity.idOf(profileId) ?: return Profiles.byId(profileId)
        return dao.profile(id)?.toProfile() ?: Profiles.BALANCED
    }

    /** Same as [resolve] from what is already loaded; for the UI. */
    fun resolveLoaded(profileId: String): Profile {
        val id = CustomProfileEntity.idOf(profileId) ?: return Profiles.byId(profileId)
        return custom.value.firstOrNull { it.id == id }?.toProfile() ?: Profiles.BALANCED
    }

    suspend fun savePreset(p: SkillPresetEntity): Long = dao.savePreset(p)
    suspend fun deletePreset(p: SkillPresetEntity) = dao.deletePreset(p)
    suspend fun saveProfile(p: CustomProfileEntity): Long = dao.saveProfile(p)
    suspend fun alarmsUsing(p: CustomProfileEntity): Int = dao.alarmsUsing(p.profileId)

    /** Deletes [p]; alarms that used it move to the balanced profile instead of breaking. */
    suspend fun deleteProfile(p: CustomProfileEntity) {
        dao.reassignAlarms(p.profileId, Profiles.BALANCED.id)
        dao.deleteProfile(p)
    }
}

fun CustomProfileEntity.toProfile(): Profile {
    val skillList = SkillConfig.decodeList(skills)
    return Profile(
        id = profileId,
        intensity = Intensity.entries.firstOrNull { it.name == intensity } ?: Intensity.BALANCED,
        challenges = skillList.map { it.type },
        snooze = SnoozePolicy(maxSnoozes, snoozeMinutes, shrinkMinutes),
        crescendoSeconds = crescendoSeconds,
        vibrate = vibrate,
        skills = skillList,
        skillMode = SkillMode.entries.firstOrNull { it.name == mode } ?: SkillMode.CHAIN,
        customName = name,
    )
}
