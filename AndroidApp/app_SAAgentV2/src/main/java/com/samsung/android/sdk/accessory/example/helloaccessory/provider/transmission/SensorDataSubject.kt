package com.samsung.android.sdk.accessory.example.helloaccessory.provider.transmission

import io.reactivex.subjects.PublishSubject
import Sensor.WatchPacket.SensorMessage
import io.reactivex.Observable

/**
 * Manually coded singleton for flexibility
 * Sensor data observers should observe this subject
 */
class SensorDataSubject private constructor() {
    private lateinit var subject: PublishSubject<SensorMessage>

    val serviceConnectionListener: ServiceConnectionListener =
            object: ServiceConnectionListener {
                override fun onInit() {
                    reset()
                }

                override fun onReceive(packet: Sensor.WatchPacket) {
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
    }

    private fun reset() {
        if (!subject.hasComplete() || !subject.hasThrowable()) {
            subject.onError(Exception("SensorDataSubject is abruptly reset"))
        }
        subject = PublishSubject.create()
    }

    fun observe(): Observable<SensorMessage> {
        return subject
    }

}