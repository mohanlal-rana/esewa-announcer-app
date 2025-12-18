package com.client

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.os.Bundle
import android.util.Log
import java.util.Locale
import java.util.regex.Pattern

class NotificationService : NotificationListenerService() {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

override fun onCreate() {
    super.onCreate()
    // Use applicationContext instead of 'this' to ensure a stable context
    tts = TextToSpeech(applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
        }
    }
}

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isTtsReady) return

        val packageName = sbn.packageName
        if (packageName != "com.f1soft.esewa") return

        val extras = sbn.notification.extras ?: return
        var message = extras.getCharSequence("android.text")?.toString() ?: ""

        val lines = extras.getCharSequenceArray("android.textLines")
        if (!lines.isNullOrEmpty()) {
            message = lines.joinToString(" ") { it.toString() }
        }

        if (!message.contains("received", true) && !message.contains("credited", true)) return

        val pattern = Pattern.compile("(\\d{1,3}(?:,\\d{3})*|\\d+)(?:[.,]\\d+)?")
        val matcher = pattern.matcher(message)

        val amount = if (matcher.find()) {
            try {
                matcher.group(1)?.replace(",", "")?.toLong() ?: 0L
            } catch (_: NumberFormatException) {
                0L
            }
        } else 0L

        val speakText = if (amount > 0) "You received $amount rupees" else "You received zero money in eSewa"

        try {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) // max volume
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "eSewa-${System.currentTimeMillis()}")
            }
            tts?.takeIf { !it.isSpeaking }?.speak(speakText, TextToSpeech.QUEUE_FLUSH, params, "eSewa-${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e("NotifDebug", "TTS speak failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
