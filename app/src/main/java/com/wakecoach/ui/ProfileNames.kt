package com.wakecoach.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.wakecoach.R
import com.wakecoach.container
import com.wakecoach.data.CustomProfileEntity

@StringRes
fun profileName(id: String): Int = when (id) {
    "gentle" -> R.string.profile_gentle
    "firm" -> R.string.profile_firm
    "soldier" -> R.string.profile_soldier
    "heavy_sleeper" -> R.string.profile_heavy_sleeper
    else -> R.string.profile_balanced
}

@StringRes
fun profileDescription(id: String): Int = when (id) {
    "gentle" -> R.string.profile_gentle_desc
    "firm" -> R.string.profile_firm_desc
    "soldier" -> R.string.profile_soldier_desc
    "heavy_sleeper" -> R.string.profile_heavy_sleeper_desc
    else -> R.string.profile_balanced_desc
}

/** A profile's display name: built-in names are translated, custom ones are the user's own. */
@Composable
fun profileLabel(id: String): String {
    val customId = CustomProfileEntity.idOf(id) ?: return stringResource(profileName(id))
    val custom by LocalContext.current.container.profiles.custom.collectAsState()
    return custom.firstOrNull { it.id == customId }?.name ?: stringResource(R.string.profile_balanced)
}
