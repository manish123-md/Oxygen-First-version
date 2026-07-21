package com.oxygen.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import java.util.Calendar
import java.util.Locale

/**
 * User ne jo bola use samajhkar sahi action perform karta hai, aur ek
 * spoken reply wapas deta hai (jo TTS bolega). Har real command se pehle
 * ek chhota joke bhi sunata hai jaisa maanga gaya tha.
 */
object CommandProcessor {

    private val jokes = listOf(
        "Ek minute... pehle ek joke: Computer ko thand kyun lagti hai? Kyunki uski windows khuli rehti hain!",
        "Suniye ek joke: Teacher ne poocha 'Newton ka teesra niyam bolo', bachcha bola 'Sir Google down hai'.",
        "Ek chhota joke pehle: WiFi aur relationship mein kya common hai? Dono bina password ke connect nahi hote.",
        "Joke time: Mera dost bola code likhna easy hai, maine kaha bug fix karke dikhao... ab wo dost nahi hai."
    )

    fun process(context: Context, spokenText: String, onReply: (String) -> Unit) {
        val text = spokenText.lowercase(Locale.getDefault())
        val joke = jokes.random()

        when {
            text.contains("torch") || text.contains("flash") || text.contains("light on") -> {
                setTorch(context, true)
                onReply("$joke ... Torch on kar diya!")
            }
            text.contains("torch off") || text.contains("light off") -> {
                setTorch(context, false)
                onReply("$joke ... Torch band kar diya!")
            }
            text.contains("data on") || text.contains("mobile data") -> {
                openSettingsPanel(context, Settings.ACTION_DATA_ROAMING_SETTINGS)
                onReply("$joke ... Data settings khol raha hoon, wahan se on kar dijiye - Android security ki wajah se app khud data on nahi kar sakta.")
            }
            text.contains("time") || text.contains("samay") -> {
                val now = Calendar.getInstance().time
                val formatted = DateFormat.format("hh:mm a", now)
                onReply("$joke ... Abhi time hai $formatted")
            }
            text.contains("reminder") || text.contains("yaad dila") -> {
                onReply("$joke ... Reminder ke liye mujhe batao kitne minute baad, aur main set kar dungi.")
                // Real reminder scheduling ke liye AlarmManager ka example neeche hai:
                // scheduleReminder(context, minutes = 10, message = "Aapka reminder")
            }
            text.contains("open ") -> {
                val appName = text.substringAfter("open ").trim()
                val opened = openApp(context, appName)
                onReply(
                    if (opened) "$joke ... $appName khol rahi hoon"
                    else "$joke ... Mujhe $appName naam ki app nahi mili"
                )
            }
            text.contains("search") || text.contains("khoj") || text.contains("dhundo") -> {
                val query = text.substringAfter("search").substringAfter("for").trim()
                SearchHelper.search(query.ifBlank { text }) { answer ->
                    onReply("$joke ... $answer")
                }
            }
            else -> {
                // Kuch samajh nahi aaya to bhi internet search try kar lo (open-ended Q&A)
                SearchHelper.search(text) { answer ->
                    onReply(answer)
                }
            }
        }
    }

    private fun setTorch(context: Context, on: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            cameraManager.setTorchMode(cameraId, on)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openApp(context: Context, appNameSpoken: String): Boolean {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(0)
        val target = installedApps.firstOrNull { app ->
            pm.getApplicationLabel(app).toString().lowercase(Locale.getDefault())
                .contains(appNameSpoken.lowercase(Locale.getDefault()))
        } ?: return false

        val launchIntent = pm.getLaunchIntentForPackage(target.packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    private fun openSettingsPanel(context: Context, action: String) {
        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    // Example helper agar aap AlarmManager se real reminder banana chahein
    private fun scheduleReminder(context: Context, minutes: Int, message: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("message", message)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getBroadcast(context, minutes, intent, flags)
        am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }
}
