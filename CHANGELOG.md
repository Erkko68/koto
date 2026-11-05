# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). A release's section is its GitHub
release's notes (`.github/workflows/publish.yml`).

## [Unreleased]

## [0.1.0] - 2026-11-05

### Added
- **miniaudio 0.11.26 for Kotlin Multiplatform**: its engine, sounds, resource manager, node graph, decoders, encoders,
  data sources and devices, generated, in `commonMain`, on Android, iOS, the JVM (macOS arm64, Linux and Windows x64 and
  arm64) and the web (JS and Wasm).
- **Callbacks as Kotlin lambdas**: a device's data, stop and notification callbacks, an engine's `onProcess`, data and
  notification callbacks, a sound's end callback, a data source's next, `Context.enumerateDevices`, and Kotlin I/O
  under `Decoder.init` and `Encoder.init`. `DeviceInfo` and `DeviceNotification` read their fields.
- **Strings a config keeps**: `SoundConfig.filePath`, the PulseAudio and JACK names, copied into the config.
- **Audio as bytes**: `Decoder.initMemory` and `ResourceManager.registerEncodedData` take a `ByteArray` and keep a
  copy, so audio read with Compose resources or any other library plays on every platform. PCM as a `FloatArray` of
  frames: `ResourceManager.registerDecodedData` and `AudioBuffer.initCopy`.
- `ContextConfig.dsound.hWnd` and `AudioBufferConfig.data`.

### Removed
- `Noise.initPreallocated`: it needs native memory Kotlin doesn't allocate; `init` allocates its own.
- `DeviceConfig.userData`: koto's callbacks keep their lambdas there.
- `ContextConfig.userData` and the custom backends' user data, whose vtables aren't bound.

### Fixed
- Enums whose values miniaudio sets explicitly: `DeviceType` was off by one, and `ChannelMixMode.DEFAULT` and
  `MonoExpansionMode.DEFAULT` weren't their aliases.

[Unreleased]: https://github.com/Erkko68/koto/compare/0.1.0...HEAD
[0.1.0]: https://github.com/Erkko68/koto/releases/tag/0.1.0
