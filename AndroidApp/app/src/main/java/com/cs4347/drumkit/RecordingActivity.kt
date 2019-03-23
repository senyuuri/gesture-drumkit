package com.cs4347.drumkit

import android.app.Activity
import android.os.Bundle
import android.os.Environment
import com.cs4347.drumkit.transmission.SensorDataSubject
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_recording.*
import java.io.BufferedWriter
import java.io.File
import java.sql.Date
import java.sql.Timestamp

/**
 * Writes recorded data to sdcard
 */
class RecordingActivity: Activity() {

    companion object {
        private val rootDir = "drumkit_record"
        // existing sensors all give 3 data values
        private val csvHeaders = "sensor_type,timestamp_ms,data1,data2,data3"
    }

    private val dataLoggerDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording)

        start_button.setOnClickListener {
            stop_button.isEnabled = true
            start_button.isEnabled = false

            val fileName = "${timeNow()}.csv"
            val fileLoc = "${Environment.getExternalStorageDirectory().absolutePath}/$rootDir/$fileName"
            val fileWriter = getFileWriter(fileLoc)
            fileWriter.write(csvHeaders)
            fileWriter.newLine()

            val closeFile = { fileWriter.close() }

            val sub = SensorDataSubject.instance.observe()
                    .subscribeOn(Schedulers.io())
                    .doOnComplete(closeFile)
                    .doOnDispose(closeFile)
                    .subscribe {
                        val line = listOf<String>(
                                it.sensorType.toString(),
                                it.timestamp.toString(),
                                it.dataList.joinToString(",")
                        )
                        fileWriter.write(line.joinToString(","))
                        fileWriter.newLine()
                    }
            dataLoggerDisposable.add(sub)
        }

        stop_button.setOnClickListener {
            stop_button.isEnabled = false
            start_button.isEnabled = true
            dataLoggerDisposable.clear()
        }
    }

    override fun onStop() {
        dataLoggerDisposable.clear()
        super.onStop()
    }

    private fun timeNow(): String {
        val stamp = Timestamp(System.currentTimeMillis())
        return "${Date(stamp.time)}_${System.currentTimeMillis()}"
    }


    private fun getFileWriter(location: String): BufferedWriter {
        val file = File(location)
        file.parentFile.mkdir()
        if (!file.exists()) {
        } else {
            throw FileAlreadyExistsException(file, null, "Tried to make file at $location, but file already exists")
        }
        return file.bufferedWriter()
    }


}