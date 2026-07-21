package com.oxygen.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech

/** Jab reminder ka time ho jaaye, Oxygen isko bol kar sunata hai. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: "Aapka reminder"
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "REMINDER")
        }
    }
}
