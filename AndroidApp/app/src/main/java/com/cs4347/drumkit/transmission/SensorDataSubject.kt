package com.cs4347.drumkit.transmission

import io.reactivex.subjects.PublishSubject
import Sensor.WatchPacket.SensorMessage
import android.util.Log
import io.reactivex.Observable

/**
 * Manually coded singleton for flexibility
 * Sensor data observers should observe this subject
 */
class SensorDataSubject private constructor() {
    private var subject: PublishSubject<SensorMessage> = PublishSubject.create()

    val serviceConnectionListener: ServiceConnectionListener =
            object: ServiceConnectionListener {

                private var prevPacketTime = Long.MIN_VALUE

                override fun onInit() {
                    // reset after init doesn't matter, there should not be observers
                    reset()
                }

                override fun onReceive(packet: Sensor.WatchPacket) {
                    // TODO: for debugging, delete before submission
                    val firstMsg = packet.getMessages(0)
                    // Log.d(TAG, "Packet's First Msg: ${firstMsg.sensorType}, " +
                    //         "Time: ${firstMsg.timestamp}, " +
                    //         "Data: ${firstMsg.dataList}")

                    // assumes no packet is dropped (should be a safe assumption)
                    // assert that packets are received in chronological order
                    val currPacketTime = packet.getMessages(0).timestamp
                    if (prevPacketTime > currPacketTime) {
                        throw AssertionError("Order of WatchPackets received is not chronological, handle it!")
                    }
                    prevPacketTime = currPacketTime

                    packet.messagesList.forEach {
                        subject.onNext(it)
                    }
                }

                override fun onConnectionLost() {
                    subject.onError(Exception("Connection lost"))
                }
            }

    companion object {
        val instance = SensorDataSubject()
        private val TAG = "SensorDataSubject"
    }

    /**
     * Reset to refresh internal subject
     * A subject cannot send data when it has completed, or thrown an error.
     */
    private fun reset() {
        if (!subject.hasComplete() || !subject.hasThrowable()) {
            Log.e(TAG, "SensorDataSubject is abruptly reset")
        }
        subject = PublishSubject.create()
    }

    fun observe(): Observable<SensorMessage> {
        return subject
    }

}