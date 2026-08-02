package com.jarvis.assistant

import android.content.Context

/**
 * Простой интент-парсер на regex/ключевых словах. Для MVP этого достаточно
 * и он полностью прозрачен/настраиваем — в отличие от "чёрного ящика" NLU.
 * Дальше при желании можно заменить на вызов LLM для более гибкого
 * понимания формулировок (см. AiFallbackHelper).
 */
class CommandProcessor(private val context: Context, private val tts: TtsHelper) {

    private val systemController = SystemController(context)
    private val mediaHelper = MediaControlHelper(context)
    private val aiFallback = AiFallbackHelper()
    private val memory = MemoryStore(context)

    // Режим ожидания записи макро-команды: после "запомни команду <имя>"
    // следующий клик, который сделает пользователь вручную, свяжется с именем.
    private var pendingMacroName: String? = null

    private val movieStylePhrases = listOf(
        "Сию секунду, сэр",
        "Как пожелаете, сэр",
        "Уже выполняю, сэр",
        "Разумеется, сэр",
        "Готово, сэр"
    )

    fun process(rawText: String) {
        val cleaned = rawText.lowercase().replace("джарвис", "").trim()

        val response: String = when {
            // ---- Память: "запомни, что моё имя ..." / "что ты помнишь про ..." ----
            Regex("запомни,?\\s+что\\s+(.+)").find(cleaned) != null -> {
                val fact = Regex("запомни,?\\s+что\\s+(.+)").find(cleaned)!!.groupValues[1].trim()
                val key = fact.split(" ").take(3).joinToString(" ")
                memory.remember(key, fact)
                "Запомнил, сэр"
            }
            Regex("что ты помнишь про\\s+(.+)").find(cleaned) != null -> {
                val key = Regex("что ты помнишь про\\s+(.+)").find(cleaned)!!.groupValues[1].trim()
                val found = memory.allFacts().entries.firstOrNull { it.key.contains(key) || key.contains(it.key) }
                found?.value ?: "Пока ничего не записано об этом, сэр"
            }

            // ---- Запись пользовательской макро-команды ----
            Regex("запомни команду\\s+(.+)").find(cleaned) != null -> {
                val name = Regex("запомни команду\\s+(.+)").find(cleaned)!!.groupValues[1].trim()
                pendingMacroName = name
                "Хорошо, сделайте нужное нажатие в приложении, я его запомню, сэр"
            }
            pendingMacroName != null && Regex("(сохран|запиш)").containsMatchIn(cleaned) -> {
                val lastClicked = JarvisAccessibilityService.instance?.lastClickedText
                if (lastClicked != null) {
                    memory.saveMacro(pendingMacroName!!, lastClicked)
                    val saved = pendingMacroName
                    pendingMacroName = null
                    "Команда \"$saved\" запомнена, сэр"
                } else {
                    "Не увидел, на что вы нажали, сэр. Попробуйте ещё раз"
                }
            }
            memory.getMacro(cleaned) != null -> {
                val target = memory.getMacro(cleaned)!!
                val ok = JarvisAccessibilityService.instance?.clickElementByText(target) ?: false
                if (ok) randomPhrase() else "Не нашёл нужный элемент на экране, сэр"
            }

            // ---- Фонарик ----
            Regex("(включи|зажги).*фонарик").containsMatchIn(cleaned) -> {
                systemController.setFlashlight(true); "Фонарик включён"
            }
            Regex("(выключи|потуши).*фонарик").containsMatchIn(cleaned) -> {
                systemController.setFlashlight(false); "Фонарик выключен"
            }

            // ---- Bluetooth ----
            Regex("(включи|выключи).*(bluetooth|блютуз)").containsMatchIn(cleaned) -> {
                systemController.openBluetoothPanel(); "Открываю настройки Bluetooth"
            }

            // ---- Установка / удаление приложений ----
            Regex("удали приложение\\s+(.+)").find(cleaned) != null -> {
                val name = Regex("удали приложение\\s+(.+)").find(cleaned)!!.groupValues[1].trim()
                val ok = systemController.requestUninstallByName(name)
                if (ok) "Подтвердите удаление $name на экране, сэр" else "Не нашёл приложение $name"
            }
            Regex("установи приложение\\s+(.+)|скачай приложение\\s+(.+)").find(cleaned) != null -> {
                val m = Regex("установи приложение\\s+(.+)|скачай приложение\\s+(.+)").find(cleaned)!!
                val name = (m.groupValues[1].ifBlank { m.groupValues[2] }).trim()
                systemController.openInstallPage(name)
                "Открываю страницу $name в Play Маркете, сэр"
            }

            // ---- Фильм: "фильм <название>" ----
            Regex("^фильм\\s+(.+)").find(cleaned) != null -> {
                val title = Regex("^фильм\\s+(.+)").find(cleaned)!!.groupValues[1].trim()
                systemController.openBrowserSearch("$title смотреть онлайн бесплатно в хорошем качестве русская озвучка")
                "Ищу \"$title\" с русской озвучкой, сэр. Выберите сайт — дальше помогу с полноэкранным режимом"
            }
            cleaned.trim() == "фильм" -> {
                systemController.openBrowserSearch("подборка лучших фильмов 2026 смотреть онлайн")
                "На ваш выбор не претендую, сэр, но вот подборка — выбирайте"
            }
            Regex("полноэкранн|на весь экран").containsMatchIn(cleaned) -> {
                JarvisAccessibilityService.instance?.clickElementByText("Полноэкранный режим")
                JarvisAccessibilityService.instance?.clickElementByText("Fullscreen")
                "Пробую развернуть на весь экран, сэр"
            }
            Regex("(пропусти|скипни|убери) рекламу").containsMatchIn(cleaned) -> {
                val closed = JarvisAccessibilityService.instance?.dismissLikelyAd() ?: false
                if (closed) "Закрыл рекламу, сэр" else "Не нашёл кнопку закрытия рекламы, сэр"
            }
            // ---- Громкость ----
            Regex("громкост.*(увелич|прибав|больше)").containsMatchIn(cleaned) -> {
                systemController.adjustVolume(up = true); "Громкость увеличена"
            }
            Regex("громкост.*(уменьш|убав|меньше)").containsMatchIn(cleaned) -> {
                systemController.adjustVolume(up = false); "Громкость уменьшена"
            }
            Regex("громкост.*(\\d{1,3})").find(cleaned) != null -> {
                val percent = Regex("(\\d{1,3})").find(cleaned)!!.value.toInt()
                systemController.setVolumePercent(percent); "Громкость установлена на $percent процентов"
            }

            // ---- Яркость ----
            Regex("яркост.*(увелич|прибав|больше)").containsMatchIn(cleaned) -> {
                systemController.adjustBrightness(up = true); "Яркость увеличена"
            }
            Regex("яркост.*(уменьш|убав|меньше)").containsMatchIn(cleaned) -> {
                systemController.adjustBrightness(up = false); "Яркость уменьшена"
            }
            Regex("яркост.*(\\d{1,3})").find(cleaned) != null -> {
                val percent = Regex("(\\d{1,3})").find(cleaned)!!.value.toInt()
                systemController.setBrightnessPercent(percent); "Яркость установлена на $percent процентов"
            }

            // ---- Медиа: пауза/плей/перемотка/сначала ----
            Regex("(пауз|останов)").containsMatchIn(cleaned) -> {
                mediaHelper.pause(); "Ставлю на паузу"
            }
            Regex("(продолж|плей|play|воспроизвед)").containsMatchIn(cleaned) -> {
                mediaHelper.play(); "Продолжаю"
            }
            Regex("(перемот.*вперёд|перемот.*вперед|вперёд|следующ)").containsMatchIn(cleaned) -> {
                mediaHelper.seekRelative(15_000); "Перематываю вперёд"
            }
            Regex("(перемот.*назад|назад|предыдущ)").containsMatchIn(cleaned) -> {
                mediaHelper.seekRelative(-15_000); "Перематываю назад"
            }
            Regex("(сначала|с самого начала)").containsMatchIn(cleaned) -> {
                mediaHelper.seekToStart(); "Начинаю сначала"
            }

            // ---- Навигация по системе ----
            Regex("(домой|на главный экран)").containsMatchIn(cleaned) -> {
                systemController.goHome(); "Иду на главный экран"
            }
            Regex("(назад|вернись)").containsMatchIn(cleaned) && !cleaned.contains("перемот") -> {
                systemController.goBack(); "Возвращаюсь назад"
            }
            Regex("заблокируй экран|заблокируй телефон").containsMatchIn(cleaned) -> {
                systemController.lockScreen(); "Блокирую экран"
            }

            // ---- Wi-Fi / энергосбережение (Android не даёт переключать это напрямую,
            //      открываем нужную панель настроек одним действием) ----
            Regex("(включи|выключи).*wi.?fi|вайфай").containsMatchIn(cleaned) -> {
                systemController.openWifiPanel(); "Открываю настройки Wi-Fi"
            }
            Regex("энергосбережен").containsMatchIn(cleaned) -> {
                systemController.openBatterySaverSettings(); "Открываю настройки энергосбережения"
            }

            // ---- Открыть приложение: "открой ютуб", "зайди в спотифай" ----
            Regex("(открой|зайди в|запусти)\\s+(.+)").find(cleaned) != null -> {
                val appName = Regex("(?:открой|зайди в|запусти)\\s+(.+)").find(cleaned)!!.groupValues[1].trim()
                val opened = systemController.openAppByName(appName)
                if (opened) "Открываю $appName" else "Не нашёл приложение $appName"
            }

            // ---- Всё остальное — трактуем как вопрос и уходим к ИИ-фолбэку ----
            else -> {
                aiFallback.ask(cleaned)
            }
        }

        tts.speak(ensureSir(response))
    }

    private fun ensureSir(text: String): String {
        return if (text.trim().lowercase().endsWith("сэр")) text else "$text, сэр"
    }

    private fun randomPhrase(): String = movieStylePhrases.random()
}
