package com.sbnc.bot.data

import java.util.UUID

data class Trade(
    val id: String = UUID.randomUUID().toString(),
    val symbol: String,
    val side: String,
    val entryPrice: Double,
    val quantity: Double,
    val entryTime: Long = System.currentTimeMillis(),
    var exitPrice: Double? = null,
    var exitTime: Long? = null,
    var pnl: Double = 0.0,
    var status: String = "OPEN",
    var binanceOrderId: Long? = null
) {
    fun updatePnl(currentPrice: Double) {
        pnl = if (side == "LONG") {
            (currentPrice - entryPrice) * quantity
        } else {
            (entryPrice - currentPrice) * quantity
        }
    }
}
