package com.deepseek.widget

import android.content.Context
import android.os.Bundle
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

        apiKeyInput.setText(prefs.getString(DeepSeekWidget.KEY_API_KEY, ""))

        saveBtn.setOnClickListener {
            try {
                val key = apiKeyInput.text.toString().trim()

                if (key.isBlank()) {
                    Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!key.startsWith("sk-")) {
                    Toast.makeText(this, "API Key 应以 sk- 开头", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 只保存，不做任何其他操作
                prefs.edit().putString(DeepSeekWidget.KEY_API_KEY, key).apply()
                Toast.makeText(this, "✅ 已保存！\n请回桌面添加小部件", Toast.LENGTH_LONG).show()

                // 直接关闭，不刷新 widget
                finish()

            } catch (e: Exception) {
                Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
