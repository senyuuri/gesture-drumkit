package com.cs4347.drumkit.transmission

import android.util.Log
import com.google.protobuf.InvalidProtocolBufferException
import com.samsung.android.sdk.accessory.SASocket

/**
 * Samsung socket class, don't want to touch this much
 */
class ServiceConnection : SASocket(ServiceConnection::class.java.name) {
    var listener: ServiceConnectionListener? = null
    set(value) {
        value?.onInit()
        field = value
    }

    companion object {
        private val TAG = "ServiceConnection"
    }

    override fun onError(channelId: Int, errorMessage: String, errorCode: Int) {}

    override fun onReceive(channelId: Int, data: ByteArray) {
        try {
            val watchPacket = Sensor.WatchPacket.parseFrom(data)
            listener?.onReceive(watchPacket)

        } catch (e: InvalidProtocolBufferException) {
            Log.e(TAG, e.message)
        }
    }

    override fun onServiceConnectionLost(reason: Int) {
        listener?.onConnectionLost()
    }
}

interface ServiceConnectionListener {
    fun onInit()
    fun onReceive(packet: Sensor.WatchPacket)
    fun onConnectionLost()
}
