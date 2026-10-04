package com.sbnc.bot.trading

import android.content.Context
import com.sbnc.bot.api.BinanceFuturesApi
import com.sbnc.bot.api.Candle
import com.sbnc.bot.data.SettingsManager
import com.sbnc.bot.data.Trade
import com.sbnc.bot.data.TradeManager
import com.sbnc.bot.ml.FeatureEngine
import com.sbnc.bot.ml.ModelRunner
import com.sbnc.bot.ml.Normalizer

class TradingLoop(
    private val context: Context,
    private val settings: SettingsManager,
    private val tradeManager: TradeManager,
    private val runner: ModelRunner,
    private val onLog: (String) -> Unit
) {
    private val api = BinanceFuturesApi(settings.apiKey, settings.apiSecret, settings.testnet)
    private val featureEngine = FeatureEngine()
    private val normalizer = Normalizer()

    private val FEATURES = listOf(
        "open","high","low","close","volume",
        "rv_6","absr_6","rv_12","absr_12","rv_24","absr_24","rv_48","absr_48","rv_96","absr_96",
        "rv_sq_24","park_24","gk_24","vol_of_vol","hl_range","hl_ma24","vol_ma","rsi",
        "rv_d","rv_w","rv_m","rv_d_w","rv_w_m","bipower","jump","jump_r",
        "skew_24","kurt_24","mom_6","mom_24","mom_96","trend_12","trend_24","vol_mom"
    )

    suspend fun scanSymbol(symbol: String): Trade? {
        try {
            onLog("→ \$symbol taranıyor...")
            val candles = api.klines(symbol, "1h", 200)
            if (candles.size < 100) { onLog("  ✗ \$symbol yetersiz veri"); return null }

            val featMap = featureEngine.computeFeatures(candles)
            val xNorm = normalizer.normalize(featMap, FEATURES, 720, 168)
            if (xNorm.size < 48) return null

            val mlpInput = xNorm.last()
            val lstmInput = Array(48) { i -> xNorm[xNorm.size - 48 + i] }

            val (pDir, pVol, decision) = runner.predict(mlpInput, lstmInput)
            onLog("  \$symbol  pDir=%.3f  pVol=%.3f  → \$decision".format(pDir, pVol))

            if (decision == "FLAT") return null

            val price = candles.last().close
            val side = if (decision == "LONG") "BUY" else "SELL"
            val tradeSide = decision

            val notional = settings.tradeUsdt * settings.leverage * (1 - pVol)
            val qty = roundQty(symbol, notional / price)
            if (qty <= 0) return null

            if (!settings.testnet && settings.apiKey.isNotEmpty()) {
                api.setLeverage(symbol, settings.leverage)
                api.setMarginType(symbol, settings.marginType)
                api.marketOrder(symbol, side, qty)
                val slPrice = if (decision == "LONG") price * 0.98 else price * 1.02
                val slSide = if (decision == "LONG") "SELL" else "BUY"
                api.stopLoss(symbol, slSide, slPrice, qty)
            }

            val trade = Trade(
                symbol = symbol,
                side = tradeSide,
                entryPrice = price,
                quantity = qty
            )
            tradeManager.addOpen(trade)
            onLog("  ✅ \$symbol \$tradeSide @ \$price  qty=\$qty")
            return trade
        } catch (e: Exception) {
            onLog("  ✗ \$symbol hata: \${e.message}")
            return null
        }
    }

    private fun roundQty(symbol: String, qty: Double): Double = when {
        symbol.startsWith("BTC") -> (qty * 1000).toInt() / 1000.0
        symbol.startsWith("ETH") -> (qty * 100).toInt() / 100.0
        else -> (qty * 10).toInt() / 10.0
    }

    suspend fun closeAllPositions(prices: Map<String, Double>) {
        val open = tradeManager.getOpen()
        for (t in open) {
            if (!settings.testnet && settings.apiKey.isNotEmpty()) {
                try {
                    val closeSide = if (t.side == "LONG") "SELL" else "BUY"
                    api.closePosition(t.symbol, closeSide, t.quantity)
                } catch (e: Exception) { onLog("Kapatma hatası: \${e.message}") }
            }
            val p = prices[t.symbol] ?: t.entryPrice
            tradeManager.closeTrade(t.id, p)
        }
        onLog("🔒 Tüm pozisyonlar kapatıldı")
    }
}
