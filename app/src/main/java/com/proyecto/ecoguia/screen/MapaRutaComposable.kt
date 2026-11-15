package com.proyecto.ecoguia.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.ColorValue
import com.mapbox.maps.extension.compose.style.DoubleValue
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.compose.style.layers.generated.LineLayer
import com.mapbox.maps.extension.compose.style.sources.GeoJSONData
import com.mapbox.maps.extension.compose.style.sources.generated.rememberGeoJsonSourceState
import com.proyecto.ecoguia.data.repository.RutaDisponible

private const val CAMPUS_UES_LATITUD = 13.718
private const val CAMPUS_UES_LONGITUD = -89.203

@OptIn(MapboxExperimental::class)
@Composable
fun MapaRutaComposable(
    ruta: RutaDisponible?,
    modifier: Modifier = Modifier,
    descripcion: String = ruta?.nombre?.let { "Vista previa de la ruta $it" }
        ?: "Mapa del campus de la Universidad de El Salvador"
) {
    val puntos = remember(ruta?.id, ruta?.nodos) {
        ruta?.nodos.orEmpty().map { Point.fromLngLat(it.longitud, it.latitud) }
    }
    val source = rememberGeoJsonSourceState(sourceId = "ecoguia-ruta-preview-source")
    val viewport = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(CAMPUS_UES_LONGITUD, CAMPUS_UES_LATITUD))
            zoom(16.0)
        }
    }

    LaunchedEffect(puntos) {
        if (puntos.size >= 2) {
            source.data = GeoJSONData(LineString.fromLngLats(puntos))
            val camera = viewport.cameraForCoordinates(
                coordinates = puntos,
                coordinatesPadding = EdgeInsets(48.0, 48.0, 48.0, 48.0),
                maxZoom = 17.0
            )
            viewport.setCameraOptions(camera)
        }
    }

    MapboxMap(
        modifier = modifier.semantics { contentDescription = descripcion },
        mapViewportState = viewport,
        style = { MapStyle(style = Style.STANDARD) }
    ) {
        if (puntos.size >= 2) {
            LineLayer(
                layerId = "ecoguia-ruta-preview-layer",
                sourceState = source
            ) {
                lineColor = ColorValue(Color(0xFFFF0000))
                lineWidth = DoubleValue(4.0)
            }
        }
    }
}
