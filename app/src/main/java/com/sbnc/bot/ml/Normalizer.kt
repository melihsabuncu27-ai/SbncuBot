package com.sbnc.bot.ml

import kotlin.math.sqrt

class Normalizer {
    fun normalize(
        features: Map<String, DoubleArray>,
        names: List<String>,
        window: Int = 720,
        minPeriods: Int = 168
    ): Array<DoubleArray> {
        val n = features[names[0]]!!.size
        val out = Array(n) { DoubleArray(names.size) }

        for (k in names.indices) {
            val x = features[names[k]] ?: continue
            for (i in 0 until n) {
                val start = maxOf(0, i - window + 1)
                val len = i - start + 1
                if (len < minPeriods) { out[i][k] = 0.0; continue }
                var m = 0.0
                for (j in start..i) m += x[j]
                m /= len
                var v = 0.0
                for (j in start..i) v += (x[j] - m) * (x[j] - m)
                v /= len
                out[i][k] = (x[i] - m) / (sqrt(v) + 1e-9)
            }
        }
        return out
    }
}
