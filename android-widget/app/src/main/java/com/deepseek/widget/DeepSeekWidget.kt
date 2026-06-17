package com.deepseek.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class DeepSeekWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "DeepSeekWidget"
        const val ACTION_REFRESH = "com.deepseek.widget.ACTION_REFRESH"
        const val PREFS_NAME = "deepseek_widget_prefs"
        const val KEY_API_KEY = "api_key"
        const val KEY_CACHE = "balance_cache"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val apiKey = prefs.getString(KEY_API_KEY, null)

                // 设置加载中
                views.setTextViewText(R.id.balance_text, "⋯")
                views.setTextViewText(R.id.status_text, "查询中...")
                views.setTextColor(R.id.status_dot, 0xFFFBBF24.toInt())

                // 设置点击事件（在异步操作之前，确保 widget 可以响应点击）
                setupClickIntents(context, views, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, views)

                // 异步获取余额数据
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val result = fetchAndRender(context, apiKey, views)
                        withContext(Dispatchers.Main) {
                            appWidgetManager.updateAppWidget(appWidgetId, result)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "updateWidget error", e)
                        // 静默失败，保持现有显示
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateWidget setup error", e)
            }
        }

        private fun setupClickIntents(context: Context, views: RemoteViews, appWidgetId: Int) {
            // 刷新按钮
            val refreshIntent = Intent(context, DeepSeekWidget::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPending = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.refresh_btn, refreshPending)

            // 点击 widget → 打开设置
            val settingsIntent = Intent(context, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val settingsPending = PendingIntent.getActivity(
                context, appWidgetId + 1000, settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, settingsPending)
        }

        private suspend fun fetchAndRender(
            context: Context,
            apiKey: String?,
            views: RemoteViews
        ): RemoteViews {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            if (apiKey.isNullOrBlank() || !apiKey.startsWith("sk-")) {
                views.setTextViewText(R.id.balance_text, "—.—")
                views.setTextViewText(R.id.status_text, "点击设置 API Key")
                views.setTextColor(R.id.status_dot, 0xFFFBBF24.toInt())
                return views
            }

            return try {
                val data = fetchBalance(apiKey)
                val isAvailable = data.optBoolean("is_available", false)
                val bi = data.optJSONArray("balance_infos")?.optJSONObject(0)

                if (bi != null) {
                    val currency = bi.optString("currency", "CNY")
                    val symbol = if (currency == "CNY") "¥" else "$"
                    val total = bi.optString("total_balance", "0").toDoubleOrNull() ?: 0.0
                    val granted = bi.optString("granted_balance", "0")
                    val toppedUp = bi.optString("topped_up_balance", "0")

                    views.setTextViewText(R.id.balance_text, "$symbol${"%.2f".format(total)}")
                    views.setTextViewText(R.id.status_text,
                        "充值 ¥$toppedUp · 赠金 ¥$granted")
                    views.setTextColor(R.id.status_dot,
                        if (isAvailable) 0xFF22C55E.toInt() else 0xFFEF4444.toInt())

                    // 缓存成功数据
                    prefs.edit().putString(KEY_CACHE, data.toString()).apply()
                }
                views
            } catch (e: Exception) {
                Log.w(TAG, "Fetch balance failed, trying cache", e)
                // 尝试缓存
                val cached = prefs.getString(KEY_CACHE, null)
                if (cached != null) {
                    try {
                        val cData = JSONObject(cached)
                        val cBi = cData.optJSONArray("balance_infos")?.optJSONObject(0)
                        if (cBi != null) {
                            val currency = cBi.optString("currency", "CNY")
                            val symbol = if (currency == "CNY") "¥" else "$"
                            val total = cBi.optString("total_balance", "0")
                            views.setTextViewText(R.id.balance_text, "$symbol$total")
                            views.setTextViewText(R.id.status_text, "📦 缓存 · 无法联网")
                            views.setTextColor(R.id.status_dot, 0xFF94A3B8.toInt())
                        }
                    } catch (_: Exception) {}
                }

                if (apiKey.isNotBlank()) {
                    views.setTextViewText(R.id.status_text, "网络错误")
                    views.setTextColor(R.id.status_dot, 0xFFEF4444.toInt())
                }
                views
            }
        }

        private fun fetchBalance(apiKey: String): JSONObject {
            val url = URL("https://api.deepseek.com/user/balance")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.connect()

                val code = conn.responseCode
                if (code != 200) {
                    val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    throw Exception("HTTP $code: $errorBody")
                }
                val body = conn.inputStream.bufferedReader().readText()
                return JSONObject(body)
            } finally {
                conn.disconnect()
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            try {
                updateWidget(context, appWidgetManager, id)
            } catch (e: Exception) {
                Log.e(TAG, "onUpdate failed for widget $id", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            super.onReceive(context, intent)
            if (intent.action == ACTION_REFRESH) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onReceive error", e)
        }
    }
}
