package com.samsung.android.sdk.accessory.example.helloaccessory.provider

import Sensors.Sensor
import android.util.Log
import com.google.protobuf.InvalidProtocolBufferException
import com.samsung.android.sdk.accessory.SASocket

class ServiceConnection : SASocket(ServiceConnection::class.java.name) {
    lateinit var listener: ServiceConnectionListener

    companion object {
        private val TAG = "ServiceConnection"
    }

    override fun onError(channelId: Int, errorMessage: String, errorCode: Int) {}

    override fun onReceive(channelId: Int, data: ByteArray) {
        try {
            val sm = Sensor.SensorMessage.parseFrom(data)
            listener.onReceive(sm)
        } catch (e: InvalidProtocolBufferException) {
            Log.e(TAG, e.message)
        }
    }

    override fun onServiceConnectionLost(reason: Int) {
        listener.onConnectionLost()
    }
}

interface ServiceConnectionListener {
    fun onReceive(message: Sensor.SensorMessage)
    fun onConnectionLost()
}
