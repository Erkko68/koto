package io.github.erkko68.koto.interop

import io.github.erkko68.koto.desktop.KotoLoader

internal actual fun loadLibrary() = KotoLoader.load()
