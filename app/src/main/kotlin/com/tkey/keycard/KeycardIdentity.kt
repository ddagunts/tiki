package com.tkey.keycard

import com.tkey.crypto.Identity

/**
 * The persistent P-256 keypair this device presents as a Tesla NFC keycard.
 *
 * Distinct from the BLE-side [Identity] (alias `tkey/owner-v1`) so that enrolling
 * the phone-as-keycard creates a *separate* whitelist entry on the car
 * (form factor `KEY_FORM_FACTOR_NFC_CARD`). Revoking one does not revoke the other.
 */
object KeycardIdentity {
    private const val ALIAS = "tkey/keycard-v1"

    fun load(): Identity = Identity.loadOrCreate(ALIAS)
}
