package com.cs4347.drumkit.gestures

import Sensor.WatchPacket.SensorMessage
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.cs4347.drumkit.transmission.SensorDataSubject
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.lang.Math.abs
import java.sql.Time
import java.util.*
import java.util.concurrent.Semaphore

enum class GestureType {DOWN, UP, LEFT, RIGHT, FRONT, BACK}
data class Gesture(val type: GestureType, val time: Long)

class GestureRecognizer {

    companion object {
        const val WINDOW_SIZE = 50 // number of groups of 5ms data
        private const val NUM_SENSORS = 2
        private const val HISTORY_SIZE = 200
        private val DATA_ITEMS_PER_MSG = 3
        private val MODEL_INPUT_SIZE = NUM_SENSORS * WINDOW_SIZE * DATA_ITEMS_PER_MSG
        private const val TAG = "GestureRecognizer"

        // 5ms between each message item
        const val MESSAGE_PERIOD = 5
    }

    private val accelerationWindow: LinkedList<SensorMessage> = LinkedList()
    private val gyroscopeWindow: LinkedList<SensorMessage> = LinkedList()
    private val accelerationHistory: LinkedList<SensorMessage> = LinkedList()
    private val compositeDisposable = CompositeDisposable()
    var returnFakeGestureAfter2SecsOfData = false

    /**
     * Subscribe to gestures & respond on listener
     * Listener is executed by the computation scheduler (multithreaded)
     */
    fun subscribeToGestures(listener: (Gesture) -> Unit) {
        // all data wrangling is done on one thread to prevent race conditions
        // prediction is done on multiple threads (num threads = num cores)
        SensorDataSubject.instance.observe()
                .subscribeOn(Schedulers.single())
                .map { sensorMsg: SensorMessage ->
                    // rxjava2 doesn't let us pass nulls
                    // wrap it in a pair instead
                    processSensorData(sensorMsg)
                }
                .doOnError {
                    Log.e(TAG, "ERROR with gesture recog subscription!!!! \n $it")
                    //Toast.makeText(context,
                    //        "Data stream has died!", Toast.LENGTH_SHORT).show()
                }
                .filter { potentialModelInput ->
                    // discard empty inputs
                    potentialModelInput.first
                }
                .observeOn(Schedulers.computation())
                .subscribe { modelInput: Pair<Boolean, FloatArray?> ->
                    // cast is safe, empty inputs are already filtered out
                    predict(modelInput.second!!)
                            ?.let { gesture -> listener(gesture) }
                }
                .apply {
                    compositeDisposable.add(this)
                }
    }

    fun stopSubscriptionToGestures() {
        compositeDisposable.dispose()
    }

    /**
     * Pop left for w2 until synced with w1
     */
    private fun syncWindows(w1: LinkedList<SensorMessage>, w2: LinkedList<SensorMessage>) {
        while (w2.size > 0 && abs(w2.first.timestamp - w1.first.timestamp) > MESSAGE_PERIOD) {
            w2.removeFirst()
        }
    }

    /**
     * Processes raw sensor messages
     * @return data processed for model input, returned only when enough raw messages are processed
     */
    private fun processSensorData(message: SensorMessage): Pair<Boolean, FloatArray?> {
        // TODO: track accel history later on, to do acceleration tricks

        val justStartedLoadingAcceleration =
                accelerationWindow.size == 0
                && gyroscopeWindow.size > 0
                && message.sensorType == SensorMessage.SensorType.ACCELEROMETER

        val justStartedLoadingGyroscope =
                accelerationWindow.size > 0
                && gyroscopeWindow.size == 0
                        && message.sensorType == SensorMessage.SensorType.GYROSCOPE

        // append the data
        when (message.sensorType) {
            SensorMessage.SensorType.GYROSCOPE -> gyroscopeWindow.addLast(message)
            SensorMessage.SensorType.ACCELEROMETER -> accelerationWindow.addLast(message)
            else -> throw IllegalArgumentException("unhandled sensor type")
        }

        // sync windows if needed
        if (justStartedLoadingAcceleration) {
            syncWindows(accelerationWindow, gyroscopeWindow)
        }
        if (justStartedLoadingGyroscope) {
            syncWindows(gyroscopeWindow, accelerationWindow)
        }

        // convert sensor data into model input data & queue it for processing
        val hasSufficientData = !(accelerationWindow.size < WINDOW_SIZE
                || gyroscopeWindow.size < WINDOW_SIZE)

        if (hasSufficientData) {
            val processedData = FloatArray(MODEL_INPUT_SIZE)
            val gyIterator = gyroscopeWindow.iterator()
            val accelIterator = accelerationWindow.iterator()

            // TODO: verify that model takes data in this order
            var inputIdx = 0
            for (i in 0 until WINDOW_SIZE) {
                for (gyData in gyIterator.next().dataList) {
                    processedData[inputIdx] = gyData
                    inputIdx += 1
                }
                for (accelData in accelIterator.next().dataList) {
                    processedData[inputIdx] = accelData
                    inputIdx += 1
                }
            }
            accelerationWindow.removeFirst()
            gyroscopeWindow.removeFirst()
            return Pair(true, processedData)

        } else {
            return Pair(false, null)
        }
    }

    // TODO: remove after debugging
    private var predictCountDebug = 0
    // 2 * 1000ms / MESSAGE_PERIOD, a gesture every 2s
    private val fakeGestureAfterNCounts = 2 * 1000 / MESSAGE_PERIOD
    private val mockGestureMutex = Semaphore(1, true)

    private fun predict(data: FloatArray): Gesture? {
        // ensure data queue uses the same data type as what is required here
        // so we don't waste time copying data
        // ignore subsequent requests to predict if a gesture is detected
        // TODO: supposed to predict gesture with data, $data"
        if (returnFakeGestureAfter2SecsOfData) {
            mockGestureMutex.acquire()
            predictCountDebug += 1

            if (fakeGestureAfterNCounts == predictCountDebug) {
                Log.d(TAG, "Predicting a fake gesture")
                predictCountDebug = 0
                mockGestureMutex.release()
                return Gesture(GestureType.DOWN, System.currentTimeMillis());
            } else {
                mockGestureMutex.release()
                return null
            }
        }
        return null
    }

}

