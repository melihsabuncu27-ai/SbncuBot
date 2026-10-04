package com.sbnc.bot.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ModelRunner(context: Context) {
    private val lstm: Interpreter
    private val mlp: Interpreter

    var wLstm = 0.4f
    var wMlp = 0.6f
    var threshold = 0.56f

    init {
        lstm = Interpreter(loadModel(context, "model_lstm.tflite"))
        mlp = Interpreter(loadModel(context, "model_mlp.tflite"))
    }

    private fun loadModel(ctx: Context, name: String): MappedByteBuffer {
        val fd = ctx.assets.openFd(name)
        FileInputStream(fd.fileDescriptor).use { input ->
            return input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    fun predict(mlpInput: DoubleArray, lstmInput: Array<DoubleArray>): Triple<Float, Float, String> {
        val inMlp = Array(1) { FloatArray(mlpInput.size) { mlpInput[it].toFloat() } }
        val outMlpD = Array(1) { FloatArray(1) }
        val outMlpV = Array(1) { FloatArray(1) }
        mlp.runForMultipleInputsOutputs(arrayOf(inMlp), mapOf(0 to outMlpD, 1 to outMlpV))

        val inLstm = Array(1) { Array(lstmInput.size) { i -> FloatArray(lstmInput[i].size) { j -> lstmInput[i][j].toFloat() } } }
        val outLstmD = Array(1) { FloatArray(1) }
        val outLstmV = Array(1) { FloatArray(1) }
        lstm.runForMultipleInputsOutputs(arrayOf(inLstm), mapOf(0 to outLstmD, 1 to outLstmV))

        val pDir = wLstm * outLstmD[0][0] + wMlp * outMlpD[0][0]
        val pVol = wLstm * outLstmV[0][0] + wMlp * outMlpV[0][0]
        val decision = when {
            pDir > threshold -> "LONG"
            pDir < 1 - threshold -> "SHORT"
            else -> "FLAT"
        }
        return Triple(pDir, pVol, decision)
    }

    fun close() { lstm.close(); mlp.close() }
}
