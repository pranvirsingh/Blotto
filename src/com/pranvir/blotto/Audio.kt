package com.pranvir.blotto

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Plays the synthesised sounds. WAVs are rendered once on a background thread and cached.
 * Music streams from a dedicated thread so it never depends on large static buffers.
 */
class Audio(context: Context) {
    private val appCtx = context.applicationContext
    private val pool: SoundPool
    private val soundIds = IntArray(Sfx.COUNT) { -1 }
    private val loaded = ConcurrentHashMap<Int, Boolean>()
    private var projectorSample = -1
    private var projectorStream = 0

    @Volatile private var soundOn = true
    @Volatile private var musicOn = true
    @Volatile private var resumed = false
    @Volatile private var released = false

    @Volatile private var musicPcm: ShortArray? = null
    private var track: AudioTrack? = null
    private var musicThread: Thread? = null
    private val lock = Any()

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loaded[sampleId] = true
                if (sampleId == projectorSample) updateAmbient()
            }
        }
        Thread({ prepare() }, "blotto-audio-prep").apply { isDaemon = true; start() }
    }

    private fun prepare() {
        try {
            val dir = File(appCtx.cacheDir, "sfx_v3").apply { mkdirs() }
            for (id in 0 until Sfx.COUNT) {
                if (released) return
                val f = File(dir, "s$id.wav")
                if (!f.exists() || f.length() < 50) writeAtomic(f, Synth.wav(Synth.render(id)))
                soundIds[id] = pool.load(f.path, 1)
            }
            val pf = File(dir, "projector.wav")
            if (!pf.exists() || pf.length() < 50) writeAtomic(pf, Synth.wav(Synth.render(Sfx.PROJECTOR)))
            projectorSample = pool.load(pf.path, 1)

            val mf = File(dir, "music.pcm")
            val pcm: ShortArray
            if (mf.exists() && mf.length() > 1000) {
                val bytes = mf.readBytes()
                pcm = ShortArray(bytes.size / 2) { ((bytes[it * 2].toInt() and 255) or (bytes[it * 2 + 1].toInt() shl 8)).toShort() }
            } else {
                pcm = Synth.renderMusic()
                val bytes = ByteArray(pcm.size * 2)
                for (i in pcm.indices) { bytes[i * 2] = (pcm[i].toInt() and 255).toByte(); bytes[i * 2 + 1] = (pcm[i].toInt() shr 8).toByte() }
                writeAtomic(mf, bytes)
            }
            musicPcm = pcm
            updateMusic()
        } catch (e: Throwable) {
            android.util.Log.e("Blotto", "audio prep failed", e)
        }
    }

    private fun writeAtomic(f: File, data: ByteArray) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun play(id: Int, vol: Float, rate: Float) {
        if (!soundOn || released || id !in 0 until Sfx.COUNT) return
        val s = soundIds[id]
        if (s <= 0 || loaded[s] != true) return
        val v = vol.coerceIn(0f, 1f)
        pool.play(s, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun setEnabled(sound: Boolean, music: Boolean) {
        soundOn = sound
        musicOn = music
        updateAmbient()
        updateMusic()
    }

    fun resume() {
        resumed = true
        if (!released) pool.autoResume()
        updateAmbient()
        updateMusic()
    }

    fun pause() {
        resumed = false
        if (!released) pool.autoPause()
        updateAmbient()
        updateMusic()
    }

    @Synchronized
    private fun updateAmbient() {
        if (released) return
        val want = soundOn && resumed && projectorSample > 0 && loaded[projectorSample] == true
        if (want && projectorStream == 0) {
            projectorStream = pool.play(projectorSample, 0.1f, 0.1f, 0, -1, 1f)
        } else if (!want && projectorStream != 0) {
            pool.stop(projectorStream)
            projectorStream = 0
        }
    }

    private fun updateMusic() {
        synchronized(lock) {
            if (released) return
            val pcm = musicPcm ?: return
            val want = musicOn && resumed
            if (want) {
                if (track == null) {
                    try {
                        val minBuf = AudioTrack.getMinBufferSize(Synth.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                        val t = AudioTrack.Builder()
                            .setAudioAttributes(AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                            .setAudioFormat(AudioFormat.Builder()
                                .setSampleRate(Synth.RATE)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                            .setBufferSizeInBytes(maxOf(minBuf * 2, 8192))
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build()
                        t.setVolume(0.32f)
                        track = t
                        val th = Thread({ musicLoop(t, pcm) }, "blotto-music")
                        th.isDaemon = true
                        musicThread = th
                        t.play()
                        th.start()
                    } catch (e: Throwable) {
                        android.util.Log.e("Blotto", "music failed", e)
                        track = null
                    }
                } else {
                    try { track?.play() } catch (e: Throwable) { }
                }
            } else {
                try { track?.pause() } catch (e: Throwable) { }
            }
        }
    }

    private fun musicLoop(t: AudioTrack, pcm: ShortArray) {
        var pos = 0
        val chunk = 2048
        while (!released && track === t) {
            val n = minOf(chunk, pcm.size - pos)
            val w = try { t.write(pcm, pos, n) } catch (e: Throwable) { -1 }
            if (w < 0) break
            if (w == 0) {
                try { Thread.sleep(20) } catch (e: InterruptedException) { break }
                continue
            }
            pos += w
            if (pos >= pcm.size) pos = 0
        }
    }

    fun release() {
        released = true
        synchronized(lock) {
            val t = track
            track = null
            try { t?.pause(); t?.flush(); t?.stop() } catch (e: Throwable) { }
            try { t?.release() } catch (e: Throwable) { }
            musicThread?.interrupt()
            musicThread = null
        }
        try { pool.release() } catch (e: Throwable) { }
    }
}
