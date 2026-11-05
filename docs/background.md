# Background

Why koto exists in the shape it does.

## Why miniaudio

It is the only candidate that is permissively licensed, runs on every target above, and includes 3D positioning.

| Library | License | Web | Why not |
| :--- | :--- | :--- | :--- |
| **miniaudio** | Public domain / MIT-0 | Yes | Chosen |
| SoLoud | zlib | Yes | Runner-up: a higher-level API, but development has been slow |
| OpenAL Soft | LGPL | Via Emscripten's own OpenAL | LGPL is awkward for static linking on iOS |
| SDL3 audio + SDL_mixer | zlib | Yes | No real 3D positioning, and it pulls in SDL |
| FMOD, Wwise | Proprietary | Varies | Can't be redistributed inside a wrapper |
| PortAudio, RtAudio, cubeb, Oboe | Permissive | Weak or none | Device I/O only; the mixer would be ours to write |

[Steam Audio](https://valvesoftware.github.io/steam-audio/) (Apache 2.0) is a possible later addition for occlusion and
reflections computed from scene geometry. It processes audio but does not play it, so it would sit on top of miniaudio.

## How big the API is

Counted from the public half of `miniaudio.h`, version 0.11.25 (koto pins 0.11.26):

| | Count |
| :--- | :--- |
| Public functions (`MA_API`) | 932 |
| Named types (structs, enums, unions) | about 170, of which 47 are enums and 44 are `*_config` structs |
| Callback types | 21 |

Most of it is low-level DSP. The part that plays audio is much smaller:

| Layer | Functions |
| :--- | :--- |
| `ma_engine_*`: the listener, global volume, time | 44 |
| `ma_sound_*`: play, stop, volume, pitch, position, cone, fades | 84 |
| `ma_sound_group_*`: buses | 57 |
| `ma_resource_manager_*`: async loading, streaming | 62 |
| `ma_node_*`, `ma_node_graph_*`: effects routing | 41 |

## The name

A koto is a Japanese string instrument.
