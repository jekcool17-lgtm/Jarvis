package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Главный экран. Отсюда пользователь:
 * 1) выдаёт разрешение на микрофон
 * 2) включает Accessibility Service вручную (Android этого требует, нельзя автоматически)
 * 3) включает доступ к уведомлениям / медиасессиям
 * 4) стартует/останавливает фоновый сервис распознавания речи
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "Микрофон разрешён", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Без микрофона Джарвис работать не может", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.btnGrantMic).setOnClickListener {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        findViewById<Button>(R.id.btnGrantWriteSettings).setOnClickListener {
            if (!AndroidSettings.System.canWrite(this)) {
                val intent = Intent(AndroidSettings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Уже разрешено", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Найдите \"Jarvis\" в списке и включите", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnNotificationAccess).setOnClickListener {
            startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(this, "Найдите \"Jarvis\" и включите доступ", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            val serviceIntent = Intent(this, VoiceRecognitionService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            statusText.text = "Джарвис слушает..."
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, VoiceRecognitionService::class.java))
            statusText.text = "Остановлен"
        }
    }
}
