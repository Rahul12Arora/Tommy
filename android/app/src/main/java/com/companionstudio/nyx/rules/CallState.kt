package com.companionstudio.nyx.rules

import android.annotation.SuppressLint
import android.media.AudioManager

/** Whether a phone or VoIP call is ringing or going on; the break screen never covers one (D-89). */
fun interface CallState {
    fun inCall(): Boolean
}

/**
 * From the audio mode, which needs no permission, unlike `TelephonyManager.getCallState` (D-99). Calling apps such as
 * WhatsApp set the communication mode, so their calls count too.
 */
class AudioCallState(private val audioManager: AudioManager) : CallState {
    override fun inCall(): Boolean = audioManager.mode in CALL_MODES

    private companion object {
        // The redirect modes are API 33 constants, copied into the app at build time. Older phones never report them,
        // so listing them there is harmless
        @SuppressLint("InlinedApi")
        val CALL_MODES = setOf(
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_CALL_SCREENING,
            AudioManager.MODE_CALL_REDIRECT,
            AudioManager.MODE_COMMUNICATION_REDIRECT,
        )
    }
}
