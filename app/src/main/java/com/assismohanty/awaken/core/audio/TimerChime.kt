package com.assismohanty.awaken.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import com.assismohanty.awaken.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** The sound a timer makes when it ends (phone feedback Part 4). [OFF] is silent. */
enum class ChimeChoice(@RawRes val resource: Int?) {
    OFF(null),
    SOFT_BELL(R.raw.chime_soft_bell),
    WOOD_BLOCK(R.raw.chime_wood_block),
    DIGITAL(R.raw.chime_digital),
}

/** Plays a timer chime. A fake in tests; the real one is [SoundPoolChimePlayer]. */
interface ChimePlayer {
    fun play(choice: ChimeChoice)
}

/**
 * Plays the chimes with one [SoundPool]. The clips are loaded on first use and kept; the pool uses the notification
 * usage, so it follows the phone's volume and silent mode. Playback failing is never fatal: the timer already ended.
 */
@Singleton
class SoundPoolChimePlayer @Inject constructor(@ApplicationContext private val context: Context) : ChimePlayer {
    private val loaded = mutableMapOf<ChimeChoice, Int>()
    private val ready = mutableSetOf<Int>()
    private var pool: SoundPool? = null

    @Synchronized
    override fun play(choice: ChimeChoice) {
        val resource = choice.resource ?: return
        try {
            val pool = pool ?: SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
                .also { created ->
                    created.setOnLoadCompleteListener { soundPool, sampleId, status ->
                        if (status == 0) {
                            synchronized(this) { ready += sampleId }
                            soundPool.play(sampleId, VOLUME, VOLUME, PRIORITY, NO_LOOP, NORMAL_RATE)
                        }
                    }
                    pool = created
                }
            val sampleId = loaded[choice]
            if (sampleId == null) {
                loaded[choice] = pool.load(context, resource, PRIORITY) // plays when loading completes
            } else if (sampleId in ready) {
                pool.play(sampleId, VOLUME, VOLUME, PRIORITY, NO_LOOP, NORMAL_RATE)
            }
        } catch (e: RuntimeException) {
            Timber.w(e, "Timer chime failed")
        }
    }

    private companion object {
        const val MAX_STREAMS = 2
        const val VOLUME = 1f
        const val PRIORITY = 1
        const val NO_LOOP = 0
        const val NORMAL_RATE = 1f
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds
    abstract fun bindChimePlayer(player: SoundPoolChimePlayer): ChimePlayer
}
