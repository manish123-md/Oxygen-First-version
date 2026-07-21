package com.oxygen.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Hamesha background mein chalne wali service.
 *
 * Kaam:
 *  1. SpeechRecognizer ko baar baar restart karke "continuous listening" simulate karta hai
 *     (Android ka SpeechRecognizer khud continuous nahi hota, isliye restart-loop use kiya hai).
 *  2. Jab bhi "hey oxygen" sunayi de -> ASSISTANT ko ACTIVE mode mein daal deta hai,
 *     OverlayService (Dynamic Island) dikhata hai, aur agla jo bola jaaye use command maan kar
 *     CommandProcessor ko bhej deta hai.
 *  3. Jab "sleep oxygen" sunayi de -> ACTIVE mode band karke wapas sirf wake-word sunta rehta hai.
 *
 * NOTE: Ye SpeechRecognizer wala approach simple hai lekin thoda battery-heavy hai aur
 * restart ke beech ek chhota gap (~0.5-1 sec) hota hai. Production ke liye Vosk / Porcupine
 * jaisा offline wake-word engine use karna better hoga (README mein likha hai).
 */
class WakeWordService : Service(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isActiveMode = false
    private lateinit var tts: TextToSpeech
    private val handler = Handler(Looper.getMainLooper())

    private val wakeWords = listOf("hey oxygen", "he oxygen", "hey oxygen.", "oxygen")
    private val sleepWords = listOf("sleep oxygen", "slip oxygen")

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { }
        startForeground(NOTIF_ID, buildNotification("Oxygen sun raha hai..."))
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // system dwara kill hone par phir se start ho jaaye
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- Listening loop ----------

    private fun startListening() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(this@WakeWordService)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        }
        speechRecognizer?.startListening(intent)
    }

    /** Recognizer khud-b-khud ruk jaata hai (timeout/error), to use turant restart karo. */
    private fun restartListening() {
        handler.postDelayed({ startListening() }, 300)
    }

    override fun onResults(results: android.os.Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""
        handleRecognizedText(text)
        restartListening()
    }

    override fun onPartialResults(partialResults: android.os.Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""
        if (!isActiveMode && wakeWords.any { text.contains(it) }) {
            // Wake word turant partial result mein hi pakad lo, poora sentence khatam hone ka
            // intezaar mat karo - isse response fast lagta hai.
            activateAssistant()
        }
    }

    private fun handleRecognizedText(text: String) {
        if (text.isBlank()) return

        if (!isActiveMode) {
            if (wakeWords.any { text.contains(it) }) {
                activateAssistant()
            }
            return
        }

        // Active mode mein hai
        if (sleepWords.any { text.contains(it) }) {
            deactivateAssistant()
            return
        }

        // Ye command hai - process karo
        OverlayService.showThinking(this)
        CommandProcessor.process(this, text) { spokenReply ->
            speak(spokenReply)
        }
    }

    private fun activateAssistant() {
        isActiveMode = true
        OverlayService.showListening(this)
        val greetings = listOf(
            "Haan bataiye, main sun rahi hoon",
            "Ji boliye",
            "Oxygen ready hai, kahiye"
        )
        speak(greetings.random())
        updateNotification("Oxygen active hai - sun raha hai")
    }

    private fun deactivateAssistant() {
        isActiveMode = false
        OverlayService.hide(this)
        speak("Theek hai, sone ja rahi hoon. Hey Oxygen bolke phir bulana.")
        updateNotification("Oxygen sun raha hai...")
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "OXY_UTT")
    }

    // ---------- Notification (Android is service ko background rakhne ke liye ye maangta hai) ----------

    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Oxygen Assistant", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Oxygen")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    // Baaki RecognitionListener callbacks (kuch mein bas restart karna hai)
    override fun onError(error: Int) = restartListening()
    override fun onEndOfSpeech() { /* no-op, onResults/onError already restart karte hain */ }
    override fun onReadyForSpeech(params: android.os.Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "oxygen_channel"
        private const val NOTIF_ID = 1
    }
}
