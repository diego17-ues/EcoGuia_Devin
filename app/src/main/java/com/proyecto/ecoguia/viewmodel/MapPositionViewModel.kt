package com.proyecto.ecoguia.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.proyecto.ecoguia.data.model.MapPosition
import com.proyecto.ecoguia.data.repository.MapPositionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapPositionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MapPositionRepository(application)

    private val _savedPosition = MutableStateFlow<MapPosition?>(null)
    val savedPosition = _savedPosition.asStateFlow()

    private val _positionLoaded = MutableStateFlow(false)
    val positionLoaded = _positionLoaded.asStateFlow()

    init {
        viewModelScope.launch {
            repository.savedPosition.collect { position ->
                _savedPosition.value = position
                _positionLoaded.value = true
            }
        }
    }

    fun updatePosition(position: MapPosition) {
        viewModelScope.launch {
            repository.savePosition(position)
        }
    }
}
