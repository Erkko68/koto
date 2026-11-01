package io.github.erkko68.koto.interop

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Loads libkoto: on Android from the app's native libraries, on a desktop from its runtime jar (:desktop's KotoLoader). */
internal expect fun loadLibrary()

/**
 * The hand-written half of the JNI layer (native side: c/koto/jni/KotoJni.cpp): native memory, strings, and the
 * function C++ calls a Kotlin class's overrides through. Loads libkoto, the C API and its JNI forwarders.
 */
internal object KotoJni {
    init {
        loadLibrary()
    }

    /** Zero-filled native memory (calloc). */
    @JvmStatic external fun alloc(size: Long): Long
    @JvmStatic external fun free(ptr: Long)

    /** A direct ByteBuffer over [size] bytes of native memory at [ptr], in big-endian (JNI's default). */
    @JvmStatic private external fun view(ptr: Long, size: Long): ByteBuffer

    /** [view] in native byte order, as C arrays need. */
    fun buffer(ptr: Long, size: Int): ByteBuffer = view(ptr, size.toLong()).order(ByteOrder.nativeOrder())

    /** Reads a NUL-terminated UTF-8 string, or null for a null pointer. */
    @JvmStatic external fun readString(ptr: Long): String?

    // ponytail: a JNI call per upcall slot; Unsafe or the FFM API if listeners show up in profiles.
    @JvmStatic external fun getLong(ptr: Long): Long
    @JvmStatic external fun putLong(ptr: Long, value: Long)

    /** Bridge.hpp's `Upcall`, which calls [upcall] with what [newReference] made of the object. */
    @JvmStatic external fun upcallFunction(): Long
    @JvmStatic external fun newReference(target: Any): Long

    /** Called by the native `Upcall`, on whichever thread C++ calls the function from. */
    @JvmStatic
    fun upcall(target: Any, method: Int, args: Long, result: Long): Boolean = (target as NativeObject).nativeUpcall(method, args, result)
}
