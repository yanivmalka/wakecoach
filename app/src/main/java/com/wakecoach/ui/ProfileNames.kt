package com.wakecoach.ui

import androidx.annotation.StringRes
import com.wakecoach.R

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
