package io.github.erkko68.koto

import io.github.erkko68.koto.interop.InteropScope
import io.github.erkko68.koto.interop.interopScope
import io.github.erkko68.koto.interop.loadNativeLibrary
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val RATE = 48000

/** Real audio through the whole library, read from the engine's mix: no device or files, so it runs on every target. */
class PlaybackTest {
    /** A second of 440 Hz, mono, as the bytes of a WAV file, for miniaudio's decoder. */
    private fun sine(): ByteArray {
        val bytes = ArrayList<Byte>(44 + RATE * 2)
        fun text(value: String) = value.forEach { bytes += it.code.toByte() }
        fun number(value: Int, size: Int) = repeat(size) { bytes += (value shr (it * 8)).toByte() }
        text("RIFF"); number(36 + RATE * 2, 4); text("WAVEfmt "); number(16, 4)
        number(1, 2); number(1, 2); number(RATE, 4); number(RATE * 2, 4); number(2, 2); number(16, 2)
        text("data"); number(RATE * 2, 4)
        repeat(RATE) { number((sin(2 * PI * 440 * it / RATE) * 16000).toInt(), 2) }
        return bytes.toByteArray()
    }

    /** An engine mixing for no device, which [mix] reads. */
    private fun engine(): Engine {
        loadNativeLibrary()
        return EngineConfig.init().use { config ->
            config.noDevice = true
            config.channels = 2
            config.sampleRate = RATE
            Engine.init(config)
        }
    }

    /** [block] with a sound of [sine], decoded from the decoder's own copy of the bytes. */
    private fun play(block: InteropScope.(Engine, Sound, Decoder) -> Unit) = engine().use {
        Decoder.initMemory(sine(), null).use { decoder ->
            Sound.initFromDataSource(it, decoder.decoderHandle, 0, null).use { sound -> interopScope { block(it, sound, decoder) } }
        }
    }

    /** The next [frames] of the mix, a loudness per channel. */
    private fun InteropScope.mix(engine: Engine, frames: Int): Pair<Double, Double> {
        val out = FloatArray(frames * 2)
        val pointer = toInterop(out)
        engine.readPcmFrames(pointer, frames.toLong())
        pointer.fromInterop(out)
        fun rms(channel: Int) = sqrt((0 until frames).sumOf { (out[it * 2 + channel] * out[it * 2 + channel]).toDouble() } / frames)
        return rms(0) to rms(1)
    }

    @Test
    fun decodesTheBytes() = play { _, sound, _ ->
        assertEquals(1f, sound.lengthInSeconds, 0.01f)
    }

    @Test
    fun silenceUntilItStarts() = play { engine, _, _ ->
        assertEquals(0.0 to 0.0, mix(engine, 1024))
    }

    @Test
    fun aStartedSoundIsMixed() = play { engine, sound, _ ->
        sound.setSpatializationEnabled(false)
        sound.start()
        val (left, right) = mix(engine, 4096)
        assertTrue(left > 0.1 && right > 0.1, "left $left, right $right")
    }

    @Test
    fun volumeScalesIt() = play { engine, sound, _ ->
        sound.setSpatializationEnabled(false)
        sound.start()
        val full = mix(engine, 4096).first
        sound.volume = 0.5f
        mix(engine, 4096) // The change is smoothed in.
        assertTrue(abs(mix(engine, 4096).first - full / 2) < full * 0.1)
    }

    @Test
    fun panMovesItToOneSide() = play { engine, sound, _ ->
        sound.setSpatializationEnabled(false)
        sound.pan = -1f
        sound.start()
        mix(engine, 4096)
        val (left, right) = mix(engine, 4096)
        assertTrue(left > 0.1 && right < 0.001, "left $left, right $right")
    }

    @Test
    fun positionPlacesItAroundTheListener() = play { engine, sound, _ ->
        sound.setPosition(-3f, 0f, 0f) // The listener is at the origin, looking down -z.
        sound.start()
        mix(engine, 4096)
        val (left, right) = mix(engine, 4096)
        assertTrue(left > right * 2, "left $left, right $right")
    }

    @Test
    fun itEndsWithTheData() = play { engine, sound, _ ->
        sound.start()
        assertFalse(sound.atEnd())
        repeat(30) { mix(engine, 2048) } // A little over the second it lasts.
        assertTrue(sound.atEnd())
        assertFalse(sound.isPlaying)
    }

    @Test
    fun theEndCallbackRunsOnceAtTheEnd() = play { engine, sound, _ ->
        var ended = 0
        sound.setEndCallback { ended++ }
        sound.start()
        repeat(20) { mix(engine, 2048) }
        assertEquals(0, ended)
        repeat(10) { mix(engine, 2048) }
        assertEquals(1, ended)
    }

    @Test
    fun aReplacedEndCallbackIsntCalled() = play { engine, sound, _ ->
        var first = 0
        var second = 0
        sound.setEndCallback { first++ }
        sound.setEndCallback { second++ }
        sound.start()
        repeat(30) { mix(engine, 2048) }
        assertEquals(0 to 1, first to second)
    }

    @Test
    fun aSoundPlaysTheBytesRegisteredUnderItsName() = engine().use { engine ->
        engine.resourceManager!!.registerEncodedData("sine.wav", sine())
        Sound.initFromFile(engine, "sine.wav", 0, null, null).use { sound ->
            sound.start()
            val (left, right) = interopScope { mix(engine, 4800) }
            assertTrue(left > 0.1 && right > 0.1, "silent: $left, $right")
        }
    }

    /** A tenth of a second of 440 Hz, stereo f32 frames. */
    private fun sineFrames() = FloatArray(RATE / 10 * 2) { (sin(2 * PI * 440 * (it / 2) / RATE) * 0.5).toFloat() }

    @Test
    fun aSoundPlaysTheFramesRegisteredUnderItsName() = engine().use { engine ->
        engine.resourceManager!!.registerDecodedData("sine", sineFrames(), 2, RATE)
        Sound.initFromFile(engine, "sine", 0, null, null).use { sound ->
            sound.start()
            val (left, right) = interopScope { mix(engine, 2400) }
            assertTrue(left > 0.1 && right > 0.1, "silent: $left, $right")
        }
    }

    @Test
    fun anAudioBufferPlaysItsCopyOfTheFrames() = engine().use { engine ->
        AudioBuffer.initCopy(sineFrames(), 2).use { buffer ->
            Sound.initFromDataSource(engine, buffer.audioBufferHandle, 0, null).use { sound ->
                sound.start()
                val (left, right) = interopScope { mix(engine, 2400) }
                assertTrue(left > 0.1 && right > 0.1, "silent: $left, $right")
            }
        }
    }
}
