# EcoGuia_Devin

App Android (Kotlin + Jetpack Compose) que guía a una persona no vidente por rutas definidas
en Supabase, con un dispositivo ESP32 (GPS NEO-7M, MPU9250 y buzzer) conectado por
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

## Módulo de ruta y dispositivo

Reparto de tareas:

- **Dispositivo EcoGuía (ESP32):** guía a la persona con el buzzer. Envía su GPS (NEO-7M) y el
  rumbo del MPU9250.
- **App:** no da indicaciones a la persona. Muestra la ruta en el mapa y el progreso, valida con
  el MPU si la persona sigue la ruta y le responde al dispositivo con `L`/`R`/`A`, `N` al alcanzar
  un nodo y `F` al llegar. Envía al encargado la ubicación del **GPS del teléfono** cada 10 s.
- **Sin el dispositivo conectado** solo se marca la posición con el GPS del teléfono: sin MPU no
  hay validación del rumbo ni corrección, y la pantalla lo indica ("Sin garantía de rumbo").

| Pieza | Archivo |
|---|---|
| Constantes (umbrales, tiempos, nombre del ESP32, UUID SPP) | `guia/ConfigGuia.kt` |
| Distancia Haversine y rumbo | `guia/Haversine.kt` |
| Letras L/R/A y avisos N/F | `guia/DireccionRelativa.kt` |
| Validación del rumbo del MPU contra la ruta | `guia/ValidadorRumbo.kt` |
| Tiempo total/restante (velocidad configurable, 1.2 m/s por defecto) | `guia/EstimadorTiempo.kt` |
| Avance por nodos, obstáculos, progreso | `guia/MotorGuia.kt` |
| Elección de fuente de posición/rumbo | `guia/SelectorFuente.kt` |
| Simulación de recorrido | `guia/SimuladorRuta.kt` |
| Protocolo, conexión y emparejamiento del ESP32 | `bluetooth/MensajesEsp32.kt`, `bluetooth/ConexionEsp32.kt`, `bluetooth/EmparejamientoEsp32.kt` |
| GPS del teléfono | `sensores/SensoresTelefono.kt` |
| Servicio en primer plano (progreso + rastreo cada 10 s) | `service/ServicioGuia.kt`, `service/SesionGuia.kt` |
| Pantalla de la ruta en curso | `screen/PanelGuiaRuta.kt`, `screen/RutaEnCursoScreen.kt`, `screen/MapaRutaComposable.kt` |

### Validación del nodo de inicio (`ValidadorInicio`)

EcoGuía no lleva a la persona hasta el comienzo de la ruta. Al pulsar **"Confirmar ruta"** la app
abre una conexión temporal con el ESP32 (hasta 6 s para conectar y 5 s para recibir un `P` con fix)
y usa el GPS del NEO-7M si ya tiene satélites. Si el dispositivo no está conectado, está desactivado
o aún busca satélites, usa una lectura nueva del GPS del teléfono (máx. 15 s, tomada en paralelo).
Solo se crea el seguimiento si la persona está a `ConfigGuia.RADIO_NODO_INICIO_METROS` (15 m) o
menos del primer nodo. Si no, se muestra
"No se puede iniciar el recorrido, ubicate en el nodo de inicio para comenzar" junto con la
distancia al nodo. Reanudar una ruta ya iniciada no vuelve a pedir esta validación.

Si la validación pasa, la voz anuncia "Has iniciado la ruta <nombre>" y la guía (`ServicioGuia`)
arranca en ese momento, sin esperar a abrir "Ruta en curso". La conexión temporal se cierra y la
guía vuelve a conectar con el ESP32. Como el nodo de inicio ya se validó, la guía apunta desde el
principio al segundo nodo: el rumbo del MPU se valida sobre el primer tramo desde el primer paso
(si la persona mira en sentido contrario, se envía `L`/`R` de inmediato).

### Fuentes de posición y rumbo

1. **Posición de la ruta:** si el ESP32 está conectado y envió un `P` válido en los últimos 3 s,
   se usa el NEO-7M; si no, el GPS del teléfono (`FusedLocationProviderClient`).
2. **Rumbo:** solo el MPU9250 (`Y` recibido en el último segundo). Como `rumboMagnetico()` es
   magnético, se corrige con la declinación local (`ConfigGuia.RUMBO_ESP32_ES_MAGNETICO`). Sin
   rumbo del dispositivo no se calcula ninguna respuesta: solo se muestra el progreso.
3. **Ubicación del encargado:** siempre el GPS del teléfono, nunca el del ESP32. Si
   "Compartir ubicación" ya está activo, el servicio de la ruta no la envía de nuevo.

El cambio de fuente no reinicia la ruta ni el progreso.

### Validación del rumbo (`ValidadorRumbo`)

No es un modelo entrenado (no hay datos de recorridos para entrenarlo), sino un filtro de reglas
que evita pitidos por ruido del MPU o del GPS. Cada 250 ms:

