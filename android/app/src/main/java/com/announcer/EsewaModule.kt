package com.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.util.Locale

class EsewaModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var isTtsInitialized = false

    override fun getName(): String = "EsewaModule"

    init {
        try {
            tts = TextToSpeech(reactContext, this)
            audioManager = reactContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        } catch (e: Exception) {
            Log.e("EsewaModule", "TTS init failed: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.ENGLISH
            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.1f)
            isTtsInitialized = true
        } else {
            Log.e("EsewaModule", "TTS Initialization Failed!")
        }
    }

    // Audio Focus
    private fun requestAudioFocus() {
        audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build()
            audioManager!!.requestAudioFocus(focusRequest!!)
        } else {
            audioManager!!.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonAudioFocus() {
        audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager!!.abandonAudioFocusRequest(focusRequest!!)
        } else {
            audioManager!!.abandonAudioFocus(null)
        }
    }

    // Speak with volume control
    private fun speak(message: String, volume: Float = 1.0f) {
        val engine = tts ?: return
        if (!isTtsInitialized) return

        requestAudioFocus()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { abandonAudioFocus() }
            override fun onError(utteranceId: String?) { abandonAudioFocus() }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "ESEWA_TTS")
            }
            engine.speak(message, TextToSpeech.QUEUE_FLUSH, params, "ESEWA_TTS")
        } else {
            val params = HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_VOLUME] = volume.toString()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "ESEWA_TTS"
            @Suppress("DEPRECATION")
            engine.speak(message, TextToSpeech.QUEUE_FLUSH, params)
        }
    }

    @ReactMethod
    fun testVoice() {
        speak("eSewa announcement enabled", 1.0f) // max volume
    }

    @ReactMethod
    fun speakText(message: String, volume: Double = 1.0) {
        speak(message, volume.toFloat())
    }

    // Open notification settings
    @ReactMethod
    fun openNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            reactApplicationContext.startActivity(intent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            reactApplicationContext.startActivity(fallback)
        }
    }

    // Check notification access
@ReactMethod
fun isNotificationServiceEnabled(promise: Promise) {
    try {
        val contentResolver = reactApplicationContext.contentResolver
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val packageName = reactApplicationContext.packageName
        
        // This check is safer and accounts for null strings
        val enabled = enabledListeners?.contains(packageName) == true
        promise.resolve(enabled)
    } catch (e: Exception) {
        promise.reject("ERROR", e.message)
    }
}
}
