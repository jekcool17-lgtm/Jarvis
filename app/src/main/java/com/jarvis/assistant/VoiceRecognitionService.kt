package com.jarvis.assistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat

class VoiceRecognitionService : Service(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TtsHelper
    private lateinit var commandProcessor: CommandProcessor
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false

    private var awake = false
    private val wakeWord = "джарвис"
    private val sleepWord = "спи"

    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        tts = TtsHelper(this)
        commandProcessor = CommandProcessor(this, tts)
        startForeground(NOTIFICATION_ID, buildNotification("Джарвис активен"))
        initRecognizer()
        scheduleWellbeingReminders()
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = pm.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK, "Jarvis::ListeningWakeLock"
        ).apply { acquire(12 * 60 * 60 * 1000L) }
    }

    private fun scheduleWellbeingReminders() {
        val request = androidx.work.PeriodicWorkRequestBuilder<WellbeingReminderWorker>(
            90, java.util.concurrent.TimeUnit.MINUTES
        ).setInitialDelay(90, java.util.concurrent.TimeUnit.MINUTES).build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "jarvis_wellbeing_reminder",
            androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListening()
        tts.speak("Слушаю. Скажите Джарвис")
        return START_STICKY
    }

    private fun initRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(this@VoiceRecognitionService)
        }
    }

    private fun recognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
    }

    private fun startListening() {
        if (listening) return
        listening = true
        speechRecognizer?.startListening(recognizerIntent())
    }

    private fun restartListeningSoon() {
        listening = false
        handler.postDelayed({ startListening() }, 400)
    }

    override fun onResults(results: Bundle) {
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val heard = matches?.firstOrNull()?.trim().orEmpty().lowercase()

        if (heard.isNotEmpty()) {
            when {
                !awake && heard.contains(wakeWord) -> {
                    awake = true
                    val rest = heard.substringAfter(wakeWord).trim()
                    if (rest.isNotEmpty()) {
                        commandProcessor.process(rest)
                    } else {
                        tts.speak("Да, сэр")
                    }
                }
                awake && heard.contains(sleepWord) && heard.length < 20 -> {
                    awake = false
                    tts.speak("Ухожу в режим ожидания, сэр")
                }
                awake -> {
                    commandProcessor.process(heard)
                }
            }
        }
        restartListeningSoon()
    }

    override fun onError(error: Int) {
        restartListeningSoon()
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun buildNotification(text: String): Notification {
        val channelId = "jarvis_channel"
        if (Build_VERSION_O()) {
            val channel = NotificationChannel(
                channelId, "Jarvis", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun Build_VERSION_O() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts.shutdown()
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("jarvis_wellbeing_reminder")
        if (wakeLock?.isHeld == true) wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 42
    }
}
