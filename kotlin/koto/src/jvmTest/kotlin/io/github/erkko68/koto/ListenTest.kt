package io.github.erkko68.koto

import io.github.erkko68.koto.interop.loadNativeLibrary
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test

private const val STEP_MS = 30L
private const val CLIP = "../../miniaudio/data/16-44100-stereo.flac"

/**
 * For ears: plays a file through the default output device, a scene at a time, saying what to listen for.
 * `KOTO_LISTEN=1 ./gradlew :kotlin:koto:jvmTest --tests '*ListenTest*' --rerun` plays miniaudio's own test clip;
 * KOTO_LISTEN=<file> plays that file instead, and KOTO_VOLUME sets its loudness (0.3 unless set). Skipped without KOTO_LISTEN.
 */
class ListenTest {
    private fun scene(what: String, seconds: Double, step: (Double) -> Unit = {}) {
        println("\n>> $what")
        val steps = (seconds * 1000 / STEP_MS).toInt()
        repeat(steps) {
            step(it.toDouble() / steps)
            Thread.sleep(STEP_MS)
        }
    }

    @Test
    fun listen() {
        val listen = System.getenv("KOTO_LISTEN")
        assumeTrue("Set KOTO_LISTEN to listen", listen != null)
        val file = listen.takeIf { File(it).isFile } ?: CLIP
        loadNativeLibrary()
        println("miniaudio ${versionString()}, playing $file")
        val engine = EngineConfig.init().use { Engine.init(it) }
        engine.use {
            it.volume = System.getenv("KOTO_VOLUME")?.toFloat() ?: 0.3f
            Sound.initFromFile(it, file, 0, null, null).use { sound ->
                sound.setLooping(true)
                sound.setSpatializationEnabled(false)
                sound.setFadeInMilliseconds(0f, 1f, 1000)
                sound.start()
                println("${sound.lengthInSeconds} s at ${it.sampleRate} Hz, ${it.channels} channels")
                scene("Plain: both ears, unchanged, fading in over a second", 7.0)
                scene("Pan: sliding left, to right, and back", 8.0) { t -> sound.pan = -cos(t * 2 * PI).toFloat() }
                sound.pan = 0f
                scene("Volume: dips to silence and back", 5.0) { t -> sound.volume = ((1 + cos(t * 2 * PI)) / 2).toFloat() }
                sound.volume = 1f
                scene("Pitch: slowing down and deepening, then up past normal", 8.0) { t -> sound.pitch = (1 + 0.3 * sin(t * 2 * PI + PI)).toFloat() }
                sound.pitch = 1f
                sound.setSpatializationEnabled(true)
                sound.minDistance = 1f
                scene("3D: circling your head: front, right, behind, left", 12.0) { t ->
                    val angle = t * 2 * 2 * PI
                    sound.setPosition(3 * sin(angle).toFloat(), 0f, -3 * cos(angle).toFloat())
                }
                scene("3D: walking away in front of you, and coming back", 8.0) { t ->
                    sound.setPosition(0f, 0f, -(1 + 19 * sin(t * PI)).toFloat())
                }
                sound.setSpatializationEnabled(false)
                sound.setFadeInMilliseconds(-1f, 0f, 3000)
                scene("Fade: out over three seconds, to silence", 3.5)
                sound.stop()
            }
        }
        println("\nDone.")
    }
}
