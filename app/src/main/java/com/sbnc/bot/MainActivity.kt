package com.sbnc.bot

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sbnc.bot.api.BinanceWebSocket
import com.sbnc.bot.data.SettingsManager
import com.sbnc.bot.data.TradeManager
import com.sbnc.bot.ml.ModelRunner
import com.sbnc.bot.trading.TradingLoop
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var settings: SettingsManager
    private lateinit var tradeManager: TradeManager
    private lateinit var runner: ModelRunner
    private lateinit var loop: TradingLoop
    private var ws: BinanceWebSocket? = null

    private val prices = mutableMapOf<String, Double>()
    private val logs = mutableListOf<String>()
    private val symbols = listOf(
        "BTCUSDT","ETHUSDT","BNBUSDT","XRPUSDT","ADAUSDT","LTCUSDT","TRXUSDT",
        "LINKUSDT","ETCUSDT","DOGEUSDT","SOLUSDT","DOTUSDT","AVAXUSDT","ATOMUSDT",
        "UNIUSDT","FILUSDT","NEARUSDT","AAVEUSDT","ALGOUSDT","VETUSDT","FTMUSDT",
        "HBARUSDT","GRTUSDT","STXUSDT","SANDUSDT","MANAUSDT","THETAUSDT","EOSUSDT",
        "XLMUSDT","CRVUSDT"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = SettingsManager(this)
        tradeManager = TradeManager(this)
        runner = ModelRunner(this)
        loop = TradingLoop(this, settings, tradeManager, runner) { msg -> addLog(msg) }

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnScan).setOnClickListener {
            lifecycleScope.launch { runScan() }
        }
        findViewById<Button>(R.id.btnCloseAll).setOnClickListener {
            lifecycleScope.launch {
                loop.closeAllPositions(prices)
                refreshUI()
            }
        }
        findViewById<Button>(R.id.btnReset).setOnClickListener {
            tradeManager.resetStats()
            addLog("🧹 PnL sıfırlandı")
            refreshUI()
        }

        ws = BinanceWebSocket(settings.testnet) { sym, p ->
            prices[sym] = p
            runOnUiThread { refreshUI() }
        }
        ws?.subscribe(symbols)

        refreshUI()

        lifecycleScope.launch {
            while (isActive) {
                delay(60_000)
                runScan()
            }
        }
    }

    private suspend fun runScan() = withContext(Dispatchers.Default) {
        addLog("═══ Tarama başladı ═══")
        for (sym in symbols) {
            loop.scanSymbol(sym)
        }
        withContext(Dispatchers.Main) { refreshUI() }
    }

    private fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logs.add(0, "[\$time] \$msg")
        if (logs.size > 100) logs.removeAt(logs.size - 1)
        runOnUiThread {
            findViewById<TextView>(R.id.tvLogs).text = logs.joinToString("\n")
        }
    }

    private fun refreshUI() {
        val balance = tradeManager.balance()
        val openPnl = tradeManager.getOpen().also { list ->
            list.forEach { t -> prices[t.symbol]?.let { t.updatePnl(it) } }
        }.sumOf { it.pnl }
        val totalPnl = tradeManager.totalPnl()

        findViewById<TextView>(R.id.tvBalance).text = "%.2f USDT".format(balance)
        findViewById<TextView>(R.id.tvOpenPnl).text = "%+.2f USDT".format(openPnl)
        findViewById<TextView>(R.id.tvTotalPnl).text = "%+.2f USDT".format(totalPnl)

        val container = findViewById<LinearLayout>(R.id.positionsContainer)
        container.removeAllViews()
        for (t in tradeManager.getOpen()) {
            val view = LayoutInflater.from(this).inflate(R.layout.item_position, container, false)
            view.findViewById<TextView>(R.id.tvSymbol).text = t.symbol
            val sideColor = if (t.side == "LONG") 0xFF00C853.toInt() else 0xFFD50000.toInt()
            view.findViewById<TextView>(R.id.tvSide).apply {
                text = t.side; setTextColor(sideColor)
            }
            prices[t.symbol]?.let { t.updatePnl(it) }
            view.findViewById<TextView>(R.id.tvEntry).text = "Giriş: %.4f".format(t.entryPrice)
            view.findViewById<TextView>(R.id.tvCurrent).text = "Şu an: %.4f".format(prices[t.symbol] ?: 0.0)
            view.findViewById<TextView>(R.id.tvPnl).apply {
                text = "%+.2f".format(t.pnl)
                setTextColor(if (t.pnl >= 0) 0xFF00C853.toInt() else 0xFFD50000.toInt())
            }
            container.addView(view)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ws?.close()
        runner.close()
    }
}
