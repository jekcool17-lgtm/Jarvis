package com.jarvis.assistant

import android.app.admin.DeviceAdminReceiver

/**
 * Пустой ресивер — нужен только чтобы система разрешила приложению
 * запрашивать статус device admin (используется для DevicePolicyManager.lockNow()).
 * Не обязателен, если блокировку экрана делаем через AccessibilityService
 * GLOBAL_ACTION_LOCK_SCREEN (доступно с API 28+) — тогда этот файл можно
 * не подключать вовсе.
 */
class JarvisDeviceAdminReceiver : DeviceAdminReceiver()
