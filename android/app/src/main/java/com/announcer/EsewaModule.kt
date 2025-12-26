package com.announcer

import android.content.Context
import android.content.Intent
import android.media.*
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.util.*

class EsewaModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var bellPlayer: MediaPlayer? = null
    private var isTtsInitialized = false

    private val handler = Handler(Looper.getMainLooper())
    private var words: List<String> = emptyList()
    private var currentIndex = 0
    private var gapMs: Long = 300
    private var volume: Float = 1.0f

    override fun getName(): String = "EsewaModule"

    init {
        tts = TextToSpeech(reactContext, this)
        audioManager =
            reactContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.ENGLISH
            tts?.setSpeechRate(0.9f)
            tts?.setPitch(1f)
            isTtsInitialized = true
        }
    }

    // ---------- AUDIO FOCUS ----------
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            focusRequest = AudioFocusRequest.Builder(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ).setAudioAttributes(attributes).build()

            audioManager?.requestAudioFocus(focusRequest!!)
        } else {
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager?.abandonAudioFocusRequest(focusRequest!!)
        } else {
            audioManager?.abandonAudioFocus(null)
        }
    }

    // ---------- WORD-BY-WORD SPEECH ----------
    private fun speakNextWord() {
        if (currentIndex >= words.size) {
            abandonAudioFocus()
            return
        }

        val word = words[currentIndex]
        val utteranceId = UUID.randomUUID().toString()

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}

            override fun onDone(id: String?) {
                handler.postDelayed({
                    currentIndex++
                    speakNextWord()
                }, gapMs)
            }

            override fun onError(id: String?) {
                abandonAudioFocus()
            }
        })

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }

        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    private fun speakWithGap(text: String, vol: Float, gap: Long) {
        if (!isTtsInitialized || text.isBlank()) return

        requestAudioFocus()

        words = text.split("\\s+".toRegex())
        currentIndex = 0
        volume = vol
        gapMs = gap

        speakNextWord()
    }

    // ---------- 🔔 BELL + SPEAK ----------
    private fun playBellThenSpeak(text: String, vol: Float, gap: Long) {
        requestAudioFocus()

        bellPlayer?.release()
        bellPlayer = MediaPlayer.create(reactApplicationContext, R.raw.bell)

        bellPlayer?.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )

        bellPlayer?.setOnCompletionListener {
            it.release()
            bellPlayer = null

            handler.postDelayed({
                speakWithGap(text, vol, gap)
            }, 150)
        }

        bellPlayer?.start()
    }

    // ---------- REACT METHODS ----------
    @ReactMethod
    fun speakTextWithBell(message: String, volume: Double = 1.0, gapMs: Int = 300) {
        playBellThenSpeak(message, volume.toFloat(), gapMs.toLong())
    }

    @ReactMethod
    fun testVoice() {
        playBellThenSpeak("20 rupees received", 1.0f, 300)
    }

    @ReactMethod
    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        reactApplicationContext.startActivity(intent)
    }

    @ReactMethod
    fun isNotificationServiceEnabled(promise: Promise) {
        val enabled = Settings.Secure.getString(
            reactApplicationContext.contentResolver,
            "enabled_notification_listeners"
        )
        promise.resolve(enabled?.contains(reactApplicationContext.packageName) == true)
    }
}
