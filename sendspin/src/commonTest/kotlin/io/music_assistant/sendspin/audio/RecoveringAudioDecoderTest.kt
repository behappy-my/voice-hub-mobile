package io.music_assistant.sendspin.audio

import io.music_assistant.sendspin.api.AudioCodec
import io.music_assistant.sendspin.api.AudioDecoder
import io.music_assistant.sendspin.api.AudioFormatSpec
import io.music_assistant.sendspin.api.RecoveringAudioDecoder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecoveringAudioDecoderTest {
    private val format = AudioFormatSpec(AudioCodec.FLAC, 2, 48_000, 16)

    private class Decoder(
        var failDecode: Boolean = false,
        var failReset: Boolean = false,
        var failConfigure: Boolean = false,
    ) : AudioDecoder {
        var released = false
        var configured: AudioFormatSpec? = null
        var header: ByteArray? = null
        var calls = 0
        override val outputBitDepth = 16
        override val outputCodec = AudioCodec.PCM
        override fun configure(format: AudioFormatSpec, codecHeader: ByteArray?) {
            configured = format
            header = codecHeader
            if (failConfigure) error("cannot configure")
        }
        override fun decode(input: ByteArray, offset: Int, length: Int): ByteArray {
            calls++
            check(!released && !failDecode) { "Released state" }
            return input.copyOfRange(offset, offset + length)
        }
        override fun reset() { check(!failReset) { "flush failed" } }
        override fun release() { released = true }
    }

    @Test
    fun releasedCodecIsRecreatedAndTheSameFrameRetriedWithOriginalHeader() {
        val created = mutableListOf<Decoder>()
        val wrapped = RecoveringAudioDecoder {
            Decoder(failDecode = created.isEmpty()).also { created += it }
        }
        val csd = byteArrayOf(1, 2, 3)
        wrapped.configure(format, csd)
        csd[0] = 99
        assertContentEquals(byteArrayOf(8, 9), wrapped.decode(byteArrayOf(7, 8, 9, 0), 1, 2))
        assertEquals(2, created.size)
        assertTrue(created[0].released)
        assertEquals(format, created[1].configured)
        assertContentEquals(byteArrayOf(1, 2, 3), created[1].header)
        assertEquals(1, created[0].calls)
        assertEquals(1, created[1].calls)
    }

    @Test
    fun persistentFailureDoesNotRecreateInAnUnboundedLoop() {
        val created = mutableListOf<Decoder>()
        val wrapped = RecoveringAudioDecoder { Decoder(failDecode = true).also { created += it } }
        wrapped.configure(format, null)
        repeat(20) { assertTrue(wrapped.decode(byteArrayOf(1), 0, 1).isEmpty()) }
        assertEquals(2, created.size)
        assertTrue(created.all { it.released })
        assertEquals(2, created.sumOf { it.calls })
        wrapped.configure(format, null)
        assertEquals(3, created.size, "a fresh stream can initialize a new decoder")
    }

    @Test
    fun flushFailureRecreatesAConfiguredDecoder() {
        val created = mutableListOf<Decoder>()
        val wrapped = RecoveringAudioDecoder {
            Decoder(failReset = created.isEmpty()).also { created += it }
        }
        wrapped.configure(format, byteArrayOf(2))
        wrapped.reset()
        assertTrue(created[0].released)
        assertEquals(format, created[1].configured)
        assertContentEquals(byteArrayOf(2), created[1].header)
        assertContentEquals(byteArrayOf(3), wrapped.decode(byteArrayOf(3), 0, 1))
    }

    @Test
    fun laterSecondFailureEndsRecoveryUntilANewStream() {
        val created = mutableListOf<Decoder>()
        val wrapped = RecoveringAudioDecoder {
            Decoder(failDecode = created.isEmpty()).also { created += it }
        }
        wrapped.configure(format, null)
        wrapped.decode(byteArrayOf(1), 0, 1)
        created[1].failDecode = true
        assertTrue(wrapped.decode(byteArrayOf(2), 0, 1).isEmpty())
        assertEquals(2, created.size)
        assertTrue(created[1].released)
    }

    @Test
    fun releaseDoesNotResurrectADecoder() {
        val created = mutableListOf<Decoder>()
        val wrapped = RecoveringAudioDecoder { Decoder().also { created += it } }
        wrapped.configure(format, null)
        wrapped.release()
        wrapped.reset()
        assertTrue(wrapped.decode(byteArrayOf(1), 0, 1).isEmpty())
        assertEquals(1, created.size)
        assertTrue(created[0].released)
    }

    @Test
    fun configureFailureReleasesThePartialInstance() {
        val raw = Decoder(failConfigure = true)
        val wrapped = RecoveringAudioDecoder { raw }
        assertFailsWith<IllegalStateException> { wrapped.configure(format, null) }
        assertTrue(raw.released)
    }

    @Test
    fun failedRecoveryConfigurationStaysBounded() {
        val created = mutableListOf<Decoder>()
        val wrapped = RecoveringAudioDecoder {
            Decoder(failDecode = true, failConfigure = created.isNotEmpty()).also { created += it }
        }
        wrapped.configure(format, null)
        repeat(10) { assertTrue(wrapped.decode(byteArrayOf(1), 0, 1).isEmpty()) }
        assertEquals(2, created.size)
        assertTrue(created.all { it.released })
    }
}
