package com.tkey.ble

import java.security.MessageDigest

object VinHash {
    /**
     * BLE advertising local name a Tesla vehicle publishes for the given VIN.
     *
     * Format: `S` + hex(SHA1(VIN)[:8]) + `C` — 18 ASCII characters.
     * Matches `fmt.Sprintf("S%02xC", digest[:8])` in
     * teslamotors/vehicle-command/pkg/connector/ble/ble.go.
     */
    fun localName(vin: String): String {
        val normalized = vin.trim().uppercase()
        val digest = MessageDigest.getInstance("SHA-1").digest(normalized.toByteArray(Charsets.US_ASCII))
        val hex = buildString(16) { for (i in 0 until 8) append("%02x".format(digest[i])) }
        return "S${hex}C"
    }
}
