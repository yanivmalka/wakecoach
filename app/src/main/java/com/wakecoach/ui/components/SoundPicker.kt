package com.wakecoach.ui.components

import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import com.wakecoach.R
import com.wakecoach.audio.AlarmPlayer
import com.wakecoach.core.audio.BuiltinSound
import com.wakecoach.core.audio.SoundRef

/** "Sound: Automatic ▸" row; opens a list of built-in sounds, automatic, or any sound on the phone. */
@Composable
fun SoundPickerRow(stored: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val current = SoundRef.decode(stored)
    val name = when (current) {
        null -> stringResource(R.string.sound_auto)
        is SoundRef.Builtin -> stringResource(builtinName(current.sound))
        is SoundRef.Custom -> remember(current.uri) {
            runCatching { RingtoneManager.getRingtone(context, Uri.parse(current.uri))?.getTitle(context) }.getOrNull()
        } ?: stringResource(R.string.sound_phone)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable { open = true },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎵 " + stringResource(R.string.sound), fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(name, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (open) SoundDialog(stored, onPick = { onChange(it); open = false }, onDismiss = { open = false })
}

@Composable
private fun SoundDialog(stored: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<Ringtone?>(null) }
    // Tapping an option previews it; "Done" saves the choice.
    var chosen by remember { mutableStateOf(stored) }
    DisposableEffect(Unit) { onDispose { preview?.stop() } }

    fun play(uri: Uri?) {
        preview?.stop()
        preview = uri?.let { u ->
            RingtoneManager.getRingtone(context, u)?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                play()
            }
        }
    }

    val phonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            if (uri != null) onPick(SoundRef.Custom(uri.toString()).encode())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sound)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Option(stringResource(R.string.sound_auto), stringResource(R.string.sound_auto_desc), selected = chosen == null) {
                    preview?.stop(); chosen = null
                }
                listOf(BuiltinSound.CHIME_SOFT, BuiltinSound.BIRDS_MORNING, BuiltinSound.RISE_BRIGHT, BuiltinSound.URGENT_BEEP)
                    .forEach { sound ->
                        val ref = SoundRef.Builtin(sound).encode()
                        Option(stringResource(builtinName(sound)), null, selected = chosen == ref) {
                            chosen = ref
                            play(AlarmPlayer.rawUri(context, sound))
                        }
                    }
                Option(stringResource(R.string.sound_phone), stringResource(R.string.sound_phone_desc), selected = SoundRef.decode(chosen) is SoundRef.Custom) {
                    preview?.stop()
                    phonePicker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (SoundRef.decode(stored) as? SoundRef.Custom)?.let { Uri.parse(it.uri) })
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { preview?.stop(); onPick(chosen) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { preview?.stop(); onDismiss() }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Option(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun builtinName(sound: BuiltinSound): Int = when (sound) {
    BuiltinSound.CHIME_SOFT -> R.string.sound_chime
    BuiltinSound.BIRDS_MORNING -> R.string.sound_birds
    BuiltinSound.RISE_BRIGHT -> R.string.sound_rise
    BuiltinSound.URGENT_BEEP -> R.string.sound_beep
    BuiltinSound.PHONE_RINGTONE -> R.string.sound_phone
}