1. Toma el tramo actual (último nodo alcanzado → nodo objetivo) y apunta a un punto 8 m por
   delante sobre ese tramo. Así también corrige si la persona se apartó de la línea aunque mire
   hacia el nodo.
2. Anticipa el rumbo con el giro del MPU (`giro` × 0.5 s, máx. ±45°): si ya está girando hacia la
   ruta, no se le corrige.
3. Compara con el rumbo hacia ese punto: más de 20° a un lado → `L` o `R`; vuelve a `A` cuando
   baja de 12° (histéresis).
4. Un cambio de letra debe repetirse dos cálculos seguidos (0.5 s), salvo que vaya a más de 90°
   de la ruta.

Al alcanzar un nodo se envía `N` (o `F` si es el destino) y la validación empieza de cero con el
tramo siguiente.

### Obstáculos

El esquema no tiene un campo booleano de obstáculo. Provisionalmente un nodo es obstáculo si
su `nodos.tipo` contiene "obstaculo" (sin importar mayúsculas ni tildes, p. ej. `Obstáculo`).
Al quedar a menos de 5 m se pasa al siguiente. Cambio de esquema propuesto (no aplicado):

```sql
alter table public.nodos add column es_obstaculo boolean not null default false;
```

## Bluetooth Classic (ESP32)

- RFCOMM con UUID SPP `00001101-0000-1000-8000-00805F9B34FB`; no se usa BLE.
- Nombre esperado: `EcoGuia_ESP32` (`ConfigGuia.NOMBRE_DISPOSITIVO_ESP32`).
- La tarjeta **"Dispositivo EcoGuía"** de la ruta en curso tiene un interruptor para usar o no el
  dispositivo. Además muestra un botón **"Activar Bluetooth"** si está apagado y
  **"Emparejar dispositivo"** si no está emparejado. Ese botón usa `CompanionDeviceManager`:
  Android busca el ESP32, muestra su propio diálogo y la app crea el vínculo. Si el teléfono no
  lo permite, se abre Ajustes > Bluetooth.
- Reconexión automática cada 3 s. Las líneas inválidas se registran
  (`adb logcat -s EcoGuiaEsp32`) y se ignoran.

Protocolo (texto, cada mensaje termina en `\n`):

| Sentido | Mensaje | Significado |
|---|---|---|
| ESP32 → app | `Y,rumbo,giro` | `rumboMagnetico()` y `mpu.getGyroZ()`, 10 Hz en `MODO_APP` |
| ESP32 → app | `P,latitud,longitud[,fix]` | Posición del NEO-7M; `fix` = 1 válido, 0 sin fix. Sin `fix` se toma como válida; `0,0` se toma como sin fix; los campos extra se ignoran |
| app → ESP32 | `L` / `R` / `A` | Gira a la izquierda / gira a la derecha / vas bien, sigue adelante. Se envía al cambiar y se repite cada 1 s como latido |
| app → ESP32 | `N` | Nodo alcanzado (una vez) |
| app → ESP32 | `F` | Destino final (una vez; si no se pudo enviar, al reconectar) |
| app → ESP32 | `H` | Latido opcional cada 1 s cuando no hay letra de estado (sin MPU o ruta terminada) |

Tras `N` o `F` la app deja de escribir 1.2 s para que el buzzer termine el aviso (menos que los
3 s tras los que el firmware da la app por perdida) y luego reenvía la letra de estado.

El ESP32 no envía `P` hasta encontrar satélites. Mientras tanto la app usa el GPS del teléfono para
la posición y el rumbo del MPU para validar, y muestra "El dispositivo está buscando satélites".

**Firmware:** según el fragmento de `MODO_APP` compartido, el ESP32 envía `Y` a 10 Hz, pita
con `L`/`R` y, tras 3 s sin mensajes de la app, avisa y vuelve a su modo GPS propio. Repetir la
letra vigente como latido mantiene ese enlace activo y no cambia el estado del buzzer, aunque
el firmware tome cualquier carácter recibido como `estadoGuia`. **Falta confirmar en el firmware
completo:**

- con qué formato envía `P`;
- los patrones del buzzer para `A`, `N` y `F`, y cómo trata la `H`;
- si vuelve a `MODO_APP` cuando la app reconecta.

## Simulación (sin ESP32 ni GPS real)

Para llegar a la ruta en curso hay que pasar la validación del nodo de inicio. Después, en ella, activar el interruptor **"Modo simulación"** (o poner
`ConfigGuia.SIMULACION_ACTIVA_POR_DEFECTO = true`). Se recorre la ruta a la velocidad configurada,
con un rumbo que oscila ±35°. Si el dispositivo está conectado, recibe `L`/`R`/`A`, `N` y `F` y
se puede oír el buzzer; la tarjeta del dispositivo muestra la última letra enviada. En simulación se
comparte con el encargado la posición simulada.

## Permisos

Ubicación precisa (progreso y rastreo), dispositivos cercanos `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN`
en Android 12+ (ESP32) y notificaciones (servicio activo con pantalla apagada). La pantalla de
ruta explica cada uno antes de pedirlos.
