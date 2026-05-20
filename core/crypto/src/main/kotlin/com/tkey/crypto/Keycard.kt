package com.tkey.crypto

/**
 * Tesla NFC keycard authentication primitive — the same crypto a physical TKC
 * keycard runs inside its applet.
 *
 * The vehicle sends `vehiclePubKey(65) || challenge(16)` in APDU INS 0x11. The card
 * (or our HCE service, here) computes:
 *
 *   shared = ECDH(cardPriv, vehiclePub).X       // 32 bytes, P-256
 *   K      = SHA1(shared)[:16]                  // matches BLE session derivation
 *   resp   = AES-128-ECB-Encrypt(K, challenge)  // 16 bytes
 *
 * The vehicle does the same derivation with its own ephemeral private key, decrypts
 * `resp`, and accepts the card if the plaintext matches the challenge it sent.
 *
 * We deliberately reuse [Identity.deriveSession] and [Session.encryptBlock] so the
 * crypto path is shared with the BLE session and lives in a single audited place.
 *
 * This is the "TKC" (plain keycard, form-factor 0x0001) variant — no random-prefix
 * rewrite. The "TPK" smartphone-card variant overwrites bytes [0..3] of the
 * challenge before encrypting; we don't pretend to be a TPK card.
 */
object Keycard {

    /** SEC1 uncompressed P-256 public key sent by the vehicle in INS 0x11. */
    private const val PUBKEY_LEN = 65

    /** The vehicle's challenge: a single AES block. */
    private const val CHALLENGE_LEN = 16

    /** Total INS 0x11 payload: pubkey followed by the challenge. */
    const val AUTHENTICATE_PAYLOAD_LEN = PUBKEY_LEN + CHALLENGE_LEN

    /**
     * Compute the keycard's INS 0x11 response.
     *
     * @param identity the card's persistent P-256 keypair (Android Keystore-backed).
     * @param payload  exactly [AUTHENTICATE_PAYLOAD_LEN] bytes: `vehiclePub(65) || challenge(16)`.
     * @return         16-byte AES-ECB ciphertext to return as the APDU response body.
     */
    fun respondToChallenge(identity: Identity, payload: ByteArray): ByteArray {
        require(payload.size == AUTHENTICATE_PAYLOAD_LEN) {
            "expected $AUTHENTICATE_PAYLOAD_LEN bytes (pubkey + challenge); got ${payload.size}"
        }
        val vehiclePubBytes = payload.copyOfRange(0, PUBKEY_LEN)
        val challenge = payload.copyOfRange(PUBKEY_LEN, AUTHENTICATE_PAYLOAD_LEN)
        val vehiclePub = Session.decodePublicKey(vehiclePubBytes)
        val session = identity.deriveSession(vehiclePub)
        return session.encryptBlock(challenge)
    }
}
