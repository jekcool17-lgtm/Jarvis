package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import kotlin.math.roundToInt

/**
 * Всё, что не относится к медиа-плееру: громкость, яркость, запуск
 * приложений, базовая навигация (домой/назад через AccessibilityService),
 * открытие системных панелей.
 *
 * Честное ограничение: обычное (не системное, без root) приложение НЕ
 * может физически выключить телефон или программно переключить Wi-Fi /
 * энергосбережение — Android с API 29+ такое запрещает всем сторонним
 * приложениям. Поэтому для этих команд мы открываем нужный системный
 * экран одним действием — это максимум, что доступно легальным путём.
 */
class SystemController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ---------- Громкость ----------

    fun adjustVolume(up: Boolean) {
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    fun setVolumePercent(percent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent.coerceIn(0, 100) / 100f) * max).roundToInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
    }

    // ---------- Яркость ----------
    // Требует разрешения WRITE_SETTINGS (пользователь выдаёт его вручную в MainActivity)

    fun adjustBrightness(up: Boolean) {
        if (!Settings.System.canWrite(context)) return
        val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        val step = 25
        val newValue = (current + if (up) step else -step).coerceIn(0, 255)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newValue)
    }

    fun setBrightnessPercent(percent: Int) {
        if (!Settings.System.canWrite(context)) return
        val value = ((percent.coerceIn(0, 100) / 100f) * 255).roundToInt()
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
    }

    // ---------- Навигация ----------

    fun goHome() {
        JarvisAccessibilityService.instance?.goHome()
    }

    fun goBack() {
        JarvisAccessibilityService.instance?.goBack()
    }

    fun lockScreen() {
        JarvisAccessibilityService.instance?.lockScreen()
    }

    // ---------- Системные панели (Wi-Fi, энергосбережение) ----------

    fun openWifiPanel() {
        val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openBatterySaverSettings() {
        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    // ---------- Фонарик ----------

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var torchOn = false

    fun setFlashlight(on: Boolean) {
        try {
            val camId = cameraManager.cameraIdList.firstOrNull() ?: return
            cameraManager.setTorchMode(camId, on)
            torchOn = on
        } catch (e: Exception) {
            // На некоторых устройствах камера занята другим приложением
        }
    }

    fun toggleFlashlight() = setFlashlight(!torchOn)

    // ---------- Bluetooth ----------
    // Программное включение/выключение Bluetooth запрещено сторонним
    // приложениям с Android 13 (API 33) — открываем панель для одного тапа.

    fun openBluetoothPanel() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    // ---------- Удаление / установка приложений ----------
    // Оба действия требуют подтверждения пользователя тапом — так решил
    // Android, обойти это без root невозможно.

    fun requestUninstallByName(spokenName: String): Boolean {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        val query = spokenName.lowercase().trim()
        val match = apps.firstOrNull { pm.getApplicationLabel(it).toString().lowercase().contains(query) }
            ?: return false
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${match.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return true
    }

    fun openInstallPage(appName: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://search?q=$appName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    // ---------- Открыть браузер с поиском (для команды "фильм ...") ----------

    fun openBrowserSearch(query: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    // ---------- Запуск приложений по имени ----------

    fun openAppByName(spokenName: String): Boolean {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        val normalizedQuery = spokenName.lowercase().trim()

        val match = apps.firstOrNull { app ->
            val label = pm.getApplicationLabel(app).toString().lowercase()
            label.contains(normalizedQuery) || normalizedQuery.contains(label)
        } ?: return false

        val launchIntent = pm.getLaunchIntentForPackage(match.packageName) ?: return false
        launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(launchIntent)
        return true
    }
}
