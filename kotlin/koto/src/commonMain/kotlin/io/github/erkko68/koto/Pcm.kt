package io.github.erkko68.koto

import io.github.erkko68.koto.interop.interopScope

// PCM a Kotlin program makes or decodes itself, as interleaved 32-bit float frames: the frames of what C takes as a
// `const void*` and a frame count, in the one format a FloatArray holds.

/**
 * Registers [frames], interleaved f32 PCM of [channels] channels at [sampleRate], under [name], for
 * `Sound.initFromFile(engine, name, …)` to play. The resource manager keeps a copy until it's closed.
 */
fun ResourceManager.registerDecodedData(name: String, frames: FloatArray, channels: Int, sampleRate: Int) =
    interopScope { registerDecodedData(name, toInterop(frames), frameCount(frames, channels), Format.F32, channels, sampleRate) }

/** An audio buffer of its own copy of [frames], interleaved f32 PCM of [channels] channels: a data source. */
fun AudioBuffer.Companion.initCopy(frames: FloatArray, channels: Int): AudioBuffer =
    interopScope { AudioBufferConfig.init(Format.F32, channels, frameCount(frames, channels), toInterop(frames), null).use { AudioBuffer.initCopy(it) } }

private fun frameCount(frames: FloatArray, channels: Int): Long {
    require(channels > 0 && frames.size % channels == 0) { "${frames.size} samples aren't whole frames of $channels channels" }
    return (frames.size / channels).toLong()
}
