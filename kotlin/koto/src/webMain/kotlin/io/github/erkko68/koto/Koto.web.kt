package io.github.erkko68.koto

import kotlin.js.Promise

/** koto.js's factory (`-sEXPORT_NAME`). */
private external fun createKotoModule(): Promise<JsAny>

/** Loads koto.wasm, next to the koto.js the page includes; call it once, and everything else after it resolves. */
fun loadKoto(): Promise<JsAny> = createKotoModule()
