# EcoGuia_Devin

App Android (Kotlin + Jetpack Compose) que guía a una persona no vidente por rutas definidas
en Supabase, con un dispositivo ESP32 (GPS NEO-6M, MPU9250 y buzzer) conectado por
Bluetooth Classic, y que permite al encargado ver su posición y ruta activa.

## Configuración

Las claves **no** van en el código. Crear `local.properties` (está en `.gitignore`):

```properties
sdk.dir=/ruta/al/android-sdk
SUPABASE_URL=https://<proyecto>.supabase.co
SUPABASE_PUBLISHABLE_KEY=<clave publicable>
MAPBOX_ACCESS_TOKEN=<token público de Mapbox>
```

Compilar y probar:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

En Supabase ejecutar `supabase/seguimiento.sql` (RLS, función del encargado, Realtime y la
tabla `posiciones_usuario` con `actualizado_utc`; renombra `fecha` si existía).

## Módulo de guía

| Pieza | Archivo |
|---|---|
| Constantes (umbrales, tiempos, nombre del ESP32, UUID SPP) | `guia/ConfigGuia.kt` |
| Distancia Haversine y rumbo | `guia/Haversine.kt` |
| Indicación L/R/F (umbral ±20°) | `guia/DireccionRelativa.kt` |
| Tiempo total/restante (velocidad configurable, 1.2 m/s por defecto) | `guia/EstimadorTiempo.kt` |
| Avance por nodos, obstáculos, estado de guía | `guia/MotorGuia.kt` |
| Elección de fuente de posición/rumbo | `guia/SelectorFuente.kt` |
| Simulación de recorrido | `guia/SimuladorRuta.kt` |
| Protocolo y conexión con el ESP32 | `bluetooth/MensajesEsp32.kt`, `bluetooth/ConexionEsp32.kt` |
| GPS y brújula del teléfono | `sensores/SensoresTelefono.kt` |
| Servicio en primer plano (guía + rastreo cada 10 s) | `service/ServicioGuia.kt`, `service/SesionGuia.kt` |
| Pantalla de la ruta en curso | `screen/PanelGuiaRuta.kt`, `screen/RutaEnCursoScreen.kt` |

### Fuentes de posición y rumbo

1. ESP32 conectado y `P` con `fix=1` reciente (≤ 3 s): posición del NEO-6M.
2. ESP32 conectado y `Y` reciente (≤ 1 s): rumbo del MPU9250.
3. Si no: GPS (`FusedLocationProviderClient`) y brújula (rotation vector, o acelerómetro +
   magnetómetro) del teléfono.

El cambio de fuente no reinicia la ruta ni el progreso. El rastreo para el encargado usa siempre
el GPS del teléfono (el del ESP32 solo como respaldo).

### Obstáculos

El esquema no tiene un campo booleano de obstáculo. Provisionalmente un nodo es obstáculo si
su `nodos.tipo` contiene "obstaculo" (sin importar mayúsculas ni tildes, p. ej. `Obstáculo`).
Al quedar a menos de 5 m se pasa al siguiente. Cambio de esquema propuesto (no aplicado):

```sql
alter table public.nodos add column es_obstaculo boolean not null default false;
```

## Bluetooth Classic (ESP32)

- RFCOMM con UUID SPP `00001101-0000-1000-8000-00805F9B34FB`; no se usa BLE.
- El ESP32 debe estar **emparejado desde Ajustes de Android** con el nombre `EcoGuia_ESP32`
  (`ConfigGuia.NOMBRE_DISPOSITIVO_ESP32`). La app no escanea; si no está emparejado lo indica.
- Reconexión automática cada 3 s. Las líneas inválidas se registran (`adb logcat -s EcoGuiaEsp32`) y se ignoran.

Protocolo (texto, cada mensaje termina en `\n`):

| Sentido | Mensaje | Significado |
|---|---|---|
| ESP32 → app | `Y,rumbo,giro` | Rumbo 0–360° y velocidad de giro °/s del MPU9250 |
| ESP32 → app | `P,latitud,longitud,fix` | Posición del NEO-6M; `fix` = 1 válido, 0 sin fix |
| app → ESP32 | `L` / `R` / `F` | Izquierda / derecha / recto (solo cuando cambia) |
| app → ESP32 | `H` | Latido cada 1 s mientras hay sesión |

**Firmware:** el firmware no está en este repositorio, así que no se pudo verificar si ya
envía `P` ni si reconoce `H`. Requisitos para el firmware: enviar `Y` y `P` como arriba, ignorar
`H` (o usarlo para detectar que la app sigue conectada; respaldo propio tras 3 s sin datos) y
sonar el buzzer (GPIO25) con `L/R/F`.

## Simulación (sin ESP32 ni GPS real)

En la pantalla de ruta en curso, **mantener pulsada la tarjeta "Fuente activa"** para activar o
desactivar la simulación (o poner `ConfigGuia.SIMULACION_ACTIVA_POR_DEFECTO = true`). Se recorre
la ruta a la velocidad configurada con un rumbo que oscila ±35° para ver `L`, `R` y `F`, usando
la misma UI y el mismo motor. La posición simulada también se envía al encargado. La app sigue
pidiendo el permiso de ubicación porque el servicio de rastreo es de tipo `location`.

## Permisos

Ubicación precisa (guía y rastreo), dispositivos cercanos `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN`
en Android 12+ (ESP32) y notificaciones (servicio activo con pantalla apagada). La pantalla de
ruta explica cada uno antes de pedirlos.
