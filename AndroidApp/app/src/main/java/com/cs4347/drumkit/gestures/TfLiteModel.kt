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

    private val oneHotToGestureLabel = listOf(GestureType.UP, GestureType.DOWN, GestureType.NO_GESTURE)

    /* Normalization was not really helpful in experiments
    private fun normalizationTransform(input: Float, transform_min: Float, transform_max: Float): Float {
        return 2*(input - transform_min)/(transform_max - transform_min)-1
    }
    private val normalizationData = hashMapOf(
            "col_min" to floatArrayOf(0.0f, -356.44f, -1178.73f, -294.77f),
            "col_max" to floatArrayOf(1.0f, 314.86002f, 666.05f, 216.51f)
    )

    private fun processData(message: Sensor.WatchPacket.SensorMessage): FloatArray {
        val resultArray = FloatArray(4)
        for (i in 0 until GestureRecognizer.DATA_ITEMS_PER_MSG) {
            val input = when (i) {
                0 -> message.sensorType.number.toFloat()
                else -> message.getData(i-1)
            }
            resultArray[i] = normalizationTransform(input,
                    normalizationData["col_min"]!![i],
                    normalizationData["col_max"]!![i])
        }

        return resultArray
    }
    */

    override fun predict(accelerationIterator: Iterator<Sensor.WatchPacket.SensorMessage>,
                         gyroIterator: Iterator<Sensor.WatchPacket.SensorMessage>,
                         count: Int, swapAxes: Boolean): GestureType {
        inputBuffer.rewind()
        //* @property swapAxes should swap axes (up/down=> left/right ay, az swap)
        for (i in 0 until count) {
            val dataList = accelerationIterator.next().dataList
            for (j in 0 until dataList.size) {
                if (swapAxes && j == 1) {
                    inputBuffer.putFloat(dataList[1]-9.81f)
                } else if (swapAxes && j == 2) {
                    inputBuffer.putFloat(dataList[2]-9.81f)
                } else {
                    inputBuffer.putFloat(dataList[j])
                }
            }
        }
        for (i in 0 until count) {
            val dataList = gyroIterator.next().dataList
            for (j in 0 until dataList.size) {
                inputBuffer.putFloat(dataList[j])
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
        val prediction = oneHotToGestureLabel[maxId]

        if (swapAxes) {
            if (prediction == GestureType.UP) {
                return GestureType.RIGHT
            }
            if (prediction == GestureType.DOWN) {
                return GestureType.LEFT
            }
            return GestureType.NO_GESTURE
        } else {
            return prediction
        }
    }

}