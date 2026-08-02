package com.jarvis.assistant

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

/**
 * Раз в заданный интервал озвучивает напоминание сделать паузу.
 * Не претендует на точную аналитику активности — простое периодическое
 * напоминание, как и просили ("иногда должен говорить, что пора отдохнуть").
 */
class WellbeingReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val reminders = listOf(
        "Сэр, вы уже давно за экраном. Рекомендую сделать паузу и размять спину",
        "Пора немного отдохнуть, сэр. Даже мне иногда требуется пауза",
        "Сэр, советую выпить воды и отвлечься на пару минут"
    )

    override suspend fun doWork(): Result {
        val tts = TtsHelper(applicationContext)
        delay(800) // дать TTS-движку инициализироваться
        tts.speak(reminders.random())
        delay(4000)
        tts.shutdown()
        return Result.success()
    }
}
