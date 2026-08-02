package com.jarvis.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Обёртка над Android TextToSpeech, настроенная так, чтобы голос звучал
 * ближе к "Джарвису": чуть ниже питч, чуть спокойнее темп.
 *
 * Штатный Android TTS не сможет дать 1-в-1 голос из фильма (это голос
 * актёра, клонировать его нельзя). Если хочется максимально похожий и
 * действительно киношный тембр — см. README, раздел "Более качественный
 * голос", там описано подключение стороннего нейро-TTS (ElevenLabs и
 * похожие сервисы дают кастомные "дикторские" голоса по API).
 */
class TtsHelper(context: Context) {

    private var ready = false
    private val queueBeforeReady = mutableListOf<String>()

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            configureVoice()
            queueBeforeReady.forEach { speakNow(it) }
            queueBeforeReady.clear()
        }
    }

    private fun configureVoice() {
        tts.language = Locale("ru", "RU")
        // Чуть ниже тон и чуть медленнее — придаёт "спокойный и уверенный" оттенок
        tts.setPitch(0.85f)
        tts.setSpeechRate(1.0f)
    }

    fun speak(text: String) {
        if (ready) speakNow(text) else queueBeforeReady.add(text)
    }

    private fun speakNow(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
