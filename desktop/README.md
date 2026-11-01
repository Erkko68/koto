# `:desktop`: the JVM's native runtime

Builds `libkoto.{dylib,so}` / `koto.dll` (the C API and its JNI layer, over miniaudio) for the host and ships the loader
that finds it at run time. `:kotlin:koto`'s JVM target depends on it.

| Artifact | Contents |
|---|---|
| `koto-desktop` | `KotoLoader`, no natives. Its Gradle metadata depends on **every** runtime jar below by default, and exposes one variant per platform (`OperatingSystemFamily` × `MachineArchitecture`) |
| `koto-runtime-{macos-arm64, linux-x64, linux-arm64, windows-x64, windows-arm64}` | one platform's `libkoto` (+ `.sha256`) under `natives/<os>-<arch>/` |

- **Native build:** `:desktop:cmakeBuild` builds the `koto-jni` target of [`c/CMakeLists.txt`](../c/CMakeLists.txt)
  for the host, with `jni.h` from the JDK running Gradle, into `build/koto-c/<os>-<arch>/`.
- **Runtime jars:** each `runtime-<id>` jar takes the host's library from `cmakeBuild` and every other platform's
  from `-PcArtifactsDir=<dir>` (`<dir>/<id>/`), which CI's publish job fills; publishing an empty jar to a repository
  fails.
- **Loading:** `loadNativeLibrary()` reaches `KotoLoader.load()`, which extracts the library once into a content-hash-keyed
  cache dir (`~/.koto/koto-<hash>/`) and `System.load`s it. Knobs: `koto.library.path` (load from that directory
  instead), `koto.data.path` (cache root), `koto.data.cleanup.days` (stale-cache purge age, default 30, `<= 0`
  disables).
- **Seal:** only `Java_*` and `JNI_OnLoad` are exported; miniaudio stays local to the image. On Linux the C++ runtime
  is linked in, so it needs only glibc; on Windows, the static C runtime.

## One platform instead of five

Plain consumers get every runtime jar (zero config). Gradle consumers that set the two attributes get just theirs:

```kotlin
configurations.matching { it.isCanBeResolved }.configureEach {
    attributes {
        attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, objects.named(OperatingSystemFamily.MACOS))
        attribute(MachineArchitecture.ARCHITECTURE_ATTRIBUTE, objects.named(MachineArchitecture.ARM64))
    }
}
```

Packaged desktop apps (jpackage / Compose Desktop distributions) bundle the whole runtime classpath, so per-platform
installers should set these attributes, or depend on one runtime jar directly (it pulls the loader and excludes its
siblings):

```kotlin
implementation("io.github.erkko68.koto:koto-runtime-macos-arm64:<version>")
```
