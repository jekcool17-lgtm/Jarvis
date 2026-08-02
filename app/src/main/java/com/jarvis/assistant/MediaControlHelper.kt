package com.jarvis.assistant

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager

/**
 * Управляет воспроизведением через системный MediaSessionManager.
 * Это НАДЁЖНЕЕ, чем эмулировать клики по экрану: YouTube, Spotify,
 * Netflix и большинство видео/аудио-приложений публикуют MediaSession
 * с их плеером, и мы можем слать команды play()/pause()/seekTo()
 * напрямую, независимо от того, какое конкретно приложение открыто.
 *
 * Требует, чтобы пользователь один раз включил доступ к уведомлениям
 * для JarvisNotificationListenerService (кнопка в MainActivity).
 */
class MediaControlHelper(private val context: Context) {

    private fun activeController(): MediaController? {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(context, JarvisNotificationListenerService::class.java)
        return try {
            manager.getActiveSessions(component).firstOrNull()
        } catch (e: SecurityException) {
            // Доступ к уведомлениям ещё не выдан пользователем
            null
        }
    }

    fun play() {
        activeController()?.transportControls?.play()
    }

    fun pause() {
        activeController()?.transportControls?.pause()
    }

    fun seekRelative(deltaMs: Long) {
        val controller = activeController() ?: return
        val current = controller.playbackState?.position ?: return
        controller.transportControls.seekTo((current + deltaMs).coerceAtLeast(0))
    }

    fun seekToStart() {
        activeController()?.transportControls?.seekTo(0)
    }

    fun skipNext() {
        activeController()?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        activeController()?.transportControls?.skipToPrevious()
    }
}
