package io.github.erkko68.koto

import io.github.erkko68.koto.interop.KotoJni
import io.github.erkko68.koto.interop.loadNativeLibrary
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Kotlin I/O under an encoder and a decoder, the engine's callbacks, a data source's next, and strings a config keeps. */
class IoCallbackTest {
    /** A file in memory that an encoder writes and a decoder reads. */
    private class Memory {
        var bytes = ByteArray(0)
        var position = 0

        fun write(from: Long, count: Int): Pair<Result, Int> {
            if (position + count > bytes.size) bytes = bytes.copyOf(position + count)
            KotoJni.buffer(from, count).get(bytes, position, count)
            position += count
            return Result.SUCCESS to count
        }

        fun read(to: Long, count: Int): Pair<Result, Int> {
            val n = minOf(count, bytes.size - position)
            if (n > 0) KotoJni.buffer(to, n).put(bytes, position, n)
            position += n
            return (if (n == 0) Result.AT_END else Result.SUCCESS) to n
        }

        fun seek(offset: Long, origin: SeekOrigin): Result {
            position = (offset + when (origin) {
                SeekOrigin.START -> 0
                SeekOrigin.CURRENT -> position
                SeekOrigin.END -> bytes.size
            }).toInt()
            return Result.SUCCESS
        }

        fun decoder() = DecoderConfig.init(Format.F32, 1, RATE).use { config ->
            position = 0
            Decoder.init({ _, out, count -> read(out, count) }, { _, offset, origin -> seek(offset, origin) }, config)
        }
    }

    private fun <T> frames(count: Int, block: (Long) -> T): T {
        val pointer = KotoJni.alloc(count * 4L)
        try {
            return block(pointer)
        } finally {
            KotoJni.free(pointer)
        }
    }

    private fun wav(samples: FloatArray) = Memory().also { file ->
        loadNativeLibrary()
        val config = EncoderConfig.init(EncodingFormat.WAV, Format.F32, 1, RATE)
        val encoder = config.use { Encoder.init({ _, from, count -> file.write(from, count) }, { _, offset, origin -> file.seek(offset, origin) }, it) }
        frames(samples.size) { p ->
            KotoJni.buffer(p, samples.size * 4).asFloatBuffer().put(samples)
            assertEquals(samples.size.toLong(), encoder.writePcmFrames(p, samples.size.toLong()))
        }
        encoder.close()
    }

    @Test
    fun aDecoderReadsWhatAnEncoderWroteThroughKotlin() {
        val samples = FloatArray(100) { it / 100f }
        val file = wav(samples)
        assertTrue(file.bytes.size > samples.size * 4, "no header: ${file.bytes.size} bytes")
        file.decoder().use { decoder ->
            val read = frames(200) { p ->
                val n = decoder.readPcmFrames(p, 200)
                FloatArray(n.toInt()).also { KotoJni.buffer(p, it.size * 4).asFloatBuffer().get(it) }
            }
            assertEquals(samples.toList(), read.toList())
        }
    }

    @Test
    fun aDataSourceGoesOnToTheNextOneKotlinGives() {
        val file = wav(FloatArray(100) { 0.5f })
        val first = file.decoder()
        val second = Memory().apply { bytes = file.bytes }.decoder()
        dataSourceSetNextCallback(first.decoderHandle) { second.decoderHandle }
        val read = frames(300) { dataSourceReadPcmFrames(first.decoderHandle, it, 300) }
        assertEquals(200, read)
        // Closed with its next still set, which the close lets go of.
        second.close()
        first.close()
    }

    @Test
    fun anEngineCallsItsProcessDataAndNotificationCallbacks() {
        loadNativeLibrary()
        val processed = AtomicLong()
        val rendered = CountDownLatch(1)
        val started = CountDownLatch(1)
        Context.init(listOf(Backend.NULL), 1, null).use { context ->
            lateinit var engine: Engine
            engine = EngineConfig.init().use { config ->
                config.context = context
                // An empty graph reads no frames, but the engine processes what it read all the same.
                config.setOnProcess { _, _ -> processed.incrementAndGet() }
                config.setDataCallback { _, output, _, frameCount ->
                    engine.readPcmFrames(output, frameCount.toLong())
                    rendered.countDown()
                }
                config.setNotificationCallback { if (it!!.type == DeviceNotificationType.STARTED) started.countDown() }
                Engine.init(config)
            }
            engine.use {
                assertTrue(started.await(5, TimeUnit.SECONDS), "no notification")
                assertTrue(rendered.await(5, TimeUnit.SECONDS), "no data callback")
            }
        }
        assertTrue(processed.get() > 0)
    }

    @Test
    fun aConfigKeepsTheStringsSetOnIt() {
        loadNativeLibrary()
        SoundConfig.init().use { config ->
            config.filePath = buildString { append("shot.wav") }
            System.gc()
            assertEquals("shot.wav", config.filePath)
            config.filePath = null
            assertEquals(null, config.filePath)
        }
    }

    private companion object {
        const val RATE = 48000
    }
}
