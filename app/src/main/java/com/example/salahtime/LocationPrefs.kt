package com.example.salahtime

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

val Context.dataStore by preferencesDataStore(name = "settings")

object LocationKeys {
    val LAT = stringPreferencesKey("lat")
    val LON = stringPreferencesKey("lon")
}

suspend fun saveLocation(lat: String, lon: String, context: Context) {
    context.dataStore.edit {
        it[LocationKeys.LAT] = lat
        it[LocationKeys.LON] = lon
    }
}

suspend fun loadLastLocation(context: Context): Pair<String?, String?> {
    val prefs = context.dataStore.data.first()
    return prefs[LocationKeys.LAT] to prefs[LocationKeys.LON]
}