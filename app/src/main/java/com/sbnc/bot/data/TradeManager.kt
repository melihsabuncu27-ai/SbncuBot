package com.sbnc.bot.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TradeManager(context: Context) {
    private val prefs = context.getSharedPreferences("sbnc_trades", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val openTrades = mutableListOf<Trade>()
    private val closedTrades = mutableListOf<Trade>()

    init { load() }

    fun addOpen(trade: Trade) { openTrades.add(trade); save() }

    fun closeTrade(id: String, exitPrice: Double) {
        val idx = openTrades.indexOfFirst { it.id == id }
        if (idx < 0) return
        val t = openTrades.removeAt(idx)
        t.exitPrice = exitPrice
        t.exitTime = System.currentTimeMillis()
        t.updatePnl(exitPrice)
        t.status = "CLOSED"
        closedTrades.add(t)
        save()
    }

    fun closeAll(exitPrices: Map<String, Double>) {
        val ids = openTrades.map { it.id }
        for (id in ids) {
            val t = openTrades.firstOrNull { it.id == id } ?: continue
            val price = exitPrices[t.symbol] ?: t.entryPrice
            closeTrade(id, price)
        }
    }

    fun resetStats() { closedTrades.clear(); openTrades.clear(); save() }
    fun getOpen(): List<Trade> = openTrades.toList()
    fun getClosed(): List<Trade> = closedTrades.toList()
    fun totalPnl(): Double = closedTrades.sumOf { it.pnl }
    fun openPnl(): Double = openTrades.sumOf { it.pnl }
    fun balance(): Double = 1000.0 + totalPnl() + openPnl()

    private fun save() {
        prefs.edit()
            .putString("open", gson.toJson(openTrades))
            .putString("closed", gson.toJson(closedTrades))
            .apply()
    }

    private fun load() {
        val type = object : TypeToken<List<Trade>>() {}.type
        openTrades.addAll(gson.fromJson(prefs.getString("open", "[]"), type) ?: emptyList())
        closedTrades.addAll(gson.fromJson(prefs.getString("closed", "[]"), type) ?: emptyList())
    }
}
