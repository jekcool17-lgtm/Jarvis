package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Даёт доступ к глобальным действиям (домой/назад/шторка) и, при желании,
 * к поиску и клику по элементам на экране (полезно для приложений, которые
 * НЕ публикуют MediaSession и управляются только через обычные экранные
 * кнопки — например, свои плей/пауза внутри специфичного плеера).
 *
 * Пользователь обязан включить эту службу вручную в
 * Настройки → Специальные возможности → Jarvis (кнопка в MainActivity
 * ведёт сразу на нужный экран).
 */
class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    // Текст последнего элемента, по которому пользователь кликнул вручную —
    // используется при записи макро-команд ("запомни команду ...").
    var lastClickedText: String? = null
        private set

    private val adCloseHints = listOf(
        "закрыть рекламу", "close ad", "skip ad", "пропустить рекламу", "×", "x", "закрыть"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val text = event.text?.joinToString(" ")?.trim()
            if (!text.isNullOrEmpty()) {
                lastClickedText = text
            } else {
                event.contentDescription?.toString()?.let { lastClickedText = it }
            }
        }
    }

    /**
     * Best-effort: ищет на экране элемент, похожий на кнопку закрытия рекламы,
     * и кликает по нему. Ненадёжно — рекламные сети часто меняют разметку
     * специально, чтобы её было сложнее закрыть автоматически.
     */
    fun dismissLikelyAd(): Boolean {
        val root = rootInActiveWindow ?: return false
        for (hint in adCloseHints) {
            val nodes = root.findAccessibilityNodeInfosByText(hint)
            val target = nodes.firstOrNull()
            if (target != null) {
                var node = target
                while (node != null && !node.isClickable) node = node.parent
                if (node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    return true
                }
            }
        }
        return false
    }

    override fun onInterrupt() {}

    fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun goBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun lockScreen() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    /**
     * Ищет на экране элемент с указанным текстом и кликает по нему.
     * Полезно для команд вида "нажми на паузу" в приложениях без MediaSession.
     */
    fun clickElementByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        val target = nodes.firstOrNull { it.isClickable } ?: nodes.firstOrNull()
        return if (target != null) {
            var node = target
            while (node != null && !node.isClickable) {
                node = node.parent
            }
            node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) ?: false
        } else {
            false
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        var instance: JarvisAccessibilityService? = null
    }
}
