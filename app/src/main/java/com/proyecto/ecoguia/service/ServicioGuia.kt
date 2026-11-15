package com.proyecto.ecoguia.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.GeomagneticField
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.proyecto.ecoguia.MainActivity
import com.proyecto.ecoguia.R
import com.proyecto.ecoguia.bluetooth.ConexionEsp32
import com.proyecto.ecoguia.bluetooth.MensajeEsp32
import com.proyecto.ecoguia.data.repository.CoordenadaCapturada
import com.proyecto.ecoguia.data.repository.NoVidenteRepository
import com.proyecto.ecoguia.data.repository.RutaDisponible
import com.proyecto.ecoguia.data.repository.RutasRepository
import com.proyecto.ecoguia.data.repository.SeguimientoRepository
import com.proyecto.ecoguia.guia.AvisoRuta
import com.proyecto.ecoguia.guia.ConfigGuia
import com.proyecto.ecoguia.guia.Coordenada
import com.proyecto.ecoguia.guia.EntradasGuia
import com.proyecto.ecoguia.guia.EstimadorTiempo
import com.proyecto.ecoguia.guia.FuentePosicion
import com.proyecto.ecoguia.guia.FuenteRumbo
import com.proyecto.ecoguia.guia.Haversine
import com.proyecto.ecoguia.guia.Lectura
import com.proyecto.ecoguia.guia.MotorGuia
import com.proyecto.ecoguia.guia.PosicionEsp32
import com.proyecto.ecoguia.guia.PreferenciasGuia
import com.proyecto.ecoguia.guia.PuntoRuta
import com.proyecto.ecoguia.guia.RutaGuia
import com.proyecto.ecoguia.guia.SelectorFuente
import com.proyecto.ecoguia.guia.SimuladorRuta
import com.proyecto.ecoguia.guia.ValidadorRumbo
import com.proyecto.ecoguia.sensores.SensoresTelefono
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Servicio en primer plano que mantiene la sesión de una ruta en curso:
 * - Calcula el progreso de la ruta (punto siguiente, distancia y tiempo restantes, obstáculo).
 * - Mantiene la conexión con el ESP32: recibe su GPS (NEO-7M) y el rumbo del MPU9250, valida con
 *   [ValidadorRumbo] si sigue la ruta y le responde con L/R/A, más N al alcanzar un nodo y F al
 *   llegar al destino, para que el buzzer guíe. La app no guía a la persona por sí misma.
 * - Sin el dispositivo conectado solo se marca la posición con el GPS del teléfono: sin MPU no
 *   hay validación del rumbo ni letras de corrección.
 * - Envía la posición del GPS del teléfono a posiciones_usuario cada 10 s, al cambiar de ruta y
 *   al terminar.
 * Sigue funcionando con la pantalla apagada mientras la notificación esté visible.
 */
