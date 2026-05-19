package com.tkey.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement

/**
 * TKey's persistent NIST-P256 identity, stored in [AndroidKeyStore][KEYSTORE] and
 * hardware-backed where the device supports it. The private key never leaves the
 * secure element — ECDH happens inside [Identity] via Android's KeyAgreement API.
 */
class Identity private constructor(
    private val keyStore: KeyStore,
    private val alias: String,
) {

    val isHardwareBacked: Boolean
        get() {
            val factory = java.security.KeyFactory.getInstance("EC", KEYSTORE)
            val keyInfo = factory.getKeySpec(
                privateKeyEntry().privateKey,
                android.security.keystore.KeyInfo::class.java,
            )
            return keyInfo.securityLevel == android.security.keystore.KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                keyInfo.securityLevel == android.security.keystore.KeyProperties.SECURITY_LEVEL_STRONGBOX
        }

    val publicKey: ECPublicKey
        get() = privateKeyEntry().certificate.publicKey as ECPublicKey

    /** Uncompressed P-256 SEC1 encoding: `0x04 || X(32) || Y(32)` = 65 bytes. */
    fun publicKeyBytes(): ByteArray {
        val w = publicKey.w
        return byteArrayOf(0x04) + w.affineX.toUnsignedFixed(32) + w.affineY.toUnsignedFixed(32)
    }

    /**
     * Performs ECDH with [vehiclePublicKey] and returns a [Session] whose AES-GCM key K is
     * `SHA1(X_shared, 32 bytes big-endian)[:16]`, matching the Tesla reference implementation.
     */
    fun deriveSession(vehiclePublicKey: ECPublicKey): Session {
        val ka = KeyAgreement.getInstance("ECDH", KEYSTORE)
        ka.init(privateKeyEntry().privateKey)
        ka.doPhase(vehiclePublicKey, true)
        val sharedX = ka.generateSecret()
        // generateSecret() may return a shorter array if the X coordinate had leading zeros.
        val sharedX32 = if (sharedX.size == 32) sharedX else ByteArray(32 - sharedX.size) + sharedX
        return Session.fromSharedSecret(sharedX32)
    }

    private fun privateKeyEntry(): KeyStore.PrivateKeyEntry =
        keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_ALIAS = "tkey/owner-v1"

        fun loadOrCreate(alias: String = DEFAULT_ALIAS): Identity {
            val ks = KeyStore.getInstance(KEYSTORE).also { it.load(null) }
            if (!ks.containsAlias(alias)) {
                val spec = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_AGREE_KEY,
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_NONE)
                    .setUserAuthenticationRequired(false)
                    .build()
                val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
                kpg.initialize(spec)
                kpg.generateKeyPair()
            }
            return Identity(ks, alias)
        }

        private fun BigInteger.toUnsignedFixed(byteLength: Int): ByteArray {
            val raw = toByteArray()
            return when {
                raw.size == byteLength -> raw
                raw.size == byteLength + 1 && raw[0] == 0.toByte() -> raw.copyOfRange(1, raw.size)
                raw.size < byteLength -> ByteArray(byteLength - raw.size) + raw
                else -> error("BigInteger too large for $byteLength bytes (was ${raw.size})")
            }
        }
    }
}
