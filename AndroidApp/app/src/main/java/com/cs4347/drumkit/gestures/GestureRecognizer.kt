package com.cs4347.drumkit.gestures

import Sensor.WatchPacket.SensorMessage
import android.util.Log
import com.cs4347.drumkit.transmission.SensorDataSubject
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.lang.Math.abs
import java.util.*
import java.util.concurrent.Semaphore
import android.app.Activity


enum class GestureType {NO_GESTURE, DOWN, UP}
data class Gesture(val type: GestureType, val time: Long)

interface Model {
    /**
     * Feeds data into the model
     * @property accelerationIterator iterator for acceleration data
     * @property gyroIterator iterator for gyroscope data
     * @property count number of times to iterate through the iterator
     */
    fun predict(accelerationIterator: Iterator<SensorMessage>,
                gyroIterator: Iterator<SensorMessage>,
                count: Int): GestureType
}

class GestureRecognizer(activity: Activity) {

    companion object {
        private const val NUM_SENSORS = 2
        private const val HISTORY_SIZE = 200
        private const val TAG = "GestureRecognizer"
        const val WINDOW_SIZE = 50 // number of groups of 5ms data
        const val DATA_ITEMS_PER_MSG = 4 // 3 axes + 1 type (accelerometer, gyroscope)
        const val MODEL_INPUT_SIZE = NUM_SENSORS * WINDOW_SIZE * DATA_ITEMS_PER_MSG

        // 5ms between each message item
        const val MESSAGE_PERIOD = 5
    }

    private val accelerationWindow: LinkedList<SensorMessage> = LinkedList()
    private val gyroscopeWindow: LinkedList<SensorMessage> = LinkedList()
    private val compositeDisposable = CompositeDisposable()
    private val model = TfLiteModel(activity)
    private var skipGestureCount = 0
    private val accelerationHistory: LinkedList<SensorMessage> = LinkedList()

    var returnFakeGestureAfter2SecsOfData = false

    /**
     * Subscribe to gestures & respond on listener
     * Listener is executed by a thread from Schedulers.single()
     */
    fun subscribeToGestures(listener: (Gesture) -> Unit) {
        // all data wrangling & processing is done on one thread to prevent race conditions
        SensorDataSubject.instance.observe()
                .subscribeOn(Schedulers.newThread())
                .map { sensorMsg: SensorMessage ->
                    // returns true if there is sufficient data for predicting gesture
                    processSensorData(sensorMsg)
                }
                .doOnError {
                    Log.e(TAG, "ERROR with gesture recog subscription!!!! \n $it")
                }
                // do not predict gesture if there is insufficient data
                .filter { it }
                .subscribe {
                    // predict a gesture only if we have to
                    val gestureType = when(skipGestureCount) {
                        0 -> {
                            val gestureTypePrediction =
                                    predict(accelerationWindow.iterator(),
                                            gyroscopeWindow.iterator(),
                                            WINDOW_SIZE)

                            // skip slightly smaller than window size
                            if (gestureTypePrediction == GestureType.DOWN)
                                skipGestureCount = WINDOW_SIZE - 5
                            gestureTypePrediction
                        }
                        else -> {
                            skipGestureCount -= 1
                            GestureType.NO_GESTURE
                        }
                    }
                    val gestureTime = accelerationHistory.first.timestamp
                    accelerationWindow.removeFirst()
                    gyroscopeWindow.removeFirst()
                    listener(Gesture(gestureType, gestureTime))
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
     * @return returns true if there is sufficient data for model to take in
     */
    private fun processSensorData(message: SensorMessage): Boolean {
        // TODO: track accel history later on, to do acceleration tricks

        // TODO preprocess data, read json in ../models for the values
        // normalized_data = 2*(row - col_min)/(col_max - col_min) -1

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

        return hasSufficientData
    }

    // TODO: remove after debugging
    private var predictCountDebug = 0
    // 2 * 1000ms / MESSAGE_PERIOD, a gesture every 2s
    private val fakeGestureAfterNCounts = 2 * 1000 / MESSAGE_PERIOD
    private val mockGestureMutex = Semaphore(1, true)

    private fun predict(accelerationIterator: Iterator<SensorMessage>,
                        gyroIterator: Iterator<SensorMessage>,
                        count: Int): GestureType {

        // TODO: remove after debugging
        // gesture debugging code
        if (returnFakeGestureAfter2SecsOfData) {
            mockGestureMutex.acquire()
            predictCountDebug += 1

            if (fakeGestureAfterNCounts == predictCountDebug) {
                Log.d(TAG, "Predicting a fake gesture")
                predictCountDebug = 0
                mockGestureMutex.release()
                return GestureType.DOWN
            } else {
                mockGestureMutex.release()
                return GestureType.NO_GESTURE
            }
        }

        return model.predict(
                accelerationIterator,
                gyroIterator,
                count)
    }
}

