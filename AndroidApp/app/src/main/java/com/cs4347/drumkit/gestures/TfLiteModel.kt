package com.cs4347.drumkit.gestures

import android.app.Activity
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder.nativeOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TfLiteModel(activity: Activity): Model {

    private val MODEL_LOCATION = "converted_model.tflite"
    private val outputArray = arrayOf(FloatArray(3))

    // 4 bytes per float
    private val inputBuffer = ByteBuffer.allocateDirect(GestureRecognizer.MODEL_INPUT_SIZE * 4)
                    .apply { order(nativeOrder()) }

    private val tflite = Interpreter(loadModelFile(activity))

    private fun loadModelFile(activity: Activity): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(MODEL_LOCATION)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private val oneHotToGestureLabel = listOf(GestureType.NO_GESTURE, GestureType.DOWN, GestureType.UP)

    private val normalizationData = hashMapOf(
            "col_min" to floatArrayOf(0.0f, -356.44f, -1178.73f, -294.77f),
            "col_max" to floatArrayOf(1.0f, 314.86002f, 666.05f, 216.51f)
    )

    private fun normalizationTransform(input: Float, transform_min: Float, transform_max: Float): Float {
        return 2*(input - transform_min)/(transform_max - transform_min)-1
    }

    private fun processData(message: Sensor.WatchPacket.SensorMessage): FloatArray {
        val resultArray = FloatArray(4)
        for (i in 0 until GestureRecognizer.DATA_ITEMS_PER_MSG) {
            val input = when (i) {
                0 -> message.sensorType.number.toFloat()
                else -> message.getData(i-1)
            }
            resultArray[i] = normalizationTransform(input,
                    normalizationData["col_min"]!![i-1],
                    normalizationData["col_max"]!![i-1])
        }

        return resultArray
    }

    private fun unwindToByteBuffer(floats: FloatArray) {
        inputBuffer.rewind()
        for (f in floats) {
            inputBuffer.putFloat(f)
        }
    }


    override fun predict(accelerationIterator: Iterator<Sensor.WatchPacket.SensorMessage>,
                         gyroIterator: Iterator<Sensor.WatchPacket.SensorMessage>,
                         count: Int): GestureType {
        inputBuffer.rewind()
        for (i in 0 until count) {
            for (data in processData(accelerationIterator.next())) {
                inputBuffer.putFloat(data)
            }
        }
        for (i in 0 until count) {
            for (data in processData(gyroIterator.next())) {
                inputBuffer.putFloat(data)
            }
        }
        tflite.run(inputBuffer, outputArray)
        var maxId = 0
        var maxVal = outputArray[0][0]
        for (i in 0 until outputArray[0].size) {
            val newVal = outputArray[0][i]
            if (newVal > maxVal) {
                maxId = i
                maxVal = newVal
            }
        }
        return oneHotToGestureLabel[maxId]
    }

}