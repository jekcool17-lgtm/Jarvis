package com.jarvis.assistant

import android.service.notification.NotificationListenerService

/**
 * Сам по себе ничего не делает — само его наличие и включённое
 * разрешение "Доступ к уведомлениям" даёт MediaSessionManager право
 * возвращать список активных медиасессий (см. MediaControlHelper).
 */
class JarvisNotificationListenerService : NotificationListenerService()
