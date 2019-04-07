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
import android.view.View


class GenerateTrackActivity : Activity() {

    companion object {
        private val tempoRange = Pair(60, 120)
        private const val tempoStep = 10
        // currently an arbitrary value, ensure it is between 1000/(24 to 120Hz), standard refresh rate
        private const val seekBarUpdatePeriod = 16L
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

        // pause when seekbar is adjusted by user
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
                // TODO: tell drum machine to add beat to selected row
                Toast.makeText(this@GenerateTrackActivity,
                        "WIP, connect me to drum machine!",
                        Toast.LENGTH_SHORT).show()
            }
        }

        setTempoText()
        setButtons(false)

        // try to make seekbar range a multiple of our update frequency (big enough should be fine)
        // there is no particularly good reason for using the existing combination
        drumkit_instruments.seekBar.max =
                60 * 10 * tempo * DrumKitInstrumentsAdapter.COLUMNS * seekBarUpdatePeriod.toInt()
    }

    private fun play() {
        if (selectedInstrumentRow == null) {
            drumkit_instruments.instrumentsRecycler.getChildAt(0).performClick()
        }
        setButtons(true)
        snapAndStartSeekBar()
    }

    private fun pause() {
        // todo: stop ml if possible
        setButtons(false)
        stopSeekBarMovement()
    }

    override fun onStop() {
        disposables.clear()
        super.onStop()
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

    private fun snapAndStartSeekBar() {
        // snap seekbar to nearest beat
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
