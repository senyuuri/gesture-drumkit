package com.cs4347.drumkit

import android.app.Activity
import android.os.Bundle
import android.support.v4.view.ViewCompat
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
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import android.view.animation.DecelerateInterpolator
import android.animation.ObjectAnimator
import android.animation.Animator
import android.R.attr.animation
import android.content.res.AssetManager
import android.view.View


class GenerateTrackActivity : Activity() {
    private external fun native_onInit(assetManager: AssetManager)
    private external fun native_onStart(tempo: Int, beatIdx: Int)
    private external fun native_onStop()
    private external fun native_insertBeat(channel_idx: Int): Int
    private external fun native_setTempo(tempo: Int)

    init
    {
        System.loadLibrary("native-lib")
    }


    companion object {
        private val tempoRange = Pair(60, 120)
        private const val tempoStep = 10
        // currently an arbitrary value, ensure it is between 1000/(24 to 120Hz), standard refresh rate
        private const val seekBarUpdatePeriod = 16L
        // 30ms
        private const val seekBarSnapDuration = 200L
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

    private lateinit var instrumentsAdapter: DrumKitInstrumentsAdapter
    private val disposables: CompositeDisposable = CompositeDisposable()

    private var seekBarMovementDisposable: Disposable? = null


    private var tempo = tempoRange.first
    private var selectedInstrumentRow: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_track)

        instrumentsAdapter = DrumKitInstrumentsAdapter(instruments, object: RowSelectionListener {
            override fun onRowSelected(row: Int) {
                selectedInstrumentRow = row
                Toast.makeText(this@GenerateTrackActivity,
                        "Row $selectedInstrumentRow selected",
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
        }

        tempoDown.setOnClickListener {
            tempo = max(tempoRange.first, tempo - tempoStep)
            setTempoText()
        }

        play.setOnClickListener {
            play()
        }

        record.setOnClickListener {
            // TODO start ML recognizer here
            // TODO: tell drum machine to add beat to selected row inside
            play()
        }

        pause.setOnClickListener {
            pause()
        }

        drumkit_instruments.setSeekBarOnChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {}

            override fun onStartTrackingTouch(p0: SeekBar?) {
                pause()
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        clear.setOnClickListener {
            clearSelectedInstrumentBeats()
        }

        // TODO: delete after debugging
        debug_add_beat.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                var channelIdx = selectedInstrumentRow;
                if (channelIdx == null) {
                    Toast.makeText(this@GenerateTrackActivity,
                            "Select a track first!",
                            Toast.LENGTH_SHORT).show()
                } else {
                    var beatIdx = native_insertBeat(channelIdx)
                    setSelectedInstrumentBeat(beatIdx, true)
                    Toast.makeText(this@GenerateTrackActivity,
                            "Beat added!",
                            Toast.LENGTH_SHORT).show()

                }


            }
        }

        setTempoText()
        setButtons(false)
        drumkit_instruments.seekBar.max =
                60 * 10 * tempo * DrumKitInstrumentsAdapter.COLUMNS * seekBarUpdatePeriod.toInt()

        // initialise DrumMachine
        native_onInit(assets)
    }

    private fun play() {
        if (selectedInstrumentRow == null) {
            drumkit_instruments.instrumentsRecycler.getChildAt(0).performClick()
        }
        setButtons(true)
        snapAndStartSeekBar()
        native_onStart(tempo, calcDestinationBeat())
    }

    private fun pause() {
        // todo: stop ml if possible
        setButtons(false)
        stopSeekBarMovement()
        // todo: add native_pause
        native_onStop()
    }

    override fun onStop() {
        disposables.clear()
        super.onStop()
        native_onStop()
    }

    private fun setSelectedInstrumentBeat(col: Int, activate: Boolean) {
        selectedInstrumentRow?.let {
            val beatRowRecycler: RecyclerView = drumkit_instruments.instrumentsRecycler.getChildAt(it).instrument_beats_rv
            val beatRowAdapter = beatRowRecycler.adapter as BeatsAdapter
            beatRowAdapter.setColumn(col, activate)
        }
    }

    private fun clearSelectedInstrumentBeats() {
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

    private fun stopSeekBarMovement() {
        seekBarMovementDisposable?.dispose()
    }

    private fun calcDestinationBeat(): Int {
        val timePerBeatMs = 60*1000/tempo.toFloat()
        val totalDuration: Float = timePerBeatMs * DrumKitInstrumentsAdapter.COLUMNS
        val timePerPercentage: Float = totalDuration / drumkit_instruments.seekBar.max
        val percentagePerTime: Float = 1/timePerPercentage

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

    private fun snapAndStartSeekBar() {
        // snap seekbar to nearest beat
        val timePerBeatMs = 60*1000/tempo.toFloat()
        val totalDuration: Float = timePerBeatMs * DrumKitInstrumentsAdapter.COLUMNS
        val timePerPercentage: Float = totalDuration / drumkit_instruments.seekBar.max
        val percentagePerTime: Float = 1/timePerPercentage
        val destinationBeat = calcDestinationBeat()
        val destProgress = (destinationBeat * timePerBeatMs * percentagePerTime).roundToInt()

        // start seekbar movement after animation
        disposables.add(drumkit_instruments.seekBar.shiftTo(destProgress, seekBarSnapDuration)
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    // TODO: start drum machine when code is available
                    startSeekBarMovement()
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
