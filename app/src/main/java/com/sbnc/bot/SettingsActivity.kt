package com.sbnc.bot

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.sbnc.bot.data.SettingsManager

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val s = SettingsManager(this)

        val etKey = findViewById<EditText>(R.id.etApiKey)
        val etSecret = findViewById<EditText>(R.id.etApiSecret)
        val etLev = findViewById<EditText>(R.id.etLeverage)
        val etUsdt = findViewById<EditText>(R.id.etTradeUsdt)
        val swTestnet = findViewById<Switch>(R.id.swTestnet)
        val btnSave = findViewById<Button>(R.id.btnSave)

        etKey.setText(s.apiKey)
        etSecret.setText(s.apiSecret)
        etLev.setText(s.leverage.toString())
        etUsdt.setText(s.tradeUsdt.toString())
        swTestnet.isChecked = s.testnet

        btnSave.setOnClickListener {
            s.apiKey = etKey.text.toString().trim()
            s.apiSecret = etSecret.text.toString().trim()
            s.leverage = etLev.text.toString().toIntOrNull() ?: 3
            s.tradeUsdt = etUsdt.text.toString().toDoubleOrNull() ?: 100.0
            s.testnet = swTestnet.isChecked
            Toast.makeText(this, "✅ Kaydedildi", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
