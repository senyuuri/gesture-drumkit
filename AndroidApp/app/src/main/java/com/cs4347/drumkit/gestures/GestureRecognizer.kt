package com.cs4347.drumkit.gestures

import io.reactivex.Observable
import Sensor.WatchPacket.SensorMessage
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.lang.Math.abs
import java.sql.Time
import java.util.*
import kotlin.collections.ArrayList

enum class GestureType {DOWN, UP, LEFT, RIGHT, FRONT, BACK}
data class Gesture(val type: GestureType, val time: Time)

class GestureRecognizer {

    companion object {
        private const val WINDOW_SIZE = 10
        private const val HISTORY_SIZE = 200

        // 5ms between each message item
        private const val MESSAGE_PERIOD = 5
    }

    private val accelerationWindow: LinkedList<SensorMessage> = LinkedList()
    private val gyroscopeWindow: LinkedList<SensorMessage> = LinkedList()
    private val dataQueue: LinkedList<ArrayList<Float>> = LinkedList()
    private val accelerationHistory: LinkedList<SensorMessage> = LinkedList()

    private var disposable: Disposable? = null

    /**
     * Assumes that messages from observable always come in chronological order
     */
    fun subscribe(observable: Observable<SensorMessage>, listener: GestureListener) {
        disposable = observable.subscribeOn(Schedulers.io())
                .doOnNext { loadData(it) }
                .observeOn(Schedulers.computation())
                .subscribe {
                    if (dataQueue.size > 0) {
                        predict(dataQueue.poll())?.let {
                            listener.gestureDetected(it)
                        }
                    }
                }

    }

    fun dispose() {
        disposable?.dispose()
    }

    /**
     * Pop left for w2 until synced with w1
     */
    private fun syncWindows(w1: LinkedList<SensorMessage>, w2: LinkedList<SensorMessage>) {
        while (w2.size > 0 && abs(w2.first.timestamp - w1.first.timestamp) > MESSAGE_PERIOD) {
            w2.removeFirst()
        }
    }

    private fun loadData(message: SensorMessage) {
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

        // add data item
        val shouldAddQueueItem = !(accelerationWindow.size < WINDOW_SIZE
                || gyroscopeWindow.size < WINDOW_SIZE)

        if (shouldAddQueueItem) {
            val data = ArrayList<Float>()
            for (i in 0.. WINDOW_SIZE) {
                // what order should the data be in?
                // use iterators for this
                TODO("load data into data queue")
            }
            dataQueue.add(data)
            accelerationWindow.removeFirst()
            gyroscopeWindow.removeFirst()
        }
    }


    private fun predict(data: ArrayList<Float>): Gesture? {
        // ensure data queue uses the same data type as what is required here
        // so we don't waste time copying data
        TODO("recognition / inference code here")
    }

}

interface GestureListener {
    fun gestureDetected(gesture: Gesture)
}
