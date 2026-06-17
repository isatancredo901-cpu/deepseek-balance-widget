package com.deepseek.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences(DeepSeekWidget.PREFS_NAME, Context.MODE_PRIVATE)
        val apiKeyInput = findViewById<EditText>(R.id.api_key_input)
        val saveBtn = findViewById<Button>(R.id.save_btn)

        // 加载已保存的 Key
        apiKeyInput.setText(prefs.getString(DeepSeekWidget.KEY_API_KEY, ""))

        saveBtn.setOnClickListener {
            val key = apiKeyInput.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!key.startsWith("sk-")) {
                Toast.makeText(this, "API Key 应以 sk- 开头", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putString(DeepSeekWidget.KEY_API_KEY, key).apply()
            Toast.makeText(this, "✅ 已保存", Toast.LENGTH_SHORT).show()

            // 尝试刷新 Widget（如果有的话）
            try {
                val appWidgetManager = AppWidgetManager.getInstance(this)
                val componentName = ComponentName(this, DeepSeekWidget::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds.isNotEmpty()) {
                    val intent = Intent(this, DeepSeekWidget::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    }
                    sendBroadcast(intent)
                }
            } catch (_: Exception) {
                // Widget 还没添加到桌面，忽略
            }

            // 延迟关闭，避免 Toast 冲突
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 300)
        }
    }
}
