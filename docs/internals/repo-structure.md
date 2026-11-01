# Repository Structure

```
miniaudio/      miniaudio, a submodule pinned to the release koto binds
c/              the C API over it, and how it's built; api-headers.txt says what's bound
kotlin/koto/    the Kotlin Multiplatform module
desktop/        the JVM's native runtime: the loader, and libkoto in a jar per platform
build-logic/    the generator, and the Gradle tasks that run it
```

Most of `c/` and `kotlin/koto/src/commonMain` is generated and committed.

| Task | Does |
| :--- | :--- |
| `./gradlew generateApi` | Regenerates everything committed: the C API, the externals, the Kotlin API, `c/api-coverage.txt`. |
| `./gradlew apiCheck` | Fails when what's committed isn't what `generateApi` would write. |
| `./gradlew -p build-logic test` | The generator on its fixture library. |
| `KOTO_LISTEN=<file> ./gradlew :kotlin:koto:jvmTest --tests '*ListenTest*' --rerun` | Plays a WAV, FLAC or MP3 through the default output device, a scene at a time, saying what to listen for. `KOTO_VOLUME` sets its loudness. |

The generator started as kolt's, the one that writes Kotlin. It reads `miniaudio.h` as C++, so what C can't express
(structs by value, caller-allocated objects) comes from the same bridges that serve a C++ library, and three things
were added for C: `typedef struct {…} name;`, fields of unnamed structs, and functions named after their object
(`CObjects`). It needs `clang` and `clang++` on the path.
