package com.akira.app.core.audio

import android.media.AudioDeviceInfo
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OutputKey(val type: Int, val productName: String) {
    val encoded: String get() = "$type:${productName.length}:$productName"
}
object OutputRoutes {
    private val mutableOutput = MutableStateFlow(OutputKey(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, ""))
    val current = mutableOutput.asStateFlow()
    fun refresh(devices: Array<AudioDeviceInfo>, selectedBluetoothName: String? = null, useDefaultRoute: Boolean = false) {
        val priority = buildList {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_HEARING_AID)
            if (android.os.Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            addAll(listOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        }
        val candidates = devices.filter { it.isSink && it.type in priority }
        val bluetoothTypes = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_HEARING_AID)
            if (android.os.Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        val selected = if (selectedBluetoothName != null) candidates.filter { it.type in bluetoothTypes }
            .firstOrNull { it.productName.toString() == selectedBluetoothName }
            ?: candidates.firstOrNull { it.type in bluetoothTypes }
        else candidates.filter { !useDefaultRoute || it.type !in bluetoothTypes }.minByOrNull { priority.indexOf(it.type) }
        mutableOutput.value = if (selected == null) OutputKey(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "")
            else OutputKey(selected.type, selected.productName.toString())
    }
}

data class EqOutputProfile(val id: String, val name: String, val bands: List<Float>, val bass: Float,
    val space: Float, val preamp: Float = 1f)
object OutputProfiles {
    fun decode(json: String): List<EqOutputProfile> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index -> runCatching {
            val item = array.getJSONObject(index)
            val gains = item.getJSONArray("bands")
            if (gains.length() != 5 || item.getString("id").isBlank() || item.getString("name").isBlank()) null else EqOutputProfile(item.getString("id"),item.getString("name"),
                List(5) { gains.getDouble(it).toFloat().coerceIn(-12f,12f) },
                item.getDouble("bass").toFloat().coerceIn(0f,1f),item.getDouble("space").toFloat().coerceIn(0f,1f),
                item.optDouble("preamp",1.0).toFloat().takeIf { it.isFinite() }?.coerceIn(.25f,1f) ?: 1f)
        }.getOrNull() }.distinctBy {it.id}
    }.getOrDefault(emptyList())
    fun encode(profiles: List<EqOutputProfile>): String = JSONArray().also { array ->
        profiles.forEach { profile ->
            val sound=EqualizerConfiguration(profile.bands,profile.bass,profile.space,profile.preamp).normalized()
            array.put(JSONObject().put("id",profile.id).put("name",profile.name)
                .put("bands",JSONArray(sound.bands)).put("bass",sound.bass).put("space",sound.space).put("preamp",sound.preamp))
        }
    }.toString()
    fun assignments(json: String): Map<String,String> = runCatching {
        val value = JSONObject(json)
        value.keys().asSequence().associateWith { value.getString(it) }
    }.getOrDefault(emptyMap())
}
