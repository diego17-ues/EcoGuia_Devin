package com.proyecto.ecoguia.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.proyecto.ecoguia.data.model.MapPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mapPositionDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "map_position")

class MapPositionRepository(context: Context) {
    private val dataStore = context.applicationContext.mapPositionDataStore

    val savedPosition: Flow<MapPosition?> = dataStore.data.map { preferences ->
        val latitude = preferences[Keys.latitude] ?: return@map null
        val longitude = preferences[Keys.longitude] ?: return@map null
        val zoom = preferences[Keys.zoom] ?: return@map null

        MapPosition(
            latitude = latitude,
            longitude = longitude,
            zoom = zoom
        )
    }

    suspend fun savePosition(position: MapPosition) {
        dataStore.edit { preferences ->
            preferences[Keys.latitude] = position.latitude
            preferences[Keys.longitude] = position.longitude
            preferences[Keys.zoom] = position.zoom
        }
    }

    private object Keys {
        val latitude = doublePreferencesKey("map_latitude")
        val longitude = doublePreferencesKey("map_longitude")
        val zoom = doublePreferencesKey("map_zoom")
    }
}