class ServicioGuia : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val seguimientoRepository by lazy { SeguimientoRepository() }

    private lateinit var sensores: SensoresTelefono
    private lateinit var esp32: ConexionEsp32

    // Últimas lecturas de cada fuente; se escriben desde distintos hilos.
    @Volatile private var posicionTelefono: Lectura<Location>? = null
    @Volatile private var posicionEsp32: Lectura<PosicionEsp32>? = null
    @Volatile private var rumboEsp32: Lectura<Double>? = null
    @Volatile private var giroEsp32 = 0.0
    @Volatile private var lecturaSimulada: Lectura<Pair<Coordenada, Double>>? = null

    @Volatile private var motor: MotorGuia? = null
    private val validador = ValidadorRumbo()
    /** Nodo objetivo del cálculo anterior, para detectar cuándo se alcanza uno (aviso N). */
    @Volatile private var indiceObjetivoAnterior = -1
    @Volatile private var destinoAvisado = false
    private var simulador: SimuladorRuta? = null
    private var usuarioId: String? = null
    private var seguimientoId: Long? = null
    private var simulacion = false
    private var dispositivoActivo = true

    private var trabajoCarga: Job? = null
    private var trabajoGuia: Job? = null
    private var trabajoEnvio: Job? = null
    private var trabajoSimulacion: Job? = null
    private var textoNotificacion = ""

    override fun onCreate() {
        super.onCreate()
        crearCanalNotificacion()
        sensores = SensoresTelefono(
            contexto = this,
            alRecibirUbicacion = { posicionTelefono = Lectura(it, SystemClock.elapsedRealtime()) }
        )
        esp32 = ConexionEsp32(this, scope, ::alRecibirEsp32)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DETENER -> {
                detenerSesion()
                return START_NOT_STICKY
            }
            ACTION_SIMULACION -> {
                cambiarSimulacion(intent.getBooleanExtra(EXTRA_SIMULACION, false))
                return modoReinicio()
            }
            ACTION_DISPOSITIVO -> {
                cambiarDispositivo(intent.getBooleanExtra(EXTRA_DISPOSITIVO, true))
                return modoReinicio()
            }
            ACTION_VELOCIDAD -> {
                val velocidad = intent.getDoubleExtra(EXTRA_VELOCIDAD, ConfigGuia.VELOCIDAD_CAMINATA_POR_DEFECTO_MS)
                motor?.estimador = EstimadorTiempo(velocidad)
                SesionGuia.actualizar { it.copy(velocidadMetrosSegundo = velocidad) }
                return modoReinicio()
            }
        }

        // ACTION_INICIAR, o reinicio del sistema tras matar el proceso (intent nulo).
        iniciarPrimerPlano()
        val solicitado = intent?.getLongExtra(EXTRA_SEGUIMIENTO_ID, -1L)?.takeIf { it > 0 }
        if (solicitado != null && solicitado == seguimientoId && motor != null) return START_STICKY
        cargarSesion(solicitado)
        return START_STICKY
    }

    // Android usa el último valor devuelto: con sesión activa debe seguir reiniciándose.
    private fun modoReinicio() = if (motor != null || trabajoCarga?.isActive == true) START_STICKY else START_NOT_STICKY

    private fun cargarSesion(seguimientoSolicitado: Long?) {
        trabajoCarga?.cancel()
        SesionGuia.actualizar { it.copy(cargando = true, error = null) }
        trabajoCarga = scope.launch {
            try {
                val (id, rutaActiva, rutaCompleta) = withContext(Dispatchers.IO) {
                    val id = NoVidenteRepository().obtenerIdAutenticado()
                    val activa = seguimientoRepository.obtenerRutaActivaUsuario()
                        ?: error("No hay una ruta activa.")
                    val completa = RutasRepository().obtenerRutaDisponible(activa.rutaId)
                        ?: error("No se pudo cargar la ruta.")
                    Triple(id, activa, completa)
                }
                if (seguimientoSolicitado != null && seguimientoSolicitado != rutaActiva.seguimientoId) {
                    Log.w(TAG, "El seguimiento $seguimientoSolicitado ya no está activo; se usa ${rutaActiva.seguimientoId}.")
                }
                val rutaCambio = seguimientoId != null && seguimientoId != rutaActiva.seguimientoId
                usuarioId = id
                if (rutaCambio) enviarPosicionAhora()
                seguimientoId = rutaActiva.seguimientoId

                val ruta = rutaCompleta.aRutaGuia()
                if (ruta.puntos.size < 2) error("La ruta no tiene un recorrido ordenado de al menos 2 puntos.")
                val indiceUltimo = ruta.puntos.indexOfFirst { it.id == rutaActiva.ultimoNodoId }
                val velocidad = PreferenciasGuia.velocidad(this@ServicioGuia)
                motor = MotorGuia(
                    ruta = ruta,
                    estimador = EstimadorTiempo(velocidad),
                    // Se retoma en el nodo siguiente al último registrado. Si ese último es el destino
                    // (marcado a 15 m), se sigue apuntando a él para no dar la llegada por hecha.
                    indiceInicial = if (indiceUltimo >= 0) (indiceUltimo + 1).coerceAtMost(ruta.puntos.lastIndex) else 0
                ).also {
                    indiceObjetivoAnterior = it.indiceObjetivo
                    destinoAvisado = false
                    esp32.descartarAvisosPendientes()
                    synchronized(validador) { validador.reiniciar() }
                }
                // Una ruta nueva no hereda el recorrido simulado de la anterior.
                trabajoSimulacion?.cancel()
                lecturaSimulada = null
                simulador = null
                simulacion = PreferenciasGuia.simulacion(this@ServicioGuia)

                val gps = sensores.iniciarGps()
                dispositivoActivo = PreferenciasGuia.dispositivoActivo(this@ServicioGuia)
                if (dispositivoActivo) esp32.iniciar() else esp32.detener()

                SesionGuia.actualizar {
                    it.copy(
                        activa = true,
                        cargando = false,
                        error = null,
                        seguimientoId = rutaActiva.seguimientoId,
                        ruta = ruta,
                        simulacion = simulacion,
                        velocidadMetrosSegundo = velocidad,
                        dispositivoActivo = dispositivoActivo,
                        gpsTelefonoDisponible = gps
                    )
                }
                iniciarBucles()
                if (simulacion) iniciarSimulacion()
                enviarPosicionAhora()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo iniciar la guía", e)
                SesionGuia.actualizar {
                    it.copy(cargando = false, activa = false, error = e.localizedMessage ?: "No se pudo iniciar la guía.")
                }
                retirarServicio()
            }
        }
    }

    private fun iniciarBucles() {
        trabajoGuia?.cancel()
        trabajoGuia = scope.launch(Dispatchers.IO) {
            while (isActive) {
                calcularGuia()
                delay(ConfigGuia.INTERVALO_CALCULO_GUIA_MS)
            }
        }
        trabajoEnvio?.cancel()
        trabajoEnvio = scope.launch {
            while (isActive) {
                delay(ConfigGuia.INTERVALO_ENVIO_POSICION_MS)
                enviarPosicionAhora()
            }
        }
    }

    private fun calcularGuia() {
        val motorActual = motor ?: return
        val ahora = SystemClock.elapsedRealtime()
        val simulada = lecturaSimulada
        val fuentes = SelectorFuente.elegir(
            EntradasGuia(
                simulacion = simulacion,
                esp32Conectado = esp32.estado.value.conectado,
                posicionEsp32 = posicionEsp32,
                rumboEsp32 = rumboEsp32,
                posicionTelefono = posicionTelefono?.let { Lectura(it.valor.aCoordenada(), it.instanteMs) },
                posicionSimulada = simulada?.let { Lectura(it.valor.first, it.instanteMs) },
                rumboSimulado = simulada?.let { Lectura(it.valor.second, it.instanteMs) }
            ),
            ahora
        )
        val guia = synchronized(motorActual) { motorActual.actualizar(fuentes.posicion, fuentes.rumbo) }

        // Avisos de avance: N al alcanzar un nodo intermedio, F una sola vez al llegar al destino.
        val nodoAlcanzado = guia.indiceObjetivo > indiceObjetivoAnterior && indiceObjetivoAnterior >= 0
        indiceObjetivoAnterior = guia.indiceObjetivo
        if (guia.finalizada) {
            if (!destinoAvisado) {
                destinoAvisado = true
                esp32.enviarAviso(AvisoRuta.DESTINO_FINAL)
            }
        } else if (nodoAlcanzado) {
            esp32.enviarAviso(AvisoRuta.NODO_ALCANZADO)
        }

        // Validación del rumbo con el MPU sobre el tramo actual (nodo alcanzado → nodo objetivo).
        val validacion = synchronized(validador) {
            if (nodoAlcanzado) validador.reiniciar()
            validador.evaluar(
                posicion = fuentes.posicion,
                rumbo = fuentes.rumbo,
                giroGradosSegundo = if (fuentes.fuenteRumbo == FuenteRumbo.ESP32) giroEsp32 else 0.0,
                anterior = motorActual.ruta.puntos.getOrNull(guia.indiceObjetivo - 1)?.coordenada,
                siguiente = guia.siguientePunto?.coordenada.takeUnless { guia.finalizada }
            )
        }
        // Letra de estado L/R/A: se escribe al cambiar y se repite como latido (ConexionEsp32).
        // Sin MPU o con la ruta terminada no hay letra y solo queda el latido "H".
        esp32.enviarIndicacion(validacion.indicacion)
        val conectado = esp32.estado.value.conectado

        SesionGuia.actualizar {
            it.copy(
                fuentes = fuentes,
                guia = guia,
                esp32 = esp32.estado.value,
                ultimaLetraEsp32 = esp32.ultimaLetraEnviada.takeIf { conectado },
                validacion = validacion,
                esp32BuscandoSatelites = conectado && !simulacion &&
                        fuentes.fuentePosicion != FuentePosicion.ESP32
            )
        }
        actualizarNotificacion(
            when {
                guia.finalizada -> "Ruta completada."
                guia.siguientePunto != null && guia.distanciaSiguientePuntoMetros != null ->
                    "Hacia ${guia.siguientePunto.nombre}: ${guia.distanciaSiguientePuntoMetros.toInt()} m · " +
                            "quedan ${EstimadorTiempo.formatear(guia.tiempoRestanteSegundos)}"
                else -> "Buscando posición…"
            }
        )
    }

    private fun alRecibirEsp32(mensaje: MensajeEsp32) {
        val ahora = SystemClock.elapsedRealtime()
        when (mensaje) {
            is MensajeEsp32.Rumbo -> {
                giroEsp32 = mensaje.giroGradosSegundo
                rumboEsp32 = Lectura(rumboGeografico(mensaje.rumboGrados), ahora)
            }
            is MensajeEsp32.Posicion -> posicionEsp32 = Lectura(
                PosicionEsp32(Coordenada(mensaje.latitud, mensaje.longitud), mensaje.fixValido),
                ahora
            )
            is MensajeEsp32.Invalido -> Unit
        }
    }

    private fun rumboGeografico(rumboMagnetico: Double): Double {
        if (!ConfigGuia.RUMBO_ESP32_ES_MAGNETICO) return rumboMagnetico
        val referencia = posicionEsp32?.valor?.takeIf { it.fixValido }?.coordenada
            ?: posicionTelefono?.valor?.aCoordenada()
            ?: return rumboMagnetico
        val declinacion = GeomagneticField(
            referencia.latitud.toFloat(),
            referencia.longitud.toFloat(),
            0f,
            System.currentTimeMillis()
        ).declination
        return Haversine.normalizarRumbo(rumboMagnetico + declinacion)
    }

    private fun cambiarSimulacion(activa: Boolean) {
        PreferenciasGuia.guardarSimulacion(this, activa)
        simulacion = activa
        if (activa) iniciarSimulacion() else {
            trabajoSimulacion?.cancel()
            lecturaSimulada = null
        }
        SesionGuia.actualizar { it.copy(simulacion = activa) }
    }

    private fun cambiarDispositivo(activo: Boolean) {
        PreferenciasGuia.guardarDispositivoActivo(this, activo)
        dispositivoActivo = activo
        if (motor != null) {
            if (activo) esp32.iniciar() else esp32.detener()
        }
        SesionGuia.actualizar { it.copy(dispositivoActivo = activo, esp32 = esp32.estado.value) }
    }

    private fun iniciarSimulacion() {
        val motorActual = motor ?: return
        if (trabajoSimulacion?.isActive == true) return
        // Continúa desde la última posición simulada o, si no hay, desde el último punto alcanzado
        // (no desde el GPS real, que puede estar lejos de la ruta).
        val inicio = lecturaSimulada?.valor?.first
        val puntos = motorActual.ruta.puntos.map { it.coordenada }
        val simuladorNuevo = simulador?.takeIf { !it.terminado } ?: SimuladorRuta(
            puntos = if (inicio != null) puntos.drop(motorActual.indiceObjetivo)
            else puntos.drop((motorActual.indiceObjetivo - 1).coerceAtLeast(0)),
            velocidadMetrosSegundo = motorActual.estimador.velocidadMetrosSegundo,
            inicio = inicio
        )
        simulador = simuladorNuevo
        trabajoSimulacion = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val lectura = simuladorNuevo.avanzar()
                lecturaSimulada = Lectura(lectura.posicion to lectura.rumbo, SystemClock.elapsedRealtime())
                delay(ConfigGuia.INTERVALO_SIMULACION_MS)
            }
        }
    }

    /**
     * Rastreo: se envía la posición del GPS del teléfono, independiente del ESP32. En simulación
     * se envía la posición simulada para poder probar la vista del encargado sin caminar.
     */
    private fun enviarPosicionAhora() {
        val id = usuarioId ?: return
        // "Compartir ubicación" ya la envía con el mismo GPS; así no se duplican las escrituras.
        if (!simulacion && EstadoServicioSeguimiento.activo.value) {
            SesionGuia.actualizar { it.copy(compartidaPorOtroServicio = true, errorEnvioPosicion = null) }
            return
        }
        val coordenada = posicionParaRastreo() ?: return
        scope.launch(Dispatchers.IO) {
            try {
                seguimientoRepository.guardarPosicion(id, coordenada)
                SesionGuia.actualizar {
                    it.copy(
                        ultimoEnvioPosicionMs = System.currentTimeMillis(),
                        errorEnvioPosicion = null,
                        compartidaPorOtroServicio = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo enviar la posición", e)
                SesionGuia.actualizar { it.copy(errorEnvioPosicion = e.localizedMessage ?: "Error de red") }
            }
        }
    }

    private fun posicionParaRastreo(): CoordenadaCapturada? {
        val ahora = SystemClock.elapsedRealtime()
        if (simulacion) {
            lecturaSimulada?.let { return CoordenadaCapturada(it.valor.first.latitud, it.valor.first.longitud, 0f) }
        }
        // El encargado siempre recibe el GPS del teléfono, no el del ESP32.
        return posicionTelefono?.takeIf { ahora - it.instanteMs <= ConfigGuia.VIGENCIA_POSICION_TELEFONO_MS }?.let {
            CoordenadaCapturada(
                it.valor.latitude,
                it.valor.longitude,
                if (it.valor.hasAccuracy()) it.valor.accuracy else 0f
            )
        }
    }

    private fun detenerSesion() {
        trabajoCarga?.cancel()
        val id = usuarioId
        val ultima = posicionParaRastreo()
        detenerTrabajos()
        if (id != null && ultima != null) {
            // Último envío al terminar; se completa aunque el servicio se esté cerrando.
            scope.launch(Dispatchers.IO + NonCancellable) {
                runCatching { seguimientoRepository.guardarPosicion(id, ultima) }
                withContext(Dispatchers.Main) { retirarServicio() }
            }
        } else {
            retirarServicio()
        }
    }

    private fun detenerTrabajos() {
        trabajoGuia?.cancel()
        trabajoEnvio?.cancel()
        trabajoSimulacion?.cancel()
        sensores.detener()
        esp32.detener()
        motor = null
        simulador = null
        seguimientoId = null
        lecturaSimulada = null
    }

    private fun retirarServicio() {
        SesionGuia.reiniciar()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun iniciarPrimerPlano() {
        val notificacion = crearNotificacion("Preparando la guía…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notificacion,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notificacion)
        }
    }

    private fun actualizarNotificacion(texto: String) {
        if (texto == textoNotificacion) return
        textoNotificacion = texto
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, crearNotificacion(texto))
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID,
                "Guía de ruta de EcoGuía",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progreso de la ruta en curso y ubicación compartida con el encargado."
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    private fun crearNotificacion(texto: String): Notification {
        val abrirApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ubicacion)
            .setContentTitle("EcoGuía: ruta en curso")
            .setContentText(texto)
            .setContentIntent(abrirApp)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        detenerTrabajos()
        SesionGuia.reiniciar()
        scope.cancel()
        super.onDestroy()
    }

    private fun Location.aCoordenada() = Coordenada(latitude, longitude)

    private fun RutaDisponible.aRutaGuia() = RutaGuia(
        id = id,
        nombre = nombre,
        puntos = nodos.map { nodo ->
            PuntoRuta(
                id = nodo.id,
                nombre = nodo.nombre,
                tipo = nodo.tipo,
                descripcion = "",
                coordenada = Coordenada(nodo.latitud, nodo.longitud)
            )
        },
        longitudesAristas = distanciasTramosMetros
    )

    companion object {
        const val ACTION_INICIAR = "com.proyecto.ecoguia.action.INICIAR_GUIA"
        const val ACTION_DETENER = "com.proyecto.ecoguia.action.DETENER_GUIA"
        const val ACTION_SIMULACION = "com.proyecto.ecoguia.action.SIMULACION_GUIA"
        const val ACTION_VELOCIDAD = "com.proyecto.ecoguia.action.VELOCIDAD_GUIA"
        const val ACTION_DISPOSITIVO = "com.proyecto.ecoguia.action.DISPOSITIVO_GUIA"
        const val EXTRA_DISPOSITIVO = "dispositivo"
        const val EXTRA_SEGUIMIENTO_ID = "seguimiento_id"
        const val EXTRA_SIMULACION = "simulacion"
        const val EXTRA_VELOCIDAD = "velocidad"
        private const val CHANNEL_ID = "ecoguia_guia_ruta"
        private const val NOTIFICATION_ID = 2302
        private const val TAG = "EcoGuiaGuia"
    }
}
