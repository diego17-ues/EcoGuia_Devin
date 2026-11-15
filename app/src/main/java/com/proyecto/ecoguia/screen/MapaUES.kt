package com.proyecto.ecoguia.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.Marker
import com.mapbox.maps.extension.compose.style.MapStyle
import com.proyecto.ecoguia.data.model.MapPosition
import com.proyecto.ecoguia.viewmodel.MapPositionViewModel

private const val MAP_STYLE_URI = "mapbox://styles/gs24026/cmwd0qhjp00yc01s14jpbbppe"

private val INITIAL_POSITION = MapPosition(
    latitude = 13.718,
    longitude = -89.203,
    zoom = 14.0
)

@OptIn(MapboxExperimental::class)
@Composable
fun MapaUES(
    modifier: Modifier = Modifier,
    viewModel: MapPositionViewModel = viewModel()
) {
    val savedPosition by viewModel.savedPosition.collectAsState()
    val positionLoaded by viewModel.positionLoaded.collectAsState()
    val markerPosition = savedPosition ?: INITIAL_POSITION

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(markerPosition.longitude, markerPosition.latitude))
            zoom(markerPosition.zoom)
        }
    }

    LaunchedEffect(positionLoaded) {
        if (positionLoaded) {
            savedPosition?.let { position ->
                mapViewportState.setCameraOptions {
                    center(Point.fromLngLat(position.longitude, position.latitude))
                    zoom(position.zoom)
                }
            }
        }
    }

    MapboxMap(
        modifier = modifier,
        mapViewportState = mapViewportState,
        onMapClickListener = { point ->
            viewModel.updatePosition(
                MapPosition(
                    latitude = point.latitude(),
                    longitude = point.longitude(),
                    zoom = mapViewportState.cameraState?.zoom ?: markerPosition.zoom
                )
            )
            true
        },
        style = {
            MapStyle(style = MAP_STYLE_URI)
        }
    ) {
        Marker(
            point = Point.fromLngLat(markerPosition.longitude, markerPosition.latitude),
            color = Color(0xFFE53935)
        )
    }
}
