package com.tkey.crypto

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac

/**
 * Tag-Length-Value metadata serializer for Tesla command authentication.
 *
 * Mirrors `internal/authentication/metadata.go`. Each entry encodes as
 * `[tag][len(1B)][value]`; tags MUST be added in strictly increasing order;
 * `value` length must be ≤ 255 bytes. [checksum] terminates with `TAG_END (0xFF)`
 * followed by the message body, returning the underlying hash.
 *
 * Two flavors of [Sink]:
 *  - [Sha256]: produces SHA-256 of the encoded TLV+body, suitable as AES-GCM AAD
 *  - [Hmac]:   produces HMAC-SHA256 over the same bytes, suitable as a signature tag
 */
class Metadata private constructor(private val sink: Sink) {

    sealed interface Sink {
        fun update(bytes: ByteArray)
        fun finish(): ByteArray

        class Sha256 : Sink {
            private val md = MessageDigest.getInstance("SHA-256")
            override fun update(bytes: ByteArray) { md.update(bytes) }
            override fun finish(): ByteArray = md.digest()
        }

        class Hmac(private val mac: Mac) : Sink {
            override fun update(bytes: ByteArray) { mac.update(bytes) }
            override fun finish(): ByteArray = mac.doFinal()
        }
    }

    private var lastTag: Int = -1

    fun add(tag: Tag, value: ByteArray?): Metadata {
        if (value == null) return this
        check(tag.code > lastTag) { "tags must be added in increasing order; ${tag.name}(${tag.code}) after $lastTag" }
        require(value.size <= 255) { "metadata field too long: ${value.size}" }
        lastTag = tag.code
        sink.update(byteArrayOf(tag.code.toByte(), value.size.toByte()))
        sink.update(value)
        return this
    }

    fun addByte(tag: Tag, value: Int): Metadata = add(tag, byteArrayOf(value.toByte()))

    fun addUint32BE(tag: Tag, value: Int): Metadata =
        add(tag, ByteBuffer.allocate(4).putInt(value).array())

    /**
     * Append `TAG_END (0xFF)`, then [body], and return the sink's hash/tag.
     * Body is the protobuf-serialized command payload.
     */
    fun checksum(body: ByteArray = EMPTY): ByteArray {
        sink.update(TAG_END_BYTES)
        sink.update(body)
        return sink.finish()
    }

    enum class Tag(val code: Int) {
        SIGNATURE_TYPE(0),
        DOMAIN(1),
        PERSONALIZATION(2),
        EPOCH(3),
        EXPIRES_AT(4),
        COUNTER(5),
        CHALLENGE(6),
        FLAGS(7),
        REQUEST_HASH(8),
        FAULT(9),
    }

    companion object {
        const val TAG_END = 0xFF
        private val TAG_END_BYTES = byteArrayOf(TAG_END.toByte())
        private val EMPTY = ByteArray(0)

        fun sha256(): Metadata = Metadata(Sink.Sha256())
        fun hmac(mac: Mac): Metadata = Metadata(Sink.Hmac(mac))
    }
}
