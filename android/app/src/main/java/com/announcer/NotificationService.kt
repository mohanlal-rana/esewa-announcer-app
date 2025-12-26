package com.announcer

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*
import java.util.regex.Pattern

class NotificationService : NotificationListenerService() {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    // Variables for word-by-word gap
    private var wordsToSpeak: List<String> = emptyList()
    private var currentWordIndex = 0
    private val WORD_GAP_MS: Long = 300 // Adjust this for speed

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.9f)
                tts?.setPitch(1.05f)
                setupProgressListener()
                isTtsReady = true
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                // When one word finishes, trigger the next one after a delay
                handler.postDelayed({
                    currentWordIndex++
                    speakNextWord()
                }, WORD_GAP_MS)
            }

            override fun onError(id: String?) {
                Log.e("NotifDebug", "TTS Word Error")
            }
        })
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.f1soft.esewa" || !isTtsReady) return

        val extras = sbn.notification.extras ?: return
        var message = extras.getCharSequence("android.text")?.toString() ?: ""

        val lines = extras.getCharSequenceArray("android.textLines")
        if (!lines.isNullOrEmpty()) {
            message = lines.joinToString(" ") { it.toString() }
        }

        if (!message.contains("received", ignoreCase = true) &&
            !message.contains("credited", ignoreCase = true)
        ) return

        val pattern = Pattern.compile("(\\d{1,3}(?:,\\d{3})*|\\d+)(?:[.,]\\d+)?")
        val matcher = pattern.matcher(message)
        val amount = if (matcher.find()) {
            try { matcher.group(1)?.replace(",", "")?.toLong() ?: 0L } catch (_: Exception) { 0L }
        } else 0L

        val fullText = if (amount > 0) "$amount rupees received" else "Money received in eSewa"

        // Prepare the word list for the gap logic
        wordsToSpeak = fullText.split("\\s+".toRegex())
        currentWordIndex = 0

        playCustomBell()
    }

    private fun playCustomBell() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(applicationContext, R.raw.bell)
            mediaPlayer?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mediaPlayer?.setOnCompletionListener {
                it.release()
                mediaPlayer = null
                handler.postDelayed({ speakNextWord() }, 300)
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            speakNextWord()
        }
    }

    private fun speakNextWord() {
        if (currentWordIndex >= wordsToSpeak.size) return

        val word = wordsToSpeak[currentWordIndex]
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        
        // Using a unique ID triggers the UtteranceProgressListener onDone
        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, params, "word_${currentWordIndex}")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        mediaPlayer?.release()
        super.onDestroy()
    }
}