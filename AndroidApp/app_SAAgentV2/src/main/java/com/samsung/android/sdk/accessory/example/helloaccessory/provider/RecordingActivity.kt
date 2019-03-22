package com.samsung.android.sdk.accessory.example.helloaccessory.provider

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

class RecordingActivity: Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording)

        Toast.makeText(applicationContext, "WIP, does nothing so far, please look at the debug log for sensor data", Toast.LENGTH_LONG)
                .show()
        // TODO: WIP
        // assign new observable on start record
        // -> get timestamp
        //
        // end observable on stop or activity closed
    }

    /** Some sample code
     *
    val sd_main = File(Environment.getExternalStorageDirectory()+"/yourlocation")
    var success = true
    if (!sd_main.exists()) {
        success = sd_main.mkdir()
    }
    if (success) {
        val sd = File("filename.txt")

        if (!sd.exists()) {
            success = sd.mkdir()
        }
    }
    if (success) {
        // directory exists or already created
        val writer = File(fileName).bufferedWriter()
        try {
            // write when msg is received
        } catch (e: Exception) {
            // handle the exception
            // close & toast
            // and kill observable
        }

    } else {
        // directory creation is not successful
        // Toast
    }

     */

}