package com.cs4347.drumkit

import android.app.Activity
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.widget.SeekBar
import android.widget.Toast
import com.cs4347.drumkit.view.BeatsAdapter
import com.cs4347.drumkit.view.DrumKitInstrumentsAdapter
import com.cs4347.drumkit.view.RowSelectionListener
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.CompletableSubject
import kotlinx.android.synthetic.main.activity_generate_track.*
import kotlinx.android.synthetic.main.view_instrument_row.view.*
import java.util.concurrent.TimeUnit
import android.view.animation.DecelerateInterpolator
import android.animation.ObjectAnimator
import android.animation.Animator
import android.content.res.AssetManager
import android.view.View
import android.os.Build
import android.util.Log
import com.cs4347.drumkit.gestures.GestureRecognizer
import com.cs4347.drumkit.gestures.GestureType
import io.reactivex.Single
import kotlin.math.*


class GenerateTrackActivity : Activity() {
    private external fun native_onInit(assetManager: AssetManager)
    private external fun native_onStart(tempo: Int, beatIdx: Int)
    private external fun native_onStop()
    private external fun native_insertBeat(channel_idx: Int): Int
    private external fun native_setTempo(tempo: Int)
    private external fun native_resetTrack(track_idx: Int)

    init
    {
        System.loadLibrary("native-lib")
    }

    companion object {
        private val tempoRange = Pair(60, 120)
        private const val tempoStep = 10
        // currently an arbitrary value, ensure it is between 1000/(24 to 120Hz), standard refresh rate
        private const val seekBarUpdatePeriod = 16L
        private const val seekBarSnapDuration = 200L
        const val DEBUG_MODE_EXTRA = "debug_mode_extra"
    }

    private val instruments = listOf(
            "Kick" to R.color.colorKick,
            "Cymbal" to R.color.colorFingerCymbal,
            "Clap" to R.color.colorClap,
            "Splash" to R.color.colorSplash,
            "HiHat" to R.color.colorHiHat,
            "Scratch" to R.color.colorScratch,
            "Rim" to R.color.colorRim
    )
    private val disposables: CompositeDisposable = CompositeDisposable()
    private val gestureRecognizer: GestureRecognizer by lazy { GestureRecognizer(this) }

    private lateinit var instrumentsAdapter: DrumKitInstrumentsAdapter
    private var tempo = tempoRange.first
    private var selectedInstrumentRow: Int? = null
    private var experimentalMode: Boolean = false

    private var seekBarMovementDisposable: Disposable? = null
    private var sensorDataDisposable: Disposable? = null

    private fun hideNavBar() {
        val currentApiVersion = android.os.Build.VERSION.SDK_INT

        val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)

