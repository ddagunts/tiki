package com.tkey.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedCar(val name: String, val vin: String)

class CarStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun list(): List<SavedCar> {
        val raw = prefs.getString(KEY_CARS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(SavedCar(name = obj.getString("name"), vin = obj.getString("vin")))
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Adds or updates by VIN. Returns the new list, or null if at capacity and VIN is new. */
    fun add(car: SavedCar): List<SavedCar>? {
        val current = list().toMutableList()
        val existing = current.indexOfFirst { it.vin == car.vin }
        if (existing >= 0) {
            current[existing] = car
        } else {
            if (current.size >= MAX_CARS) return null
            current.add(car)
        }
        save(current)
        return current
    }

    fun remove(vin: String): List<SavedCar> {
        val current = list().filterNot { it.vin == vin }
        save(current)
        if (lastVin() == vin) setLastVin(null)
        return current
    }

    fun lastVin(): String? = prefs.getString(KEY_LAST_VIN, null)

    fun setLastVin(vin: String?) {
        val editor = prefs.edit()
        if (vin == null) editor.remove(KEY_LAST_VIN) else editor.putString(KEY_LAST_VIN, vin)
        editor.apply()
    }

    private fun save(cars: List<SavedCar>) {
        val arr = JSONArray()
        for (c in cars) {
            arr.put(JSONObject().put("name", c.name).put("vin", c.vin))
        }
        prefs.edit().putString(KEY_CARS, arr.toString()).apply()
    }

    companion object {
        const val MAX_CARS = 10
        private const val PREFS_NAME = "tkey_cars"
        private const val KEY_CARS = "cars"
        private const val KEY_LAST_VIN = "last_vin"
    }
}
