package com.tkey.crypto

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Symmetric crypto context derived from an ECDH exchange with the vehicle.
 *
 * Mirrors `internal/authentication/native.go` in teslamotors/vehicle-command:
 *  - shared secret = 32-byte X coordinate (big-endian) of `localPrivate · remotePublic`
 *  - K = `SHA1(shared_secret)[:16]` (16-byte AES-GCM key)
 *  - HMAC subkey for label = `HMAC-SHA256(K, label_utf8)`
 */
class Session(val key: ByteArray) {

    init {
        require(key.size == KEY_SIZE) { "K must be $KEY_SIZE bytes" }
    }

    private val rng = SecureRandom()

    /** AES-128-GCM seal with a fresh random 12-byte nonce. */
    fun encrypt(plaintext: ByteArray, aad: ByteArray): EncryptedFrame {
        val nonce = ByteArray(NONCE_SIZE).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad)
        }
        val ctAndTag = cipher.doFinal(plaintext)
        val ct = ctAndTag.copyOfRange(0, ctAndTag.size - TAG_BYTES)
        val tag = ctAndTag.copyOfRange(ctAndTag.size - TAG_BYTES, ctAndTag.size)
        return EncryptedFrame(nonce, ct, tag)
    }

    /** AES-128-GCM open. Throws on auth failure. */
    fun decrypt(nonce: ByteArray, ciphertext: ByteArray, tag: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad)
        }
        return cipher.doFinal(ciphertext + tag)
    }

    /**
     * AES-128-ECB encrypt of a single 16-byte block under [key]. Used by the Tesla
     * keycard's INS 0x11 challenge/response — the card returns
     * `AES-ECB-Encrypt(K, challenge)` where K is the same `SHA1(X)[:16]` reduction this
     * [Session] already derives. Do not use for anything else — ECB has no place in
     * the BLE protocol; it lives here only because the keycard reuses the K derivation.
     */
    fun encryptBlock(block: ByteArray): ByteArray {
        require(block.size == KEY_SIZE) { "AES block must be $KEY_SIZE bytes" }
        val cipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        }
        return cipher.doFinal(block)
    }

    /**
     * Returns a fresh HMAC-SHA256 [Mac] keyed with `subkey = HMAC-SHA256(K, label_utf8)`.
     * The caller updates with the bytes to authenticate and reads `doFinal()` as the tag.
     */
    fun newHmac(label: String): Mac {
        val subkey = Mac.getInstance(HMAC_SHA256).run {
            init(SecretKeySpec(key, HMAC_SHA256))
            doFinal(label.toByteArray(Charsets.UTF_8))
        }
        return Mac.getInstance(HMAC_SHA256).apply {
            init(SecretKeySpec(subkey, HMAC_SHA256))
        }
    }

    data class EncryptedFrame(val nonce: ByteArray, val ciphertext: ByteArray, val tag: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is EncryptedFrame &&
                nonce.contentEquals(other.nonce) &&
                ciphertext.contentEquals(other.ciphertext) &&
                tag.contentEquals(other.tag)

        override fun hashCode(): Int =
            (nonce.contentHashCode() * 31 + ciphertext.contentHashCode()) * 31 + tag.contentHashCode()
    }

    companion object {
        const val KEY_SIZE = 16
        const val NONCE_SIZE = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        private const val HMAC_SHA256 = "HmacSHA256"

        /** Derive K from the 32-byte X coordinate of the ECDH shared secret. */
        fun fromSharedSecret(sharedX: ByteArray): Session {
            require(sharedX.size == 32) { "shared secret must be 32 bytes (P-256 X coordinate); got ${sharedX.size}" }
            val sha1 = MessageDigest.getInstance("SHA-1").digest(sharedX)
            return Session(sha1.copyOfRange(0, KEY_SIZE))
        }

        /**
         * Parse a SEC1 uncompressed P-256 public key: `0x04 || X(32) || Y(32)` = 65 bytes.
         *
         * Validates that the point actually lies on the curve before returning. The
         * AndroidKeyStore ECDH provider would reject an off-curve point at `doPhase()`
         * time, but the keycard path ([Keycard.respondToChallenge]) consumes vehicle-
         * supplied bytes and we'd rather fail at parse than risk an invalid-curve
         * attack against any future provider that's less careful.
         */
        fun decodePublicKey(uncompressed: ByteArray): ECPublicKey {
            require(uncompressed.size == 65 && uncompressed[0] == 0x04.toByte()) {
                "expected uncompressed P-256 (65 bytes, leading 0x04); got ${uncompressed.size} bytes"
            }
            val x = BigInteger(1, uncompressed.copyOfRange(1, 33))
            val y = BigInteger(1, uncompressed.copyOfRange(33, 65))
            requireOnP256(x, y)
            return KeyFactory.getInstance("EC").generatePublic(
                ECPublicKeySpec(ECPoint(x, y), P256_PARAMS)
            ) as ECPublicKey
        }

        /**
         * Reject points that aren't on the P-256 curve. Both coordinates must be in
         * `[0, p)` and satisfy `y² ≡ x³ + a·x + b (mod p)` (for P-256, `a = p − 3`).
         */
        private fun requireOnP256(x: BigInteger, y: BigInteger) {
            val curve = P256_PARAMS.curve
            val p = (curve.field as ECFieldFp).p
            require(x.signum() >= 0 && x < p && y.signum() >= 0 && y < p) {
                "P-256 coordinates out of field"
            }
            val lhs = y.modPow(BIG_TWO, p)
            val rhs = x.modPow(BIG_THREE, p).add(curve.a.multiply(x)).add(curve.b).mod(p)
            require(lhs == rhs) { "P-256 public key point not on curve" }
        }

        private val BIG_TWO = BigInteger.valueOf(2)
        private val BIG_THREE = BigInteger.valueOf(3)

        private val P256_PARAMS: ECParameterSpec by lazy {
            AlgorithmParameters.getInstance("EC").run {
                init(ECGenParameterSpec("secp256r1"))
                getParameterSpec(ECParameterSpec::class.java)
            }
        }
    }
}
