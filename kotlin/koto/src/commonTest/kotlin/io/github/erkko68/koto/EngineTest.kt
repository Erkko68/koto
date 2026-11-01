package io.github.erkko68.koto

import io.github.erkko68.koto.interop.NativeException
import io.github.erkko68.koto.interop.loadNativeLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EngineTest {
    private fun engine(): Engine {
        loadNativeLibrary()
        return EngineConfig.init().use { config ->
            config.noDevice = true
            config.channels = 2
            config.sampleRate = 48000
            Engine.init(config)
        }
    }

    @Test
    fun version() {
        loadNativeLibrary()
        assertEquals("0.11.26", versionString())
    }

    /** No audio device needed: the engine mixes when asked. */
    @Test
    fun engineWithoutADevice() {
        engine().use { engine ->
            assertEquals(2, engine.channels)
            assertEquals(48000, engine.sampleRate)
            engine.volume = 0.5f
            assertEquals(0.5f, engine.volume)
        }
    }

    @Test
    fun aFailedInitThrows() {
        engine().use { engine ->
            assertFailsWith<NativeException> { Sound.initFromFile(engine, "does-not-exist.wav", 0, null, null) }
        }
    }

    @Test
    fun aSoundGroupIsASound() {
        engine().use { engine ->
            Sound.groupInit(engine, 0, null).use { group ->
                group.setPosition(1f, 2f, 3f)
                assertEquals(3f, group.position.z)
                assertTrue(group.isPlaying) // A group starts as it is made.
            }
        }
    }
}
