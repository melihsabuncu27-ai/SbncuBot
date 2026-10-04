package com.sbnc.bot.ml

import com.sbnc.bot.api.Candle
import kotlin.math.*


class FeatureEngine {

    fun computeFeatures(candles: List<Candle>): Map<String, DoubleArray> {
        val n = candles.size
        val open   = DoubleArray(n) { candles[it].open }
        val high   = DoubleArray(n) { candles[it].high }
        val low    = DoubleArray(n) { candles[it].low }
        val close  = DoubleArray(n) { candles[it].close }
        val volume = DoubleArray(n) { candles[it].volume }

        val logret = DoubleArray(n)
        for (i in 1 until n) logret[i] = ln(close[i] / close[i - 1])
        val absret = DoubleArray(n) { abs(logret[it]) }

        val f = mutableMapOf<String, DoubleArray>()
        f["open"] = open; f["high"] = high; f["low"] = low
        f["close"] = close; f["volume"] = volume

        for (w in listOf(6, 12, 24, 48, 96)) {
            f["rv_\$w"] = rollingStd(logret, w)
            f["absr_\$w"] = rollingMean(absret, w)
        }

        val sq = DoubleArray(n) { logret[it] * logret[it] }
        val sumSq = rollingMean(sq, 24)
        f["rv_sq_24"] = DoubleArray(n) { sqrt(sumSq[it] * 24) }

        val hl = DoubleArray(n) { ln(high[it] / low[it]) }
        val hl2 = DoubleArray(n) { hl[it] * hl[it] }
        val park = rollingMean(hl2, 24)
        f["park_24"] = DoubleArray(n) { sqrt(park[it] / (4 * ln(2.0))) }

        val co = DoubleArray(n) { ln(close[it] / open[it]) }
        val co2 = DoubleArray(n) { co[it] * co[it] }
        val gk = DoubleArray(n)
        for (i in 23 until n) {
            var s = 0.0
            for (j in i - 23..i) s += 0.5 * hl2[j] - (2 * ln(2.0) - 1) * co2[j]
            gk[i] = sqrt(s / 24)
        }
        f["gk_24"] = gk

        f["vol_of_vol"] = rollingStd(f["rv_24"]!!, 24)

        val hlRange = DoubleArray(n) { (high[it] - low[it]) / close[it] }
        f["hl_range"] = hlRange
        f["hl_ma24"] = rollingMean(hlRange, 24)

        val volPct = DoubleArray(n)
        for (i in 1 until n) volPct[i] = (volume[i] - volume[i-1]) / volume[i-1]
        f["vol_ma"] = rollingStd(volPct, 24)

        val up = DoubleArray(n); val dn = DoubleArray(n)
        for (i in 1 until n) {
            val d = close[i] - close[i-1]
            up[i] = if (d > 0) d else 0.0
            dn[i] = if (d < 0) -d else 0.0
        }
        val upM = rollingMean(up, 14); val dnM = rollingMean(dn, 14)
        f["rsi"] = DoubleArray(n) { 100 - 100 / (1 + upM[it] / (dnM[it] + 1e-9)) }

        f["rv_d"]  = f["rv_24"]!!
        f["rv_w"]  = rollingStd(logret, 24*7)
        f["rv_m"]  = rollingStd(logret, 24*30)
        f["rv_d_w"] = divArrays(f["rv_d"]!!, f["rv_w"]!!)
        f["rv_w_m"] = divArrays(f["rv_w"]!!, f["rv_m"]!!)

        val bip = DoubleArray(n)
        for (i in 24 until n) {
            var s = 0.0
            for (j in i-23..i) s += absret[j] * absret[j-1]
            bip[i] = (s / 24) * (PI / 2)
        }
        f["bipower"] = bip
        val jump = DoubleArray(n) { maxOf(f["rv_d"]!![it]*f["rv_d"]!![it] - bip[it], 0.0) }
        f["jump"] = jump
        f["jump_r"] = DoubleArray(n) { jump[it] / (f["rv_d"]!![it]*f["rv_d"]!![it] + 1e-9) }

        f["skew_24"] = rollingSkew(logret, 24)
        f["kurt_24"] = rollingKurt(logret, 24)

        f["mom_6"]  = logDiff(close, 6)
        f["mom_24"] = logDiff(close, 24)
        f["mom_96"] = logDiff(close, 96)

        val ma12 = rollingMean(close, 12); val ma24 = rollingMean(close, 24)
        f["trend_12"] = DoubleArray(n) { ma12[it] / close[it] - 1 }
        f["trend_24"] = DoubleArray(n) { ma24[it] / close[it] - 1 }

        val volMom = DoubleArray(n)
        for (i in 24 until n) volMom[i] = (volume[i] - volume[i-24]) / volume[i-24]
        f["vol_mom"] = volMom

        return f
    }

    private fun rollingMean(x: DoubleArray, w: Int): DoubleArray {
        val n = x.size; val out = DoubleArray(n)
        for (i in w - 1 until n) {
            var s = 0.0
            for (j in i - w + 1..i) s += x[j]
            out[i] = s / w
        }
        return out
    }

    private fun rollingStd(x: DoubleArray, w: Int): DoubleArray {
        val n = x.size; val out = DoubleArray(n)
        for (i in w - 1 until n) {
            var m = 0.0
            for (j in i - w + 1..i) m += x[j]
            m /= w
            var v = 0.0
            for (j in i - w + 1..i) v += (x[j] - m) * (x[j] - m)
            out[i] = sqrt(v / w)
        }
        return out
    }

    private fun rollingSkew(x: DoubleArray, w: Int): DoubleArray {
        val n = x.size; val out = DoubleArray(n)
        for (i in w - 1 until n) {
            val slice = x.copyOfRange(i - w + 1, i + 1)
            val m = slice.average()
            val std = sqrt(slice.map { (it - m)*(it - m) }.sum() / w)
            out[i] = if (std < 1e-9) 0.0
                     else slice.map { ((it - m)/std).pow(3) }.sum() / w
        }
        return out
    }

    private fun rollingKurt(x: DoubleArray, w: Int): DoubleArray {
        val n = x.size; val out = DoubleArray(n)
        for (i in w - 1 until n) {
            val slice = x.copyOfRange(i - w + 1, i + 1)
            val m = slice.average()
            val std = sqrt(slice.map { (it - m)*(it - m) }.sum() / w)
            out[i] = if (std < 1e-9) 0.0
                     else slice.map { ((it - m)/std).pow(4) }.sum() / w - 3
        }
        return out
    }

    private fun logDiff(x: DoubleArray, lag: Int): DoubleArray {
        val n = x.size; val out = DoubleArray(n)
        for (i in lag until n) out[i] = ln(x[i] / x[i - lag])
        return out
    }

    private fun divArrays(a: DoubleArray, b: DoubleArray): DoubleArray =
        DoubleArray(a.size) { a[it] / (b[it] + 1e-9) }
}
