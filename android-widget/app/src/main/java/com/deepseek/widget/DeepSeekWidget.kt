package com.deepseek.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class DeepSeekWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.deepseek.widget.ACTION_REFRESH"
        const val PREFS_NAME = "deepseek_widget_prefs"
        const val KEY_API_KEY = "api_key"
        const val KEY_CACHE = "balance_cache"

        private fun setupClickIntents(context: Context, views: RemoteViews, appWidgetId: Int) {
            try {
                // 刷新按钮
                val refreshIntent = Intent(context, DeepSeekWidget::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val refreshPI = PendingIntent.getBroadcast(
                    context, appWidgetId, refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.refresh_btn, refreshPI)

                // 点击 widget → 设置
                val settingsIntent = Intent(context, SettingsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val settingsPI = PendingIntent.getActivity(
                    context, appWidgetId + 10000, settingsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, settingsPI)
            } catch (_: Exception) {}
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)

                // 先显示占位
                views.setTextViewText(R.id.balance_text, "⋯")
                views.setTextViewText(R.id.status_text, "查询中...")
                views.setTextColor(R.id.status_dot, 0xFFFBBF24.toInt())
                setupClickIntents(context, views, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, views)

                // 后台查余额
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val apiKey = prefs.getString(KEY_API_KEY, null)

                if (apiKey.isNullOrBlank() || !apiKey.startsWith("sk-")) {
                    views.setTextViewText(R.id.balance_text, "—.—")
                    views.setTextViewText(R.id.status_text, "点击设置 API Key")
                    views.setTextColor(R.id.status_dot, 0xFFFBBF24.toInt())
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    return
                }

                Thread {
                    try {
                        val data = fetchBalance(apiKey)
                        val available = data.optBoolean("is_available", false)
                        val bi = data.optJSONArray("balance_infos")?.optJSONObject(0)

                        if (bi != null) {
                            val currency = bi.optString("currency", "CNY")
                            val sym = if (currency == "CNY") "¥" else "$"
                            val total = bi.optString("total_balance", "0").toDoubleOrNull() ?: 0.0
                            val granted = bi.optString("granted_balance", "0")
                            val topped = bi.optString("topped_up_balance", "0")

                            views.setTextViewText(R.id.balance_text, "$sym${"%.2f".format(total)}")
                            views.setTextViewText(R.id.status_text, "充值 ¥$topped · 赠金 ¥$granted")
                            views.setTextColor(R.id.status_dot,
                                if (available) 0xFF22C55E.toInt() else 0xFFEF4444.toInt()
                            )

                            // 缓存
                            prefs.edit().putString(KEY_CACHE, data.toString()).apply()
                        }

                        appWidgetManager.updateAppWidget(appWidgetId, views)

                    } catch (e: Exception) {
                        // 尝试缓存
                        val cached = prefs.getString(KEY_CACHE, null)
                        if (cached != null) {
                            try {
                                val cData = JSONObject(cached)
                                val cBi = cData.optJSONArray("balance_infos")?.optJSONObject(0)
                                if (cBi != null) {
                                    val currency = cBi.optString("currency", "CNY")
                                    val sym = if (currency == "CNY") "¥" else "$"
                                    views.setTextViewText(R.id.balance_text, "$sym${cBi.optString("total_balance", "0")}")
                                    views.setTextViewText(R.id.status_text, "📦 缓存 · 无法联网")
                                    views.setTextColor(R.id.status_dot, 0xFF94A3B8.toInt())
                                    appWidgetManager.updateAppWidget(appWidgetId, views)
                                }
                            } catch (_: Exception) {}
                        } else {
                            views.setTextViewText(R.id.status_text, "网络错误")
                            views.setTextColor(R.id.status_dot, 0xFFEF4444.toInt())
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    }
                }.start()

            } catch (_: Exception) {}
        }

        private fun fetchBalance(apiKey: String): JSONObject {
            val conn = URL("https://api.deepseek.com/user/balance").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.connect()

                val code = conn.responseCode
                if (code != 200) {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    throw Exception("HTTP $code: $err")
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
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            super.onReceive(context, intent)
            if (intent.action == ACTION_REFRESH) {
                val mgr = AppWidgetManager.getInstance(context)
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    updateWidget(context, mgr, id)
                }
            }
        } catch (_: Exception) {}
    }
}
