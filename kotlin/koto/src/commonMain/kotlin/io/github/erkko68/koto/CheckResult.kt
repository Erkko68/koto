package io.github.erkko68.koto

import io.github.erkko68.koto.capi.Koto_ma_result_description
import io.github.erkko68.koto.interop.NativeException
import io.github.erkko68.koto.interop.stringFromInterop

private const val SUCCESS = 0
private const val AT_END = -17

/** Throws what a `ma_result` of a failure says, after [cleanUp]. The end of a stream isn't one: a read returns what it got. */
internal inline fun checkResult(result: Int, cleanUp: () -> Unit = {}) {
    if (result == SUCCESS || result == AT_END) return
    cleanUp()
    throw NativeException(stringFromInterop(Koto_ma_result_description(result)) ?: "miniaudio error $result")
}
