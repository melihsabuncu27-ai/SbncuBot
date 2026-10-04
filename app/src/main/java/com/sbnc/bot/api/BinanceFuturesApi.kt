package com.sbnc.bot.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class Candle(
    val open: Double, val high: Double, val low: Double,
    val close: Double, val volume: Double
)

class BinanceFuturesApi(
    private val apiKey: String,
    private val secretKey: String,
    private val testnet: Boolean = true
) {
    private val base = if (testnet) "https://testnet.binancefuture.com" else "https://fapi.binance.com"
    private val client = OkHttpClient()

    suspend fun klines(symbol: String, interval: String = "1h", limit: Int = 200): List<Candle> =
        withContext(Dispatchers.IO) {
            val url = "$base/fapi/v1/klines?symbol=$symbol&interval=$interval&limit=$limit"
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            val body = resp.body?.string() ?: return@withContext emptyList()
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val k = arr.getJSONArray(i)
                Candle(k.getString(1).toDouble(), k.getString(2).toDouble(),
                    k.getString(3).toDouble(), k.getString(4).toDouble(), k.getString(5).toDouble())
            }
        }

    suspend fun price(symbol: String): Double = withContext(Dispatchers.IO) {
        val url = "$base/fapi/v1/ticker/price?symbol=$symbol"
        val resp = client.newCall(Request.Builder().url(url).build()).execute()
        val body = resp.body?.string() ?: return@withContext 0.0
        org.json.JSONObject(body).getString("price").toDouble()
    }

    suspend fun setLeverage(symbol: String, lev: Int): String = withContext(Dispatchers.IO) {
        signed("POST", "/fapi/v1/leverage", mapOf("symbol" to symbol, "leverage" to lev.toString()))
    }

    suspend fun setMarginType(symbol: String, type: String): String = withContext(Dispatchers.IO) {
        signed("POST", "/fapi/v1/marginType", mapOf("symbol" to symbol, "marginType" to type))
    }

    suspend fun marketOrder(symbol: String, side: String, qty: Double): String = withContext(Dispatchers.IO) {
        signed("POST", "/fapi/v1/order", mapOf(
            "symbol" to symbol, "side" to side, "type" to "MARKET", "quantity" to qty.toString()
        ))
    }

    suspend fun closePosition(symbol: String, side: String, qty: Double): String = withContext(Dispatchers.IO) {
        signed("POST", "/fapi/v1/order", mapOf(
            "symbol" to symbol, "side" to side, "type" to "MARKET",
            "quantity" to qty.toString(), "reduceOnly" to "true"
        ))
    }

    suspend fun stopLoss(symbol: String, side: String, stopPrice: Double, qty: Double): String = withContext(Dispatchers.IO) {
        signed("POST", "/fapi/v1/order", mapOf(
            "symbol" to symbol, "side" to side, "type" to "STOP_MARKET",
            "stopPrice" to stopPrice.toString(), "quantity" to qty.toString(), "reduceOnly" to "true"
        ))
    }

    private fun signed(method: String, path: String, params: Map<String, String>): String {
        val ts = System.currentTimeMillis()
        val all = params + mapOf("timestamp" to ts.toString(), "recvWindow" to "5000")
        val query = all.entries.joinToString("&") { "${it.key}=${it.value}" }
        val sig = hmac(query, secretKey)
        val url = "$base$path?$query&signature=$sig"
        val req = Request.Builder().url(url)
            .addHeader("X-MBX-APIKEY", apiKey)
            .method(method, RequestBody.create(null, ""))
            .build()
        return client.newCall(req).execute().body?.string() ?: ""
    }

    private fun hmac(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
