package com.sbnc.bot.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sbnc_prefs", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("apiKey", "") ?: ""
        set(v) = prefs.edit().putString("apiKey", v).apply()

    var apiSecret: String
        get() = prefs.getString("apiSecret", "") ?: ""
        set(v) = prefs.edit().putString("apiSecret", v).apply()

    var testnet: Boolean
        get() = prefs.getBoolean("testnet", true)
        set(v) = prefs.edit().putBoolean("testnet", v).apply()

    var leverage: Int
        get() = prefs.getInt("leverage", 3)
        set(v) = prefs.edit().putInt("leverage", v).apply()

    var tradeUsdt: Double
        get() = prefs.getFloat("tradeUsdt", 100f).toDouble()
        set(v) = prefs.edit().putFloat("tradeUsdt", v.toFloat()).apply()

    var marginType: String
        get() = prefs.getString("marginType", "ISOLATED") ?: "ISOLATED"
        set(v) = prefs.edit().putString("marginType", v).apply()
}
