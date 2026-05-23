package com.tkey.ui

import android.content.Context
import com.tkey.ui.proximity.ProximityConfig
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

    /**
     * Adds or updates by VIN. Returns the new list, or null if at capacity and the VIN is new.
     * Throws [IllegalArgumentException] if the VIN isn't 17 uppercase alphanumerics — the
     * AddCar UI already filters input to that shape, but enforce it here so programmatic
     * callers can't slip a bad VIN past us into [VinHash] / `localName` matching.
     */
    fun add(car: SavedCar): List<SavedCar>? {
        val normalizedVin = car.vin.uppercase()
        require(VIN_REGEX.matches(normalizedVin)) {
            "VIN must be 17 uppercase alphanumeric characters; got '${car.vin}'"
        }
        val normalizedName = car.name.trim()
        require(normalizedName.isNotEmpty()) { "Name must not be empty" }
        val normalized = SavedCar(name = normalizedName, vin = normalizedVin)
        val current = list().toMutableList()
        val existing = current.indexOfFirst { it.vin == normalizedVin }
        if (existing >= 0) {
            current[existing] = normalized
        } else {
            if (current.size >= MAX_CARS) return null
            current.add(normalized)
        }
        save(current)
        return current
    }

    fun remove(vin: String): List<SavedCar> {
        val current = list().filterNot { it.vin == vin }
        save(current)
        removeProximity(vin)
        setPaired(vin, false)
        if (lastVin() == vin) setLastVin(null)
        if (favoriteProximityVin() == vin) setFavoriteProximityVin(null)
        return current
    }

    /** True once we've completed first-time enrollment (keycard tap) for this VIN. */
    fun isPaired(vin: String): Boolean =
        prefs.getStringSet(KEY_PAIRED_VINS, emptySet())?.contains(vin) == true

    fun setPaired(vin: String, paired: Boolean) {
        val current = prefs.getStringSet(KEY_PAIRED_VINS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val changed = if (paired) current.add(vin) else current.remove(vin)
        if (changed) prefs.edit().putStringSet(KEY_PAIRED_VINS, current).apply()
    }

    fun lastVin(): String? = prefs.getString(KEY_LAST_VIN, null)

    fun setLastVin(vin: String?) {
        val editor = prefs.edit()
        if (vin == null) editor.remove(KEY_LAST_VIN) else editor.putString(KEY_LAST_VIN, vin)
        editor.apply()
    }

    /**
     * If true, the active-vehicle screen omits the "Vehicles" back pill. Only takes effect when
     * there's exactly one saved car — otherwise the back pill is the only way to switch cars.
     */
    fun hideBackToVehicles(): Boolean = prefs.getBoolean(KEY_HIDE_BACK_PILL, false)

    fun setHideBackToVehicles(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_BACK_PILL, hide).apply()
    }

    fun getProximity(vin: String): ProximityConfig {
        val raw = prefs.getString(proxKey(vin), null) ?: return ProximityConfig()
        return runCatching { ProximityConfig.fromJson(JSONObject(raw)) }.getOrDefault(ProximityConfig())
    }

    fun setProximity(vin: String, cfg: ProximityConfig) {
        prefs.edit().putString(proxKey(vin), cfg.toJson().toString()).apply()
    }

    /** Returns (vin -> config) for every car that has proximity enabled. */
    fun enabledProximity(): Map<String, ProximityConfig> = buildMap {
        for (car in list()) {
            val cfg = getProximity(car.vin)
            if (cfg.enabled) put(car.vin, cfg)
        }
    }

    /**
     * VIN of the car whose live proximity state should be surfaced in the foreground-service
     * notification. Single string by design — only one car at a time gets featured. Null
     * means "no explicit favorite"; callers should fall back to the first enabled VIN.
     */
    fun favoriteProximityVin(): String? = prefs.getString(KEY_FAVORITE_PROX_VIN, null)

    fun setFavoriteProximityVin(vin: String?) {
        val editor = prefs.edit()
        if (vin == null) editor.remove(KEY_FAVORITE_PROX_VIN) else editor.putString(KEY_FAVORITE_PROX_VIN, vin)
        editor.apply()
    }

    private fun save(cars: List<SavedCar>) {
        val arr = JSONArray()
        for (c in cars) {
            arr.put(JSONObject().put("name", c.name).put("vin", c.vin))
        }
        prefs.edit().putString(KEY_CARS, arr.toString()).apply()
    }

    private fun removeProximity(vin: String) {
        prefs.edit().remove(proxKey(vin)).apply()
    }

    companion object {
        const val MAX_CARS = 10
        private const val PREFS_NAME = "tkey_cars"
        private const val KEY_CARS = "cars"
        private const val KEY_LAST_VIN = "last_vin"
        private const val KEY_PAIRED_VINS = "paired_vins"
        private const val KEY_HIDE_BACK_PILL = "hide_back_pill"
        private const val KEY_FAVORITE_PROX_VIN = "favorite_prox_vin"
        private val VIN_REGEX = Regex("^[A-Z0-9]{17}$")

        private fun proxKey(vin: String) = "prox_$vin"
    }
}
