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

// default interval of tempo is 60-120, step size 10
class GestureRecognizer(activity: Activity,
                        private val tempoRange: Pair<Int, Int> = Pair(60, 120),
                        private val tempoStepSize: Int = 10) {

    companion object {
        private const val NUM_SENSORS = 2
        private const val TAG = "GestureRecognizer"

        const val WINDOW_SIZE = 50 // number of groups of 5ms data
        const val DATA_ITEMS_PER_MSG = 4 // 3 axes + 1 type (accelerometer, gyroscope)
        const val MODEL_INPUT_SIZE = NUM_SENSORS * WINDOW_SIZE * DATA_ITEMS_PER_MSG
        const val MESSAGE_PERIOD = 5 // 5ms between each message item

    }

    private val accelerationWindow: LinkedList<SensorMessage> = LinkedList()
    private val gyroscopeWindow: LinkedList<SensorMessage> = LinkedList()
    private val compositeDisposable = CompositeDisposable()
    private var model: Model = TfLiteModel(activity)

    // tempo 60 has cooldown of 900, tempo 120 has cooldown of 400
    private val coolDownRange = Pair(900, 400)
    private val coolDownStepSize = let {
        val numTempoIntervals = (tempoRange.second - tempoRange.first)/tempoStepSize
        (coolDownRange.second - coolDownRange.first)/numTempoIntervals
    }

    private var recognitionCoolDownDuration = coolDownRange.second // ms

    var returnFakeGestureAfter2SecsOfData = false

    fun updateRecognitionCoolDown(tempo: Int) {
        val steps = (tempo - tempoRange.first) / tempoStepSize
        recognitionCoolDownDuration = coolDownRange.first - (steps*coolDownStepSize)
    }

    /**
     * Subscribe to gestures & respond on listener
     * Listener is executed by a thread from Schedulers.single()
     */
    fun subscribeToGestures(initialTempo: Int, listener: (Gesture) -> Unit) {
        // all data wrangling & processing is done on one thread to prevent race conditions
        updateRecognitionCoolDown(initialTempo)

        var gestureDetectedTime = 0L

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
                    val skipGesture = System.currentTimeMillis() < gestureDetectedTime + recognitionCoolDownDuration
                    val gestureType = when(skipGesture) {
                        false -> {
                            val gestureTypePrediction =
                                    predict(accelerationWindow.iterator(),
                                            gyroscopeWindow.iterator(),
                                            WINDOW_SIZE)

                            // skip slightly smaller than window size
                            if (gestureTypePrediction == GestureType.DOWN) {
                                gestureDetectedTime = System.currentTimeMillis()
                            }
                            gestureTypePrediction
                        }
                        true -> {
                            // Log.i(TAG, "skipping gesture prediction")
                            GestureType.NO_GESTURE
                        }
                    }
                    val gestureTime = accelerationWindow.first.timestamp
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

    // debugging code
    private var predictCountDebug = 0
    // 2 * 1000ms / MESSAGE_PERIOD, a gesture every 2s
    private val fakeGestureAfterNCounts = 2 * 1000 / MESSAGE_PERIOD
    private val mockGestureMutex = Semaphore(1, true)

    private fun predict(accelerationIterator: Iterator<SensorMessage>,
                        gyroIterator: Iterator<SensorMessage>,
                        count: Int): GestureType {

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