        // This work only for android 4.4+
        if (currentApiVersion >= Build.VERSION_CODES.KITKAT) {

            window.decorView.systemUiVisibility = flags

            // Code below is to handle presses of Volume up or Volume down.
            // Without this, after pressing volume buttons, the navigation bar will
            // show up and won't hide
            val decorView = window.decorView
            decorView
                    .setOnSystemUiVisibilityChangeListener { visibility ->
                        if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                            decorView.systemUiVisibility = flags
                        }
                    }
        }

    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val currentApiVersion = android.os.Build.VERSION.SDK_INT
        if (currentApiVersion >= Build.VERSION_CODES.KITKAT && hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideNavBar()
        setContentView(R.layout.activity_generate_track)

        val debugMode: Boolean = intent.getBooleanExtra(DEBUG_MODE_EXTRA, false)

        instrumentsAdapter = DrumKitInstrumentsAdapter(instruments, object: RowSelectionListener {
            override fun onRowSelected(row: Int) {
                selectedInstrumentRow = row
                Toast.makeText(this@GenerateTrackActivity,
                        "${instruments[row].first} selected",
                        Toast.LENGTH_SHORT).show()
            }
        })

        drumkit_instruments.instrumentsRecycler.apply {
            this.adapter = instrumentsAdapter
            this.layoutManager = LinearLayoutManager(this@GenerateTrackActivity)
        }

        tempoUp.setOnClickListener {
            tempo = min(tempoRange.second, tempo + tempoStep)
            setTempoText()
            gestureRecognizer.updateRecognitionCoolDown(tempo)
        }

        tempoDown.setOnClickListener {
            tempo = max(tempoRange.first, tempo - tempoStep)
            setTempoText()
            gestureRecognizer.updateRecognitionCoolDown(tempo)
        }

        play.setOnClickListener {
            debug_add_beat.isEnabled = false
            play()
        }

        record.setOnClickListener {
            play()

            gestureRecognizer.subscribeToGestures(tempo) { gesture ->
                // a single gesture by the user is be detected as
                // multiple gestures happening around the same time

                // there is no guarantee whether first gesture detected in the window is
                // from the start, middle, or end of the window
                // (due to ml recognition)

                // only care about down gesture
                if (gesture.type == GestureType.DOWN) {
                    Log.i("Gesture Debug", "Down gesture detected at: ${gesture.time}")
                    Single.just(Unit)
                            .subscribeOn(AndroidSchedulers.mainThread())
                            .subscribe { _, _ ->
                                // casting is safe here, a track is always selected after play()
                                val beatIdx = native_insertBeat(selectedInstrumentRow!!)
                                setSelectedInstrumentBeat(beatIdx, true)
                            }
                }
            }
        }

        pause.setOnClickListener {
            pause()
            gestureRecognizer.stopSubscriptionToGestures()
        }

        // pause when seekbar is adjusted by user
        drumkit_instruments.setSeekBarOnChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {}

            override fun onStartTrackingTouch(p0: SeekBar?) {
                pause()
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        clear.setOnClickListener {
            uiClearSelectedInstrumentBeats()
            native_resetTrack(selectedInstrumentRow!!)
        }

        toggle_experimental_mode.setOnClickListener {
            experimentalMode = !experimentalMode
            gestureRecognizer.setExperimentalMode(experimentalMode)

            val onOffText = when (experimentalMode) {
                true -> "ON"
                false -> "OFF"
            }

            Toast.makeText(this@GenerateTrackActivity,
                    "Toggled experimental mode, $onOffText",
                    Toast.LENGTH_LONG).show()
            toggle_experimental_mode.text = "Experimental($onOffText)"
        }

        if (debugMode) {
            debugModeOnCreate()
        }

        setTempoText()
        setButtons(false)

        // try to make seekbar range a multiple of our update frequency (big enough should be fine)
        // there is no particularly good reason for using the existing combination
        drumkit_instruments.seekBar.max =
                60 * 10 * tempo * DrumKitInstrumentsAdapter.COLUMNS * seekBarUpdatePeriod.toInt()

        // initialise DrumMachine
        native_onInit(assets)
    }

    private fun debugModeOnCreate() {
        debug_add_beat.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                val channelIdx = selectedInstrumentRow
                if (channelIdx == null) {
                    Toast.makeText(this@GenerateTrackActivity,
                            "Select a track first!",
                            Toast.LENGTH_SHORT).show()
                } else {
                    val beatIdx = native_insertBeat(channelIdx)
                    setSelectedInstrumentBeat(beatIdx, true)
                }


            }
        }

        debug_mock_gesture.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                gestureRecognizer.returnFakeGestureAfter2SecsOfData =
                        !gestureRecognizer.returnFakeGestureAfter2SecsOfData
                val onOffText = when (gestureRecognizer.returnFakeGestureAfter2SecsOfData) {
                    true -> "ON"
                    false -> "OFF"
                }
                Toast.makeText(this@GenerateTrackActivity,
                        "Mocks a gesture after 2s of data is received & processed, $onOffText",
                        Toast.LENGTH_LONG).show()
                text = "Mock Gesture($onOffText)"
            }
        }
    }

    private fun play() {
        if (selectedInstrumentRow == null) {
            drumkit_instruments.instrumentsRecycler.getChildAt(0).performClick()
        }
        setButtons(true)
        snapSeekBar { destinationBeat ->
            native_onStart(tempo, destinationBeat)
            startSeekBarMovement()
        }
    }

    private fun pause() {
        debug_add_beat.isEnabled = true
        setButtons(false)
        seekBarMovementDisposable?.dispose()
        sensorDataDisposable?.dispose()
        native_onStop()
    }

    override fun onStop() {
        disposables.clear()
        native_onStop()
        super.onStop()
    }

    private fun setSelectedInstrumentBeat(col: Int, activate: Boolean) {
        selectedInstrumentRow?.let {
            val beatRowRecycler: RecyclerView = drumkit_instruments.instrumentsRecycler.getChildAt(it).instrument_beats_rv
            val beatRowAdapter = beatRowRecycler.adapter as BeatsAdapter
            beatRowAdapter.setColumn(col, activate)
        }
    }

    private fun uiClearSelectedInstrumentBeats() {
        selectedInstrumentRow?.let {
            val beatRowRecycler: RecyclerView = drumkit_instruments.instrumentsRecycler.getChildAt(it).instrument_beats_rv
            val beatRowAdapter = beatRowRecycler.adapter as BeatsAdapter
            beatRowAdapter.clearAll()
        }
    }

    private fun setButtons(playingBack: Boolean) {
        play.isEnabled = !playingBack
        record.isEnabled = !playingBack
        tempoUp.isEnabled = !playingBack
        tempoDown.isEnabled = !playingBack
        clear.isEnabled = !playingBack

        pause.isEnabled = playingBack
    }

    private fun calcDestinationBeat(): Int {
        val timePerBeatMs = 60*1000/tempo.toFloat()
        val totalDuration: Float = timePerBeatMs * DrumKitInstrumentsAdapter.COLUMNS
        val timePerPercentage: Float = totalDuration / drumkit_instruments.seekBar.max

        val currentTime = drumkit_instruments.seekBar.progress * timePerPercentage
        val timeFromLeftBeat = currentTime % timePerBeatMs
        val timeToRightBeat = timePerBeatMs - timeFromLeftBeat

        val snapToLeftBeat = timeFromLeftBeat < timeToRightBeat

        val leftBeatIdx = floor(currentTime / timePerBeatMs)

        val destinationBeat = when (snapToLeftBeat) {
            true -> leftBeatIdx
            false -> (leftBeatIdx + 1) % DrumKitInstrumentsAdapter.COLUMNS
        }
        return destinationBeat.toInt()
    }

    /**
     * Snaps seekbar to nearest beat
     */
    private fun snapSeekBar(doAfterSnap: (destinationBeat: Int)->Unit) {
        val timePerBeatMs = 60*1000/tempo.toFloat()
        val totalDuration: Float = timePerBeatMs * DrumKitInstrumentsAdapter.COLUMNS
        val timePerPercentage: Float = totalDuration / drumkit_instruments.seekBar.max
        val percentagePerTime: Float = 1/timePerPercentage
        val destinationBeat = calcDestinationBeat()
        val destProgress = (destinationBeat * timePerBeatMs * percentagePerTime).roundToInt()

        disposables.add(drumkit_instruments.seekBar.shiftTo(destProgress, seekBarSnapDuration)
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    doAfterSnap(destinationBeat)
                })
    }

    private fun startSeekBarMovement() {
        val timePerBeatMs = 60*1000/tempo.toFloat()
        val totalDuration: Float = timePerBeatMs * DrumKitInstrumentsAdapter.COLUMNS
        val timePerPercentage: Float = totalDuration / drumkit_instruments.seekBar.max
        val percentagePerTime: Float = 1/timePerPercentage

        var prevPosition = drumkit_instruments.seekBar.progress

        var prevTime = System.currentTimeMillis()

        seekBarMovementDisposable =
                Observable.interval(seekBarUpdatePeriod, TimeUnit.MILLISECONDS)
                        .subscribeOn(Schedulers.computation())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            val newTime = System.currentTimeMillis()
                            val timePassedMs = newTime - prevTime
                            val newPosition =
                                    (prevPosition + percentagePerTime*timePassedMs).roundToInt() %
                                            (drumkit_instruments.seekBar.max + 1)
                            drumkit_instruments.seekBar.progress = newPosition

                            prevPosition = newPosition
                            prevTime = newTime
                        }

        seekBarMovementDisposable?.let {
            disposables.add(it)
        }
    }

    private fun setTempoText() {
        tempoText.text = resources.getString(R.string.tempo_display, tempo)
    }

}

/**
 * Extension method to shift seekbar's progress with an animation
 * Returns a completable that sends an event when animation is complete
 */
fun SeekBar.shiftTo(destProgress: Int, duration: Long): Completable {
    val animationSubject = CompletableSubject.create()
    return animationSubject.doOnSubscribe {
        val animation = ObjectAnimator.ofInt(this, "progress", this.progress, destProgress)
        animation.duration = duration
        animation.interpolator = DecelerateInterpolator()
        animation.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                animationSubject.onComplete()
                clearAnimation()
            }
            override fun onAnimationCancel(animation: Animator) {
                animationSubject.onComplete()
                clearAnimation()
            }
        })
        animation.start()
    }
}
