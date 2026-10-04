package com.sbnc.bot.api

import okhttp3.*
import org.json.JSONObject

class BinanceWebSocket(
    private val testnet: Boolean,
    private val onPrice: (symbol: String, price: Double) -> Unit
) {
    private val client = OkHttpClient()
    private val sockets = mutableListOf<WebSocket>()

    fun subscribe(symbols: List<String>) {
        val base = if (testnet) "wss://stream.binancefuture.com/ws" else "wss://fstream.binance.com/ws"
        for (sym in symbols) {
            val s = sym.lowercase()
            val req = Request.Builder().url("$base/$s@markPrice@1s").build()
            val ws = client.newWebSocket(req, object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val j = JSONObject(text)
                        val symbol = j.getString("s")
                        val price = j.getString("p").toDouble()
                        onPrice(symbol, price)
                    } catch (_: Exception) {}
                }
            })
            sockets.add(ws)
        }
    }

    fun close() {
        sockets.forEach { it.close(1000, null) }
        sockets.clear()
    }
}
