package com.samsung.android.sdk.accessory.example.helloaccessory.provider

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
            Log.i(TAG, "got a msg")
            val watchPacket = Sensor.WatchPacket.parseFrom(data)

            listener.onReceive(watchPacket)
        } catch (e: InvalidProtocolBufferException) {
            Log.e(TAG, e.message)
        }
    }

    override fun onServiceConnectionLost(reason: Int) {
        listener.onConnectionLost()
    }
}

interface ServiceConnectionListener {
    fun onReceive(packet: Sensor.WatchPacket)
    fun onConnectionLost()
}
