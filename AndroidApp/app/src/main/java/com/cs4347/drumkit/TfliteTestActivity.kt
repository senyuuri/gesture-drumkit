package com.cs4347.drumkit

import android.app.Activity
import android.os.Bundle
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder.nativeOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TfliteTestActivity : Activity() {

    private val MODEL_LOCATION = "converted_model.tflite"
    private val outputArray = arrayOf(FloatArray(1))
    // 4 bytes per float, input shape (100,4)
    private val inputBuffer =
            ByteBuffer.allocateDirect(4 *  4 * 100 )
                    .apply { order(nativeOrder()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
    }

    private fun unwindToByteBuffer(floats: FloatArray) {
        inputBuffer.rewind()
        for (f in floats) {
            inputBuffer.putFloat(f)
        }
    }

    override fun onStart() {
        super.onStart()
        val tflite = Interpreter(loadModelFile(this))
        unwindToByteBuffer(FloatArray(400))
        tflite.run(inputBuffer, outputArray)
        Log.i("tflite", "$inputBuffer")
        Log.i("tflite", "${outputArray[0][0]}")
    }

    private fun loadModelFile(activity: Activity): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(MODEL_LOCATION)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
