package io.github.erkko68.koto

import io.github.erkko68.koto.interop.loadNativeLibrary
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A device's callbacks, on miniaudio's null backend: its own audio thread, with no sound card. */
class DeviceCallbackTest {
    private fun nullContext(): Context {
        loadNativeLibrary()
        return Context.init(listOf(Backend.NULL), 1, null)
    }

    @Test
    fun aStartedDeviceCallsItsCallbacks() = nullContext().use { context ->
        val frames = AtomicLong()
        val played = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val notified = Collections.synchronizedList(ArrayList<DeviceNotificationType>())
        val device = DeviceConfig.init(DeviceType.PLAYBACK).use { config ->
            config.playbackFormat = Format.F32
            config.playbackChannels = 2
            config.sampleRate = 48000
            config.setDataCallback { _, output, _, frameCount ->
                assertTrue(output != 0L)
                frames.addAndGet(frameCount.toLong())
                played.countDown()
            }
            config.setNotificationCallback { notified += it!!.type }
            config.setStopCallback { stopped.countDown() }
            Device.init(context, config)
        }
        device.use {
            it.start()
            assertTrue(played.await(5, TimeUnit.SECONDS), "no data callback")
            it.stop()
            assertTrue(stopped.await(5, TimeUnit.SECONDS), "no stop callback")
        }
        assertTrue(frames.get() > 0)
        assertEquals(listOf(DeviceNotificationType.STARTED, DeviceNotificationType.STOPPED), notified.toList())
    }

    @Test
    fun enumeratesTheNullDevices() = nullContext().use { context ->
        val names = ArrayList<String>()
        context.enumerateDevices { _, type, info ->
            if (type == DeviceType.PLAYBACK) names += info!!.name!!
            true
        }
        assertTrue(names.isNotEmpty() && names.all { it.isNotEmpty() }, "$names")
    }
}
