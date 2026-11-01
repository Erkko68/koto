package io.github.erkko68.koto.interop

import java.nio.ByteBuffer

// JVM and Android: externals are JNI methods, whose forwarders generateBindings writes; arrays are copied to native memory and back.

actual typealias NativePointer = Long

actual val NullPointer: NativePointer = 0L

actual fun loadNativeLibrary() {
    KotoJni
}

actual class InteropScope actual constructor() {
    private val allocations = ArrayList<Long>(2)

    private inline fun copyIn(count: Int, width: Int, put: (ByteBuffer) -> Unit): NativePointer {
        if (count == 0) return NullPointer
        val ptr = KotoJni.alloc(count.toLong() * width)
        check(ptr != 0L) { "calloc(${count * width}) failed" }
        allocations += ptr
        put(KotoJni.buffer(ptr, count * width))
        return ptr
    }

    actual fun toInterop(array: ByteArray?): NativePointer = copyIn(array?.size ?: 0, 1) { it.put(array!!) }
    actual fun toInterop(array: IntArray?): NativePointer = copyIn(array?.size ?: 0, 4) { it.asIntBuffer().put(array!!) }
    actual fun toInterop(array: LongArray?): NativePointer = copyIn(array?.size ?: 0, 8) { it.asLongBuffer().put(array!!) }
    actual fun toInterop(array: FloatArray?): NativePointer = copyIn(array?.size ?: 0, 4) { it.asFloatBuffer().put(array!!) }
    actual fun toInterop(array: DoubleArray?): NativePointer = copyIn(array?.size ?: 0, 8) { it.asDoubleBuffer().put(array!!) }

    // ponytail: 8-byte pointers; Android's 32-bit ABIs, which aren't built, need the pointer size.
    actual fun toInterop(pointers: List<NativePointer>): NativePointer = toInterop(pointers.toLongArray())

    private inline fun NativePointer.copyOut(count: Int, width: Int, get: (ByteBuffer) -> Unit) {
        if (this != NullPointer && count > 0) get(KotoJni.buffer(this, count * width))
    }

    actual fun NativePointer.fromInterop(result: ByteArray) = copyOut(result.size, 1) { it.get(result) }
    actual fun NativePointer.fromInterop(result: IntArray) = copyOut(result.size, 4) { it.asIntBuffer().get(result) }
    actual fun NativePointer.fromInterop(result: LongArray) = copyOut(result.size, 8) { it.asLongBuffer().get(result) }
    actual fun NativePointer.fromInterop(result: FloatArray) = copyOut(result.size, 4) { it.asFloatBuffer().get(result) }
    actual fun NativePointer.fromInterop(result: DoubleArray) = copyOut(result.size, 8) { it.asDoubleBuffer().get(result) }

    actual fun release() {
        allocations.forEach(KotoJni::free)
        allocations.clear()
    }
}

actual fun stringFromInterop(ptr: NativePointer): String? = KotoJni.readString(ptr)

actual object Upcalls {
    actual val function: NativePointer = KotoJni.upcallFunction()

    // A global reference, which the native Upcall drops at index -1.
    actual fun reference(target: NativeObject): NativePointer = KotoJni.newReference(target)
}

actual fun readSlot(args: NativePointer, index: Int): Long = KotoJni.getLong(args + index * 8L)
actual fun readPointerSlot(args: NativePointer, index: Int): NativePointer = readSlot(args, index)
actual fun writeSlot(result: NativePointer, value: Long) = KotoJni.putLong(result, value)

actual fun readFloats(ptr: NativePointer, count: Int): FloatArray =
    FloatArray(count).also { if (count > 0) KotoJni.buffer(ptr, count * 4).asFloatBuffer().get(it) }

actual fun writeFloats(ptr: NativePointer, values: FloatArray) {
    if (values.isNotEmpty()) KotoJni.buffer(ptr, values.size * 4).asFloatBuffer().put(values)
}

actual fun readPointers(ptr: NativePointer, count: Int): List<NativePointer> =
    if (count == 0) emptyList() else LongArray(count).also { KotoJni.buffer(ptr, count * 8).asLongBuffer().get(it) }.toList()

@Suppress("NOTHING_TO_INLINE")
actual inline fun FloatArray.readF32(index: Int): Float = this[index]
