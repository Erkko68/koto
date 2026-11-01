# Background

Why koto exists in the shape it does, and where it fits among filament-kmp and kolt.

## Why miniaudio

It is the only candidate that is permissively licensed, runs on every target above, and includes 3D positioning.

| Library | License | Web | Why not |
| :--- | :--- | :--- | :--- |
| **miniaudio** | Public domain / MIT-0 | Yes | Chosen |
| SoLoud | zlib | Yes | Runner-up: a more game-shaped API, but development has been slow |
| OpenAL Soft | LGPL | Via Emscripten's own OpenAL | LGPL is awkward for static linking on iOS |
| SDL3 audio + SDL_mixer | zlib | Yes | No real 3D positioning, and it pulls in SDL |
| FMOD, Wwise | Proprietary | Varies | Can't be redistributed inside a wrapper |
| PortAudio, RtAudio, cubeb, Oboe | Permissive | Weak or none | Device I/O only; the mixer would be ours to write |

[Steam Audio](https://valvesoftware.github.io/steam-audio/) (Apache 2.0) is a possible later addition for occlusion and
reflections computed from level geometry. It processes audio but does not play it, so it would sit on top of miniaudio.

## How big the API is

Counted from the public half of `miniaudio.h`, version 0.11.25 (koto pins 0.11.26):

| | Count |
| :--- | :--- |
| Public functions (`MA_API`) | 932 |
| Named types (structs, enums, unions) | about 170, of which 47 are enums and 44 are `*_config` structs |
| Callback types | 21 |

Most of it is low-level DSP. The part a game uses is much smaller:

| Layer | Functions |
| :--- | :--- |
| `ma_engine_*`: the listener, global volume, time | 44 |
| `ma_sound_*`: play, stop, volume, pitch, position, cone, fades | 84 |
| `ma_sound_group_*`: buses | 57 |
| `ma_resource_manager_*`: async loading, streaming | 62 |
| `ma_node_*`, `ma_node_graph_*`: effects routing | 41 |

## Where it fits

With rendering, physics and audio wrapped, a game built on these libraries still needs:

| System | Status | Plan |
| :--- | :--- | :--- |
| Window, surface, UI | Covered | filament-kmp's Compose Multiplatform layer |
| Game loop | Missing | Fixed physics timestep with render interpolation |
| Physics ↔ render sync | Missing | Copy kolt body transforms into Filament each frame |
| Entity model | Missing | [Fleks](https://github.com/Quillraven/Fleks) or a plain scene graph |
| Input | Partial | Compose gives keyboard and pointer; gamepads need per-platform code |
| Assets | Partial | gltfio loads models; async loading and collision shapes from glTF are missing |
| Animation | Partial | gltfio plays clips; blending and state machines are missing |
| Networking | Transport covered | Ktor; browsers need WebRTC or WebTransport for unreliable traffic, and replication is ours to write |
| Save data | Covered | kotlinx.serialization |
| Debug drawing | Missing | Jolt's debug renderer drawn as Filament lines |

Navigation meshes (Recast/Detour), particles and an editor are out of scope for now.

The intent is one small glue module and a sample game, not an engine: the sample shows which of these rows deserve a
library of their own.

## The name

A koto is a Japanese string instrument, and the name starts with "Ko" like kolt. It has not been checked against
existing projects or Maven coordinates.
