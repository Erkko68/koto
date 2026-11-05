# koto

[![miniaudio](https://img.shields.io/badge/miniaudio-0.11.26-orange)](https://github.com/mackron/miniaudio)
[![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE.md)
[![API reference](https://img.shields.io/badge/API-reference-blue)](https://erkko68.github.io/koto/)

[![Android](https://github.com/Erkko68/koto/actions/workflows/status-android.yml/badge.svg)](https://github.com/Erkko68/koto/actions/workflows/ci.yml)
[![iOS](https://github.com/Erkko68/koto/actions/workflows/status-ios.yml/badge.svg)](https://github.com/Erkko68/koto/actions/workflows/ci.yml)
[![JVM](https://github.com/Erkko68/koto/actions/workflows/status-jvm.yml/badge.svg)](https://github.com/Erkko68/koto/actions/workflows/ci.yml)
[![JS](https://github.com/Erkko68/koto/actions/workflows/status-js.yml/badge.svg)](https://github.com/Erkko68/koto/actions/workflows/ci.yml)
[![Wasm](https://github.com/Erkko68/koto/actions/workflows/status-wasm.yml/badge.svg)](https://github.com/Erkko68/koto/actions/workflows/ci.yml)

**koto** is [miniaudio](https://miniaud.io/) for Kotlin Multiplatform: the same playback, mixing and 3D positional audio
on **Android**, **iOS**, the **JVM** and the **Web (JS & Wasm)**, from one API in `commonMain`.

```kotlin
val engine = EngineConfig.init().use { Engine.init(it) }
engine.volume = 0.5f

val sound = Sound.initFromFile(engine, "shot.wav", 0, null, null)
sound.setPosition(1f, 0f, -2f)
sound.start()

// Once it's done with:
sound.close()
engine.close()
```

If you know miniaudio, you know koto: the objects, functions and enums are miniaudio's, in Kotlin's shape
(`ma_engine_set_volume(engine, v)` is `engine.volume = v`). It sits next to
[filament-kmp](https://github.com/Erkko68/filament-kmp) (rendering) and [kolt](https://github.com/Erkko68/kolt) (physics).

## Platform support

Every platform runs on **one C API generated from `miniaudio.h`**, and the Kotlin API is generated once, in `commonMain`,
on top of it. The API and its behavior are the same everywhere:

| Platform | Targets | How Kotlin reaches miniaudio |
| :--- | :--- | :--- |
| **Android** | `arm64-v8a`, `x86_64`, minSdk 24 | JNI, `libkoto.so` in the library's jniLibs |
| **iOS** | `iosArm64`, `iosSimulatorArm64`, iOS 15 | Kotlin/Native, linked statically |
| **JVM** | macOS arm64; Linux and Windows, x64 and arm64 | JNI, `libkoto` out of a runtime jar per platform |
| **Web** | `js`, `wasmJs`, in a browser | miniaudio compiled to wasm with Emscripten, `koto.js` + `koto.wasm` |

On the web, the page includes `koto.js` and waits for `createKotoModule()` before the first call, and a file path means
one in Emscripten's virtual filesystem: pass the audio as bytes instead.

## Installation

koto isn't on Maven Central yet. To build it from source, clone with the miniaudio submodule and run the tests:

```sh
git clone --recurse-submodules https://github.com/Erkko68/koto.git
./gradlew :kotlin:koto:jvmTest :kotlin:koto:iosSimulatorArm64Test
```

The native builds need CMake and Ninja; regenerating the API needs `clang` and `clang++`.

## API strategy

The public API follows **miniaudio's C API**, the one [its documentation](https://miniaud.io/docs/) and header describe,
adapted only where Kotlin has its own shape:

- **Classes for its objects**: a function is a member of the object it's named after (`ma_sound_start(sound)` is
  `sound.start()`), and names lose `ma_` and read as Kotlin's (`ma_engine_config` is `EngineConfig`, `ma_format_f32` is
  `Format.F32`).
- **Kotlin properties** for `get_x`/`set_x` pairs and config fields (`engine.volume`, `config.channels`).
- **`init` makes, `close()` gives back**: `Sound.initFromFile(…)` makes a sound, and `close()` is its `ma_sound_uninit`.
- **Results, not out-parameters**: `sound.lengthInPcmFrames` returns what `ma_sound_get_length_in_pcm_frames` writes
  through a pointer; a failing `ma_result` throws `NativeException`.
- **The modules a game uses**: the engine, sounds and groups, the resource manager, the node graph and its effect nodes,
  decoders and encoders, data sources, devices and contexts. Of those 1191 C functions, 1176 are wrapped;
  [`c/api-coverage.txt`](c/api-coverage.txt) names the rest. Raw DSP (filters, resamplers, ring buffers, format
  converters) stays out until something needs it: one line in [`c/api-headers.txt`](c/api-headers.txt).
- **Callbacks are lambdas**: `config.setDataCallback { device, output, input, frameCount -> }`, `setStopCallback`,
  `setNotificationCallback` on a device's or an engine's config, `engineConfig.setOnProcess { frames, frameCount -> }`,
  `sound.setEndCallback { }`, `context.enumerateDevices { context, type, info -> true }`, `dataSourceSetNextCallback`,
  and your own I/O under `Decoder.init(onRead, onSeek, config)` and `Encoder.init(onWrite, onSeek, config)`, where a
  read or write returns its result and how many bytes it moved. miniaudio keeps each until the object it's set on is
  closed, a next callback until it or its data source is. The audio ones run on the audio thread, where a GC pause is a
  glitch: keep them short and allocation-free. A lambda captures what it needs, so the user data miniaudio would hand
  back isn't bound.
- **Audio as bytes**: read a file however your app reads files (Compose resources' `Res.readBytes`, kotlinx-io, an
  Android asset) and hand koto the `ByteArray`, which it copies: `Decoder.initMemory(bytes, config)` decodes its own
  copy, and `resourceManager.registerEncodedData("shot.wav", bytes)` keeps one until the resource manager closes, for
  `Sound.initFromFile(engine, "shot.wav", …)` to play by that name. PCM you make is interleaved f32 frames:
  `resourceManager.registerDecodedData("tone", frames, channels, sampleRate)`, or `AudioBuffer.initCopy(frames,
  channels)` for a data source. The same code works on every platform, the web included. File paths still work where
  there's a filesystem.
- **Strings are copied**: a config keeps its own copy of a string set on it (`soundConfig.filePath`, PulseAudio and
  JACK names) until it's closed.
- **Nodes and data sources are pointers**: `ma_node` and `ma_data_source` are `void` in C, so what takes one takes a
  `NativePointer`: an object's `…Handle`.

## Documentation

- **[API reference](https://erkko68.github.io/koto/)**: every class, generated by Dokka.
- **[Background](docs/background.md)**: why miniaudio, how big its API is, and where koto fits in a game.
- **[Repository Structure](docs/internals/repo-structure.md)**: what lives where, the generator and its tasks.
- **[The desktop runtime](desktop/README.md)**: `libkoto` in a jar per platform, and how it's loaded.
- **[CI and Releasing](.github/workflows/README.md)**: the workflows, the API reference on GitHub Pages, publishing to
  Maven Central.

### Upstream miniaudio (authoritative for audio concepts)
- **[miniaudio documentation](https://miniaud.io/docs/)**: the engine, sounds, the node graph, the resource manager.
