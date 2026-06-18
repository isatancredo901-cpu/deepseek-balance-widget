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

        fun updateWidget(context: Context, widgetMgr: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val apiKey = prefs.getString(KEY_API_KEY, null)

            // 点击事件
            try {
                val refreshIntent = Intent(context, DeepSeekWidget::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                views.setOnClickPendingIntent(R.id.refresh_btn,
                    PendingIntent.getBroadcast(context, widgetId, refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

                val settingsIntent = Intent(context, SettingsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                views.setOnClickPendingIntent(R.id.widget_root,
                    PendingIntent.getActivity(context, widgetId + 10000, settingsIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            } catch (_: Exception) {}

            // 占位显示
            if (apiKey.isNullOrBlank() || !apiKey.startsWith("sk-")) {
                views.setTextViewText(R.id.balance_text, "—.—")
                views.setTextViewText(R.id.status_text, "点击设置 API Key")
            } else {
                views.setTextViewText(R.id.balance_text, "⋯")
                views.setTextViewText(R.id.status_text, "查询中...")
            }
            widgetMgr.updateAppWidget(widgetId, views)

            // 后台查余额
            if (!apiKey.isNullOrBlank() && apiKey.startsWith("sk-")) {
                Thread {
                    try {
                        val data = fetchBalance(apiKey)
                        val available = data.optBoolean("is_available", false)
                        val bi = data.optJSONArray("balance_infos")?.optJSONObject(0) ?: return@Thread

                        val currency = bi.optString("currency", "CNY")
                        val sym = if (currency == "CNY") "¥" else "$"
                        val total = bi.optString("total_balance", "0").toDoubleOrNull() ?: 0.0

                        views.setTextViewText(R.id.balance_text,
                            "$sym${"%.2f".format(total)}")
                        views.setTextViewText(R.id.status_text,
                            if (available) "🟢 API 可用"
                            else "🔴 余额不足")

                        prefs.edit().putString(KEY_CACHE, data.toString()).apply()
                        widgetMgr.updateAppWidget(widgetId, views)

                    } catch (_: Exception) {
                        val cached = prefs.getString(KEY_CACHE, null)
                        if (cached != null) {
                            try {
                                val c = JSONObject(cached)
                                val cBi = c.optJSONArray("balance_infos")?.optJSONObject(0)
                                if (cBi != null) {
                                    val sym = if (cBi.optString("currency", "CNY") == "CNY") "¥" else "$"
                                    views.setTextViewText(R.id.balance_text,
                                        "$sym${cBi.optString("total_balance", "0")}")
                                    views.setTextViewText(R.id.status_text, "📦 缓存")
                                    widgetMgr.updateAppWidget(widgetId, views)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }.start()
            }
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
                if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
                return JSONObject(conn.inputStream.bufferedReader().readText())
            } finally {
                conn.disconnect()
            }
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            try { updateWidget(context, mgr, id) } catch (_: Exception) {}
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        try { super.onReceive(context, intent) } catch (_: Exception) {}
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (id != -1) {
                try { updateWidget(context, AppWidgetManager.getInstance(context), id) }
                catch (_: Exception) {}
            }
        }
    }
}
