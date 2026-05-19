package com.tkey.ble

data class Beacon(
    val address: String,
    val localName: String,
    val rssi: Int,
)
