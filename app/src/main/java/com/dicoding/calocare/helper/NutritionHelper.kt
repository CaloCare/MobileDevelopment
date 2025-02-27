package com.dicoding.calocare.helper

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.dicoding.calocare.R
import com.google.android.gms.tflite.client.TfLiteInitializationOptions
import com.google.android.gms.tflite.gpu.support.TfLiteGpu
import com.google.android.gms.tflite.java.TfLite
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.gpu.GpuDelegateFactory
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class NutritionHelper(
    private val modelName: String = "Nutritional_Evaluation_Model.tflite",
    val context: Context,
    val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var interpreter: InterpreterApi? = null
    private var isGPUSupported: Boolean = false

    init {
        TfLiteGpu.isGpuDelegateAvailable(context).onSuccessTask { gpuAvailable ->
            val optionsBuilder = TfLiteInitializationOptions.builder()
            if (gpuAvailable) {
                optionsBuilder.setEnableGpuDelegateSupport(true)
                isGPUSupported = true
            }
            TfLite.initialize(context, optionsBuilder.build())
        }.addOnSuccessListener {
            loadLocalModel()
        }.addOnFailureListener {
            onError(context.getString(R.string.tflite_is_not_initialized_yet))
        }
    }

    private fun loadLocalModel() {
        try {
            val buffer: ByteBuffer = loadModelFile(context.assets, modelName)
            initializeInterpreter(buffer)
        } catch (ioException: IOException) {
            ioException.printStackTrace()
        }
    }

    private fun initializeInterpreter(model: Any) {
        interpreter?.close()
        try {
            val options = InterpreterApi.Options()
                .setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)
            if (isGPUSupported) {
                options.addDelegateFactory(GpuDelegateFactory())
            }
            if (model is ByteBuffer) {
                interpreter = InterpreterApi.create(model, options)
            }
        } catch (e: Exception) {
            onError(e.message.toString())
            Log.e(TAG, e.message.toString())
        }
    }

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
        assetManager.openFd(modelPath).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
        }
    }

    fun close() {
        interpreter?.close()
    }

    fun evaluateNutrition(calories: Double, proteins: Double, fat: Double, carbohydrate: Double): Pair<Double, Int> {
        // Calculate total nutrition
        val totalNutrition = calories + proteins + fat + carbohydrate

        // Define bins and labels
        val bins = listOf(0.0, 100.0, 300.0, 500.0, 700.0, Double.MAX_VALUE)
        val labels = listOf(1, 2, 3, 4, 5)

        // Determine evaluation category based on bins
        val evaluation = bins.indexOfFirst { totalNutrition < it } - 1
        val category = if (evaluation >= 0) labels[evaluation] else 1 // Default to 1 if out of bounds

        return Pair(totalNutrition, category)
    }

    companion object {
        private const val TAG = "PredictionHelper"
    }
}