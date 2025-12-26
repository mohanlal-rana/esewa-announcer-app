package com.announcer

import android.content.Context
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*
import java.util.regex.Pattern

class NotificationService : NotificationListenerService() {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private var wordsToSpeak: List<String> = emptyList()
    private var currentWordIndex = 0
    private val WORD_GAP_MS: Long = 350

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.80f)
                setupProgressListener()
                isTtsReady = true
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                handler.postDelayed({
                    currentWordIndex++
                    speakNextWord()
                }, WORD_GAP_MS)
            }

            override fun onError(id: String?) { abandonAudioFocus() }
        })
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.f1soft.esewa" || !isTtsReady) return

        val extras = sbn.notification.extras ?: return
        val message = extras.getCharSequence("android.text")?.toString() ?: ""

        if (!message.contains("received", true) && !message.contains("credited", true)) return

        // Extract amount
        val pattern = Pattern.compile("(\\d{1,3}(?:,\\d{3})*|\\d+)(?:[.,]\\d+)?")
        val matcher = pattern.matcher(message)
        val amountStr = if (matcher.find()) matcher.group(0)?.replace(",", "")?.split(".")?.get(0) ?: "" else ""

        val fullText = if (amountStr.isNotEmpty()) {
            "${numberToWords(amountStr.toLong())} rupees received"
        } else {
            "Money received in eSewa"
        }

        wordsToSpeak = fullText.split(" ").filter { it.isNotBlank() }
        currentWordIndex = 0

        requestAudioFocus()
        playBell()
    }

    private fun playBell() {
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
            handler.postDelayed({ speakNextWord() }, 200)
        }
        mediaPlayer?.start()
    }

    private fun speakNextWord() {
        if (currentWordIndex >= wordsToSpeak.size) {
            abandonAudioFocus()
            return
        }

        val word = wordsToSpeak[currentWordIndex]
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)

        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, params, "word_$currentWordIndex")
    }

    private fun numberToWords(num: Long): String {
        if (num == 0L) return "zero"

        val units = arrayOf(
            "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen"
        )
        val tens = arrayOf(
            "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
        )

        fun helper(n: Long): String {
            return when {
                n < 20 -> units[n.toInt()]
                n < 100 -> tens[(n / 10).toInt()] + if (n % 10 != 0L) " " + units[(n % 10).toInt()] else ""
                n < 1000 -> units[(n / 100).toInt()] + " hundred" + if (n % 100 != 0L) " " + helper(n % 100) else ""
                n < 100000 -> helper(n / 1000) + " thousand" + if (n % 1000 != 0L) " " + helper(n % 1000) else ""
                n < 10000000 -> helper(n / 100000) + " lakh" + if (n % 100000 != 0L) " " + helper(n % 100000) else ""
                else -> helper(n / 10000000) + " crore" + if (n % 10000000 != 0L) " " + helper(n % 10000000) else ""
            }
        }

        return helper(num).trim().replace("\\s+".toRegex(), " ")
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build()
            audioManager?.requestAudioFocus(focusRequest!!)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
            audioManager?.abandonAudioFocusRequest(focusRequest!!)
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        mediaPlayer?.release()
        super.onDestroy()
    }
}
