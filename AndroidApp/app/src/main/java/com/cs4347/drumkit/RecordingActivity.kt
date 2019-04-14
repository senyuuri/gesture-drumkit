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
import android.content.res.AssetManager
import android.text.Editable
import android.text.TextWatcher


/**
 * Writes recorded data to sdcard
 */
class RecordingActivity: Activity() {
    private external fun native_onInit(assetManager: AssetManager)
    private external fun native_onStart(tempo: Int,  beatIdx: Int)
    private external fun native_onStartMetronome(tempo: Int)
    private external fun native_onStop()
    private external fun native_onStopMetronome()
    private external fun native_insertBeat(channel_idx: Int)
    private external fun native_setTempo(tempo: Int)
    private var tempo: Int

    init
    {
        System.loadLibrary("native-lib");
        tempo = 60
    }

    companion object {
        private const val rootDir = "drumkit_record"
        // existing sensors all give 3 data values
        private const val csvHeaders = "sensor_type,timestamp_ms,data1,data2,data3"
    }

    private val dataLoggerDisposable = CompositeDisposable()

    private fun toggleRecordingButtons(startRecording: Boolean) {
        stop_button.isEnabled = startRecording
        start_button.isEnabled = !startRecording
        audio_start_button.isEnabled = !startRecording
        audio_stop_button.isEnabled = !startRecording
        audio_insert_beat_button.isEnabled = !startRecording
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recording)

        start_button.setOnClickListener {

            val fileName = "${timeNow()}.csv"
            val fileLoc = "${Environment.getExternalStorageDirectory().absolutePath}/$rootDir/$fileName"
            val fileWriter = getFileWriter(fileLoc)
            fileWriter.write(csvHeaders)
            fileWriter.newLine()

            val closeFile = { fileWriter.close() }

            toggleRecordingButtons(true)

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
            native_onStartMetronome(tempo)
        }

        stop_button.setOnClickListener {
            toggleRecordingButtons(false)
            native_onStop();
            dataLoggerDisposable.clear()
            native_onStopMetronome()
        }

        audio_start_button.setOnClickListener{
            native_onStart(tempo, 0);
        }

        audio_stop_button.setOnClickListener{
            native_onStop()
        }

        audio_insert_beat_button.setOnClickListener {
            native_insertBeat(0);
        }

        tempo_edittext.setText(60.toString())
        set_tempo_button.setOnClickListener {
           tempo = tempo_edittext.text.toString().toInt()
        }

        // initialise drum machine
        native_onInit(assets);
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