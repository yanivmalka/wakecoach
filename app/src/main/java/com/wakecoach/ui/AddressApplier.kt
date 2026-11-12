package com.wakecoach.ui

import android.app.GrammaticalInflectionManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.wakecoach.core.message.Address

/**
 * Hebrew screen text follows the chosen form of address through Android's grammatical gender
 * (resources in values-iw-masculine / values-iw-feminine, neutral in values-iw). The system keeps
 * the choice across restarts. Android 13 and older lack it: there only spoken lines adapt.
 */
object AddressApplier {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 34

    fun apply(context: Context, address: Address) {
        if (Build.VERSION.SDK_INT < 34) return
        val gender = when (address) {
            Address.MALE -> Configuration.GRAMMATICAL_GENDER_MASCULINE
            Address.FEMALE -> Configuration.GRAMMATICAL_GENDER_FEMININE
            Address.UNSET -> Configuration.GRAMMATICAL_GENDER_NOT_SPECIFIED
        }
        val gim = context.getSystemService(GrammaticalInflectionManager::class.java) ?: return
        // Changing it recreates the activity; skip when nothing changes.
        if (gim.applicationGrammaticalGender != gender) gim.setRequestedApplicationGrammaticalGender(gender)
    }
}
