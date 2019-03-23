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

package com.samsung.android.sdk.accessory.example.helloaccessory.provider.transmission

import android.content.Context
import android.util.Log
import android.widget.Toast

import com.samsung.android.sdk.SsdkUnsupportedException
import com.samsung.android.sdk.accessory.SA
import com.samsung.android.sdk.accessory.SAAgentV2
import com.samsung.android.sdk.accessory.SAPeerAgent
import com.samsung.android.sdk.accessory.SASocket
import com.samsung.android.sdk.accessory.example.helloaccessory.provider.R


/**
 * Samsung socket class, don't want to touch this much
 */
class ReceiverService(context: Context) : SAAgentV2(TAG, context, SASOCKET_CLASS) {
    companion object {
        private val TAG = "ReceiverService"
        private val SASOCKET_CLASS = ServiceConnection::class.java
    }

    init {
        val accessory = SA()
        try {
            accessory.initialize(applicationContext)

        } catch (e: Exception) {
            e.printStackTrace()

            if (e is SsdkUnsupportedException) {
                when (e.type) {
                    SsdkUnsupportedException.VENDOR_NOT_SUPPORTED,
                    SsdkUnsupportedException.DEVICE_NOT_SUPPORTED ->
                        Log.e(TAG, "Application cannot use Samsung Accessory SDK")
                    SsdkUnsupportedException.LIBRARY_NOT_INSTALLED ->
                        Log.e(TAG, "You need to install Samsung Accessory SDK to use this application.")
                    SsdkUnsupportedException.LIBRARY_UPDATE_IS_REQUIRED ->
                        Log.e(TAG, "You need to update Samsung Accessory SDK to use this application.")
                    SsdkUnsupportedException.LIBRARY_UPDATE_IS_RECOMMENDED ->
                        Log.e(TAG, "We recommend that you update your Samsung Accessory SDK before using this application.")

                }
            } else {
                Log.e(TAG, "Application cannot use Samsung Accessory SDK")
            }
        }
    }

    override fun onFindPeerAgentsResponse(peerAgents: Array<SAPeerAgent>?, result: Int) {
        Log.d(TAG, "onFindPeerAgentResponse : result =$result")
    }

    override fun onServiceConnectionRequested(peerAgent: SAPeerAgent?) {
        if (peerAgent != null) {
            Toast.makeText(applicationContext, R.string.connection_accept, Toast.LENGTH_SHORT).show()
            acceptServiceConnectionRequest(peerAgent)
        }
    }

    override fun onServiceConnectionResponse(peerAgent: SAPeerAgent?, socket: SASocket, result: Int) {
        if (result == SAAgentV2.CONNECTION_SUCCESS) {
            val connection = socket as ServiceConnection

            connection.listener = SensorDataSubject.instance.serviceConnectionListener

        } else if (result == SAAgentV2.CONNECTION_ALREADY_EXIST) {
            Log.e(TAG, "onServiceConnectionResponse, CONNECTION_ALREADY_EXIST")
        }
    }
}
