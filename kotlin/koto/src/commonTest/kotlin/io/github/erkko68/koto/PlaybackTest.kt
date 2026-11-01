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

    /** [block] with a sound of [sine], which decodes from memory only valid inside `interopScope`. */
    private fun play(block: InteropScope.(Engine, Sound, Decoder) -> Unit) {
        loadNativeLibrary()
        val engine = EngineConfig.init().use { config ->
            config.noDevice = true
            config.channels = 2
            config.sampleRate = RATE
            Engine.init(config)
        }
        val wav = sine()
        engine.use {
            interopScope {
                Decoder.initMemory(toInterop(wav), wav.size, null).use { decoder ->
                    Sound.initFromDataSource(it, decoder.decoderHandle, 0, null).use { sound -> block(it, sound, decoder) }
                }
            }
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
}
