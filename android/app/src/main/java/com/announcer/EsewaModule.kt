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
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.util.*
import java.util.regex.Pattern

class EsewaModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var bellPlayer: MediaPlayer? = null
    private var isTtsInitialized = false

    private val handler = Handler(Looper.getMainLooper())
    private var wordsList: List<String> = emptyList()
    private var currentIndex = 0
    private var gapMs: Long = 350
    private var volume: Float = 1.0f

    override fun getName(): String = "EsewaModule"

    init {
        tts = TextToSpeech(reactContext, this)
        audioManager = reactContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(0.80f)
            isTtsInitialized = true
        }
    }

    @ReactMethod
    fun speakTextWithBell(message: String, volumeVal: Double = 1.0, gap: Int = 350) {
        val pattern = Pattern.compile("\\d+")
        val matcher = pattern.matcher(message)
        val sb = StringBuffer()
        
        while (matcher.find()) {
            val num = matcher.group().toLong()
            matcher.appendReplacement(sb, numberToWords(num))
        }
        matcher.appendTail(sb)

        this.volume = volumeVal.toFloat()
        this.gapMs = gap.toLong()
        this.wordsList = sb.toString().split(" ").filter { it.isNotBlank() }
        this.currentIndex = 0

        playBellThenSpeak()
    }

    private fun playBellThenSpeak() {
        bellPlayer?.release()
        bellPlayer = MediaPlayer.create(reactApplicationContext, R.raw.bell)
        bellPlayer?.setOnCompletionListener {
            it.release()
            handler.postDelayed({ speakNext() }, 200)
        }
        bellPlayer?.start()
    }

    private fun speakNext() {
        if (currentIndex >= wordsList.size) return
        
        val word = wordsList[currentIndex]
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                handler.postDelayed({
                    currentIndex++
                    speakNext()
                }, gapMs)
            }
            override fun onError(id: String?) {}
        })

        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, params, "test_$currentIndex")
    }

    private fun numberToWords(num: Long): String {
        if (num == 0L) return "zero"
        val units = arrayOf("", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", 
                           "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
        val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

        fun helper(n: Long): String {
            return when {
                n < 20 -> units[n.toInt()]
                n < 100 -> tens[(n / 10).toInt()] + (if (n % 10 != 0L) " " + units[(n % 10).toInt()] else "")
                n < 1000 -> units[(n / 100).toInt()] + " hundred" + (if (n % 100 != 0L) " " + helper(n % 100) else "")
                n < 100000 -> helper(n / 1000) + " thousand" + (if (n % 1000 != 0L) " " + helper(n % 1000) else "")
                n < 10000000 -> helper(n / 100000) + " lakh" + (if (n % 100000 != 0L) " " + helper(n % 100000) else "")
                else -> helper(n / 10000000) + " crore" + (if (n % 10000000 != 0L) " " + helper(n % 10000000) else "")
            }
        }
        return helper(num).trim().replace("\\s+".toRegex(), " ")
    }

    @ReactMethod
    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        reactApplicationContext.startActivity(intent)
    }

    @ReactMethod
    fun isNotificationServiceEnabled(promise: Promise) {
        val enabled = Settings.Secure.getString(reactApplicationContext.contentResolver, "enabled_notification_listeners")
        promise.resolve(enabled?.contains(reactApplicationContext.packageName) == true)
    }
}