/*
 * Copyright (c) 2018 Samsung Electronics Co., Ltd. All rights reserved.
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that 
 * the following conditions are met:
 * 
 *     * Redistributions of source code must retain the above copyright notice, 
 *       this list of conditions and the following disclaimer. 
 *     * Redistributions in binary form must reproduce the above copyright notice, 
 *       this list of conditions and the following disclaimer in the documentation and/or 
 *       other materials provided with the distribution. 
 *     * Neither the name of Samsung Electronics Co., Ltd. nor the names of its contributors may be used to endorse
 *       or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.samsung.android.sdk.accessory.example.helloaccessory.provider

import android.content.Context
import android.os.Handler
import android.util.Log
import android.widget.Toast

import com.google.protobuf.InvalidProtocolBufferException
import com.samsung.android.sdk.SsdkUnsupportedException
import com.samsung.android.sdk.accessory.SA
import com.samsung.android.sdk.accessory.SAAgentV2
import com.samsung.android.sdk.accessory.SAAuthenticationToken
import com.samsung.android.sdk.accessory.SAPeerAgent
import com.samsung.android.sdk.accessory.SASocket

import Sensors.Sensor.SensorMessage


class ProviderService(context: Context) : SAAgentV2(TAG, context, SASOCKET_CLASS) {
    private var mConnectionHandler: ServiceConnection? = null
    private val mHandler = Handler()
    private var mContext: Context? = null

    init {
        mContext = context

        val mAccessory = SA()
        try {
            mAccessory.initialize(mContext!!)
        } catch (e: SsdkUnsupportedException) {
            // try to handle SsdkUnsupportedException
            if (processUnsupportedException(e)) {
                return
            }
        } catch (e1: Exception) {
            e1.printStackTrace()
            /*
             * Your application can not use Samsung Accessory SDK. Your application should work smoothly
             * without using this SDK, or you may want to notify user and close your application gracefully
             * (release resources, stop Service threads, close UI thread, etc.)
             */
        }

    }

    override fun onFindPeerAgentsResponse(peerAgents: Array<SAPeerAgent>?, result: Int) {
        Log.d(TAG, "onFindPeerAgentResponse : result =$result")
    }

    override fun onServiceConnectionRequested(peerAgent: SAPeerAgent?) {
        if (peerAgent != null) {
            mHandler.post { Toast.makeText(mContext, R.string.ConnectionAcceptedMsg, Toast.LENGTH_SHORT).show() }
            acceptServiceConnectionRequest(peerAgent)
        }
    }

    override fun onServiceConnectionResponse(peerAgent: SAPeerAgent?, socket: SASocket?, result: Int) {
        if (result == SAAgentV2.CONNECTION_SUCCESS) {
            if (socket != null) {
                mConnectionHandler = socket as ServiceConnection?
            }
        } else if (result == SAAgentV2.CONNECTION_ALREADY_EXIST) {
            Log.e(TAG, "onServiceConnectionResponse, CONNECTION_ALREADY_EXIST")
        }
    }

    override fun onAuthenticationResponse(peerAgent: SAPeerAgent?, authToken: SAAuthenticationToken?, error: Int) {
        /*
         * The authenticatePeerAgent(peerAgent) API may not be working properly depending on the firmware
         * version of accessory device. Please refer to another sample application for Security.
         */
    }

    override fun onError(peerAgent: SAPeerAgent?, errorMessage: String?, errorCode: Int) {
        super.onError(peerAgent, errorMessage, errorCode)
    }

    private fun processUnsupportedException(e: SsdkUnsupportedException): Boolean {
        e.printStackTrace()
        val errType = e.type
        if (errType == SsdkUnsupportedException.VENDOR_NOT_SUPPORTED || errType == SsdkUnsupportedException.DEVICE_NOT_SUPPORTED) {
            /*
             * Your application can not use Samsung Accessory SDK. You application should work smoothly
             * without using this SDK, or you may want to notify user and close your app gracefully (release
             * resources, stop Service threads, close UI thread, etc.)
             */
        } else if (errType == SsdkUnsupportedException.LIBRARY_NOT_INSTALLED) {
            Log.e(TAG, "You need to install Samsung Accessory SDK to use this application.")
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_REQUIRED) {
            Log.e(TAG, "You need to update Samsung Accessory SDK to use this application.")
        } else if (errType == SsdkUnsupportedException.LIBRARY_UPDATE_IS_RECOMMENDED) {
            Log.e(TAG, "We recommend that you update your Samsung Accessory SDK before using this application.")
            return false
        }
        return true
    }

    inner class ServiceConnection : SASocket(ServiceConnection::class.java!!.getName()) {

        override fun onError(channelId: Int, errorMessage: String, errorCode: Int) {}

        override fun onReceive(channelId: Int, data: ByteArray) {
            if (mConnectionHandler == null) {
                return
            }

            try {
                val sm = SensorMessage.parseFrom(data)
                val txt = sm.getData(0).toString() + " " + sm.sensorType + " " + sm.timestamp
                Toast.makeText(mContext, txt, Toast.LENGTH_LONG).show()
            } catch (e: InvalidProtocolBufferException) {
                Log.e(TAG, e.message)
            }

        }

        override fun onServiceConnectionLost(reason: Int) {
            mConnectionHandler = null
            mHandler.post { Toast.makeText(mContext, R.string.ConnectionTerminateddMsg, Toast.LENGTH_SHORT).show() }
        }
    }

    companion object {
        private val TAG = "HelloAccessory(P)"
        private val SASOCKET_CLASS = ServiceConnection::class.java
    }
}
