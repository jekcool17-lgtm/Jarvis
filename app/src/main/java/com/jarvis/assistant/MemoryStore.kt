package com.jarvis.assistant

import android.content.Context
import org.json.JSONObject

/**
 * Простое персистентное хранилище на SharedPreferences.
 * Два назначения:
 *  1) "запомни X" / "что ты помнишь про Y" — произвольные факты
 *  2) кастомные макро-команды (имя команды -> текст элемента для клика)
 */
class MemoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)

    // ---- Произвольные факты ----

    fun remember(key: String, value: String) {
        prefs.edit().putString("fact_${key.trim()}", value).apply()
    }

    fun recall(key: String): String? {
        return prefs.getString("fact_${key.trim()}", null)
    }

    fun allFacts(): Map<String, String> {
        return prefs.all
            .filterKeys { it.startsWith("fact_") }
            .mapKeys { it.key.removePrefix("fact_") }
            .mapValues { it.value.toString() }
    }

    // ---- Макро-команды (имя -> текст элемента, по которому кликать) ----

    fun saveMacro(commandName: String, targetElementText: String) {
        prefs.edit().putString("macro_${commandName.trim()}", targetElementText).apply()
    }

    fun getMacro(commandName: String): String? {
        return prefs.getString("macro_${commandName.trim()}", null)
    }

    fun allMacroNames(): List<String> {
        return prefs.all.keys.filter { it.startsWith("macro_") }.map { it.removePrefix("macro_") }
    }
}
