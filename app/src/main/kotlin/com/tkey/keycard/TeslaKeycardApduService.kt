package com.tkey.keycard

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.tkey.crypto.Identity
import com.tkey.crypto.Keycard

private const val TAG = "TKey.Keycard"

/**
 * Tesla keycard ("TKC") emulator over Android Host Card Emulation.
 *
 * The vehicle's NFC reader speaks ISO 7816 APDUs against AID `teslaLogic`
 * (`74 65 73 6C 61 4C 6F 67 69 63`). The hot path is three commands:
 *
 *   1. `SELECT teslaLogic` → `9000`
 *   2. `80 04 00 00` (Get Public Key, KeyID 0) → our SEC1 uncompressed pubkey + `9000`
 *   3. `80 11 P1 00 51 <vehiclePub(65) ‖ challenge(16)>` (Authenticate) →
 *       `AES-ECB(K, challenge)` + `9000`, where `K = SHA1(ECDH(cardPriv, vehiclePub).X)[:16]`
 *
 * Plus two informational queries the reader sometimes issues:
 *
 *   - `80 14 00 00 00` (Get Form Factor) → `00 01 9000` (TKC = plain keycard)
 *   - `80 06 …` (Get Certificate) → `6A82` (file not found; vehicle does not require it today)
 *
 * Protocol reference: darconeous's reverse-engineering gist; independent confirmation
 * in IOActive's 2022 relay-attack paper. The cryptographic primitive matches the BLE
 * session derivation already implemented in `core/crypto/Session.fromSharedSecret`, so
 * the same Android Keystore P-256 path used for BLE is reused here verbatim.
 *
 * Manifest must register this service under `android.nfc.cardemulation.action.HOST_APDU_SERVICE`
 * with an `aid_list.xml` that declares the `teslaLogic` AID under `CATEGORY_OTHER`,
 * and `requireDeviceUnlock=false` / `requireDeviceScreenOn=false` to support
 * lock-screen taps the way a physical card does.
 */
class TeslaKeycardApduService : HostApduService() {

    private val identity: Identity by lazy { KeycardIdentity.load() }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val apdu = commandApdu ?: return SW_WRONG_LENGTH
        if (apdu.size < 4) return SW_WRONG_LENGTH

        val cla = apdu[0].toInt() and 0xFF
        val ins = apdu[1].toInt() and 0xFF

        return try {
            when {
                cla == 0x00 && ins == 0xA4 -> handleSelect(apdu)
                cla == 0x80 && ins == 0x04 -> handleGetPublicKey(apdu)
                cla == 0x80 && ins == 0x06 -> handleGetCertificate(apdu)
                cla == 0x80 && ins == 0x11 -> handleAuthenticate(apdu)
                cla == 0x80 && ins == 0x14 -> handleGetFormFactor(apdu)
                else -> {
                    Log.i(TAG, "unsupported APDU CLA=${"%02X".format(cla)} INS=${"%02X".format(ins)}")
                    SW_INS_NOT_SUPPORTED
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "APDU handler crashed: ${t.message}", t)
            SW_UNKNOWN
        }
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "deactivated reason=$reason")
    }

    // --- APDU handlers --------------------------------------------------------

    private fun handleSelect(apdu: ByteArray): ByteArray {
        // 00 A4 04 00 Lc <AID>
        if (apdu.size < 5) return SW_WRONG_LENGTH
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return SW_WRONG_LENGTH
        val aid = apdu.copyOfRange(5, 5 + lc)
        return if (aid.contentEquals(AID_TESLA_LOGIC)) {
            Log.i(TAG, "SELECT teslaLogic OK")
            SW_OK
        } else {
            Log.i(TAG, "SELECT unknown AID ${aid.toHex()}")
            SW_FILE_NOT_FOUND
        }
    }

    private fun handleGetPublicKey(apdu: ByteArray): ByteArray {
        // 80 04 P1 00  — P1 is the KeyID; we only have KeyID 0.
        val p1 = apdu[2].toInt() and 0xFF
        if (p1 != 0) {
            Log.i(TAG, "GetPublicKey unknown KeyID=$p1")
            return SW_FILE_NOT_FOUND
        }
        val pk = identity.publicKeyBytes()
        Log.i(TAG, "GetPublicKey returning ${pk.size} bytes")
        return pk + SW_OK
    }

    private fun handleAuthenticate(apdu: ByteArray): ByteArray {
        // 80 11 P1 00 51 <vehiclePub(65) ‖ challenge(16)>
        if (apdu.size < 5) return SW_WRONG_LENGTH
        val lc = apdu[4].toInt() and 0xFF
        if (lc != Keycard.AUTHENTICATE_PAYLOAD_LEN || apdu.size < 5 + lc) {
            Log.i(TAG, "Authenticate bad Lc=$lc apdu.size=${apdu.size}")
            return SW_WRONG_LENGTH
        }
        val payload = apdu.copyOfRange(5, 5 + lc)
        val resp = Keycard.respondToChallenge(identity, payload)
        Log.i(TAG, "Authenticate returning ${resp.size} bytes")
        return resp + SW_OK
    }

    private fun handleGetFormFactor(@Suppress("UNUSED_PARAMETER") apdu: ByteArray): ByteArray {
        Log.i(TAG, "GetFormFactor → TKC (0x0001)")
        return FORM_FACTOR_TKC + SW_OK
    }

    private fun handleGetCertificate(@Suppress("UNUSED_PARAMETER") apdu: ByteArray): ByteArray {
        // We don't carry a Tesla-signed attestation cert. Current vehicles do not
        // request one during pairing or auth; if Tesla turns this on in an OTA, the
        // feature stops working — see PHASE 1 notes.
        Log.i(TAG, "GetCertificate → 6A82 (not present)")
        return SW_FILE_NOT_FOUND
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    companion object {
        /** ASCII "teslaLogic" — the AID modern vehicles SELECT against. */
        val AID_TESLA_LOGIC: ByteArray = byteArrayOf(
            0x74, 0x65, 0x73, 0x6C, 0x61, 0x4C, 0x6F, 0x67, 0x69, 0x63,
        )

        /** Form factor 0x0001 = plain Tesla Key Card (TKC). */
        private val FORM_FACTOR_TKC = byteArrayOf(0x00, 0x01)

        // ISO 7816 status words.
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00)
        private val SW_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00)
        private val SW_UNKNOWN = byteArrayOf(0x6F.toByte(), 0x00)
    }
}
