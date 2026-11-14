package com.solventa.mobile

import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.solventa.mobile.notificaciones.AvisosAndroid
import com.solventa.mobile.notificaciones.IdentificadorDispositivoPreferencias
import com.solventa.mobile.notificaciones.IdiomaDelTelefono
import com.solventa.mobile.notificaciones.NotificacionesRepository
import com.solventa.mobile.notificaciones.SondeadorNotificaciones
import kotlinx.coroutines.delay
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solventa.mobile.biometria.BiometriaViewModel
import com.solventa.mobile.biometria.ClaveBiometricaKeystore
import com.solventa.mobile.biometria.EstadoClave
import com.solventa.mobile.bloqueo.BloqueoRoute
import com.solventa.mobile.bloqueo.BloqueoViewModel
import com.solventa.mobile.bloqueo.MotivoContrasena
import com.solventa.mobile.bloqueo.debeBloquearTrasSegundoPlano
import com.solventa.mobile.iniciosesion.InicioSesionRoute
import com.solventa.mobile.iniciosesion.InicioSesionViewModel
import com.solventa.mobile.sesion.Credenciales
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.LifecycleEventObserver
import android.os.SystemClock
import com.solventa.mobile.biometria.InvitacionRoute
import com.solventa.mobile.biometria.VistaInvitacion
import com.solventa.mobile.biometria.ProveedorCapacidadAndroid
import com.solventa.mobile.biometria.SeguridadRoute
import com.solventa.mobile.compra.CompraRoute
import com.solventa.mobile.compra.CompraViewModel
import com.solventa.mobile.consentimiento.ConsentimientoScreen
import com.solventa.mobile.consentimiento.ConsentimientoViewModel
import com.solventa.mobile.cotizacion.CotizacionRoute
import com.solventa.mobile.cotizacion.CotizacionViewModel
import com.solventa.mobile.firma.FirmaRoute
import com.solventa.mobile.firma.FirmaViewModel
import com.solventa.mobile.red.CotizacionResponse
import com.solventa.mobile.privacidad.PrivacidadRoute
import com.solventa.mobile.privacidad.PrivacidadViewModel
import com.solventa.mobile.navegacion.BarraPrincipal
import com.solventa.mobile.navegacion.Paso
import com.solventa.mobile.navegacion.Seccion
import com.solventa.mobile.navegacion.atras
import com.solventa.mobile.navegacion.conBarra
import com.solventa.mobile.polizas.PolizaDetalleRoute
import com.solventa.mobile.polizas.PolizasRoute
import com.solventa.mobile.polizas.PolizasViewModel
import com.solventa.mobile.navegacion.reiniciaSeguros
import com.solventa.mobile.navegacion.seccion
import com.solventa.mobile.registro.RegistroScreen
import com.solventa.mobile.registro.RegistroViewModel
import com.solventa.mobile.resultado.Destino
import com.solventa.mobile.resultado.ResultadoRoute
import com.solventa.mobile.resultado.ResultadoViewModel
import com.solventa.mobile.sesion.AlmacenSesion
import com.solventa.mobile.sesion.SesionActual
import com.solventa.mobile.ui.tema.SolventaTheme
import com.solventa.mobile.verificacion.VerificacionRoute
import com.solventa.mobile.verificacion.VerificacionViewModel
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    // Póliza cuyo certificado debe abrirse porque el cliente tocó la notificación (HU22). Lo recoge la composición.
    private var suscripcionDeNotificacion by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        suscripcionDeNotificacion = intent.getStringExtra(EXTRA_SUSCRIPCION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val almacen = AlmacenSesion.crear(this)
        val claveBiometrica = ClaveBiometricaKeystore()
        val vistaInvitacion = VistaInvitacion.crear(this)
        suscripcionDeNotificacion = intent?.getStringExtra(EXTRA_SUSCRIPCION)
        val avisos = AvisosAndroid(this)
        val sondeador = SondeadorNotificaciones(NotificacionesRepository(), avisos, IdentificadorDispositivoPreferencias(this))
        setContent {
            SolventaTheme {
                Surface {
                    // Registro (HU01) → consentimiento (HU06) → verificación (HU02) → resultado (HU03) → secciones: seguros (HU37) y
                    // perfil con los consentimientos (HU07), unidas por la barra inferior.
                    var paso by rememberSaveable { mutableStateOf(Paso.ARRANQUE) }
                    var pasoTrasConsentimiento by rememberSaveable { mutableStateOf(Paso.VERIFICACION) }
                    // Cada visita a verificación o resultado empieza con un ViewModel nuevo.
                    var visita by rememberSaveable { mutableIntStateOf(0) }
                    // La cotización en curso sobrevive a cambiar de pestaña: solo se reinicia al llegar desde fuera de las secciones.
                    var visitaSeguros by rememberSaveable { mutableIntStateOf(0) }
                    // Oferta que el cliente va a comprar; no sobrevive a que el sistema mate el proceso (se vuelve a cotizar).
                    var ofertaAComprar by remember { mutableStateOf<CotizacionResponse?>(null) }
                    // Saga cuya póliza se va a firmar. Sobrevive a que el sistema mate el proceso: la firma queda pendiente 24 h.
                    var suscripcionAFirmar by rememberSaveable { mutableStateOf<String?>(null) }
                    var sesionRestaurada by remember { mutableStateOf(SesionActual.activa) }
                    // Bloqueo biométrico (HU05). Las credenciales guardadas no pasan a `SesionActual` hasta desbloquear.
                    var credencialesBloqueadas by remember { mutableStateOf<Credenciales?>(null) }
                    var pasoTrasBloqueo by rememberSaveable { mutableStateOf(Paso.RESULTADO) }
                    var bloqueoEnFrio by rememberSaveable { mutableStateOf(true) }
                    var visitaBloqueo by rememberSaveable { mutableIntStateOf(0) }
                    var salioEn by remember { mutableStateOf<Long?>(null) }
                    var emailInicioSesion by remember { mutableStateOf<String?>(null) }
                    var avisoInicioSesion by remember { mutableStateOf<Int?>(null) }
                    val alcance = rememberCoroutineScope()

                    fun navegar(destino: Paso) {
                        visita++
                        if (reiniciaSeguros(desde = paso, hacia = destino)) visitaSeguros++
                        paso = destino
                    }

                    // Con el acceso biométrico activo, la sesión guardada no se abre hasta autenticarse (HU05, criterio 1).
                    LaunchedEffect(Unit) {
                        val guardadas = if (!SesionActual.activa) almacen.leer() else null
                        if (guardadas != null && claveBiometrica.estado() != EstadoClave.AUSENTE) {
                            credencialesBloqueadas = guardadas
                            bloqueoEnFrio = true
                            // Si el proceso murió con el bloqueo a medias, `paso` ya es BLOQUEO y el destino guardado se conserva.
                            if (paso != Paso.BLOQUEO) pasoTrasBloqueo = if (paso == Paso.ARRANQUE) Paso.RESULTADO else paso
                            visitaBloqueo++
                            sesionRestaurada = true
                            navegar(Paso.BLOQUEO)
                            return@LaunchedEffect
                        }
                        // Al reabrir la app (o tras morir el proceso) la sesión vuelve del almacén cifrado.
                        guardadas?.let { SesionActual.iniciar(it.clienteId, it.accessToken, it.nombre, it.email) }
                        sesionRestaurada = true
                        if (paso == Paso.ARRANQUE) navegar(if (SesionActual.activa) Paso.RESULTADO else Paso.REGISTRO)
                        else if (paso == Paso.BLOQUEO) navegar(if (SesionActual.activa) Paso.RESULTADO else Paso.REGISTRO)
                    }

                    // Vuelve a pedir la biometría tras 5 minutos en segundo plano (HU05, criterio 7). Reloj monótono.
                    val pasoActual by rememberUpdatedState(paso)
                    DisposableEffect(lifecycle) {
                        val observador = LifecycleEventObserver { _, evento ->
                            when (evento) {
                                Lifecycle.Event.ON_STOP -> salioEn = SystemClock.elapsedRealtime()
                                Lifecycle.Event.ON_START -> {
                                    val salio = salioEn
                                    salioEn = null
                                    val protegido = pasoActual != Paso.BLOQUEO && pasoActual != Paso.INICIO_SESION &&
                                        pasoActual != Paso.REGISTRO && pasoActual != Paso.ARRANQUE
                                    if (protegido && SesionActual.activa &&
                                        debeBloquearTrasSegundoPlano(salio, SystemClock.elapsedRealtime()) &&
                                        claveBiometrica.estado() == EstadoClave.VALIDA
                                    ) {
                                        credencialesBloqueadas = null
                                        bloqueoEnFrio = false
                                        pasoTrasBloqueo = pasoActual
                                        visitaBloqueo++
                                        // Sin `navegar`: volver al mismo paso no debe perder lo que el cliente tenía a medias.
                                        paso = Paso.BLOQUEO
                                    }
                                }
                                else -> Unit
                            }
                        }
                        lifecycle.addObserver(observador)
                        onDispose { lifecycle.removeObserver(observador) }
                    }

                    if (!sesionRestaurada || paso == Paso.ARRANQUE) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        return@Surface
                    }

                    // Notificaciones (HU22): mientras el cliente está dentro de la app y esta se ve, el «push» simulado de la
                    // bandeja del servidor se muestra como notificación del sistema.
                    val dentroDeLaApp = paso.seccion != null
                    LaunchedEffect(dentroDeLaApp) {
                        val cliente = SesionActual.clienteId
                        if (dentroDeLaApp && cliente != null) {
                            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                sondeador.registrar(cliente, IdiomaDelTelefono.actual())
                                while (true) {
                                    sondeador.sondear()
                                    delay(INTERVALO_SONDEO_MS)
                                }
                            }
                        }
                    }
                    // Android 13 o superior: el permiso se pide al llegar a la firma, que es cuando tiene sentido avisar.
                    var permisoPedido by rememberSaveable { mutableStateOf(false) }
                    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                    LaunchedEffect(paso) {
                        if (paso == Paso.FIRMA && !permisoPedido && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !avisos.puedeMostrar()) {
                            permisoPedido = true
                            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    // «Mis pólizas» (HU35): una sola instancia para la lista y el detalle, propia de cada sesión.
                    val polizasViewModel: PolizasViewModel = viewModel(key = "polizas-${SesionActual.clienteId}")
                    // Tocar la notificación abre el detalle de esa póliza (HU35, criterio 7).
                    LaunchedEffect(suscripcionDeNotificacion, paso) {
                        val id = suscripcionDeNotificacion ?: return@LaunchedEffect
                        suscripcionDeNotificacion = null
                        if (SesionActual.activa) {
                            polizasViewModel.abrirPorSuscripcion(id)
                            navegar(Paso.POLIZA_DETALLE)
                        }
                    }

                    // Atrás dentro de una sección vuelve a su raíz; desde seguros sale de la app.
                    BackHandler(enabled = paso.atras != null) { paso.atras?.let(::navegar) }

                    Scaffold(
                        bottomBar = {
                            paso.seccion?.let { seccion ->
                                BarraPrincipal(seleccion = seccion) { elegida ->
                                    val destino = when (elegida) {
                                        Seccion.SEGUROS -> Paso.INICIO
                                        Seccion.POLIZAS -> Paso.POLIZAS
                                        Seccion.PERFIL -> Paso.PERFIL
                                    }
                                    if (destino != paso) navegar(destino)
                                }
                            }
                        },
                    ) { relleno ->
                        Box(Modifier.padding(relleno)) {
                            when (paso) {
                                Paso.ARRANQUE -> Unit

                                Paso.REGISTRO -> {
                                    val viewModel: RegistroViewModel = viewModel(key = "registro-$visita")
                                    RegistroScreen(
                                        viewModel = viewModel,
                                        onRegistroExitoso = {
                                            SesionActual.credenciales()?.let { alcance.launch { almacen.guardar(it) } }
                                            pasoTrasConsentimiento = Paso.VERIFICACION
                                            navegar(Paso.CONSENTIMIENTO)
                                        },
                                        onYaTengoCuenta = {
                                            emailInicioSesion = null
                                            avisoInicioSesion = null
                                            navegar(Paso.INICIO_SESION)
                                        },
                                    )
                                }

                                Paso.INICIO_SESION -> {
                                    val correo = emailInicioSesion ?: SesionActual.ultimoEmail.orEmpty()
                                    val viewModel: InicioSesionViewModel = viewModel(key = "iniciosesion-$visita") { InicioSesionViewModel(correo) }
                                    InicioSesionRoute(
                                        viewModel = viewModel,
                                        aviso = avisoInicioSesion,
                                        onSesionIniciada = {
                                            SesionActual.credenciales()?.let { alcance.launch { almacen.guardar(it) } }
                                            emailInicioSesion = null
                                            avisoInicioSesion = null
                                            navegar(Paso.RESULTADO)
                                        },
                                        onCrearCuenta = { navegar(Paso.REGISTRO) },
                                        onAtras = { navegar(Paso.REGISTRO) },
                                    )
                                }

                                Paso.BLOQUEO -> {
                                    val viewModel: BloqueoViewModel = viewModel(key = "bloqueo-$visitaBloqueo") { BloqueoViewModel(claveBiometrica) }
                                    BloqueoRoute(
                                        viewModel = viewModel,
                                        nombre = credencialesBloqueadas?.nombre ?: SesionActual.nombre,
                                        onDesbloqueado = {
                                            // Solo ahora la sesión guardada pasa a estar disponible.
                                            credencialesBloqueadas?.let { SesionActual.iniciar(it.clienteId, it.accessToken, it.nombre, it.email) }
                                            credencialesBloqueadas = null
                                            if (bloqueoEnFrio) navegar(pasoTrasBloqueo) else paso = pasoTrasBloqueo
                                        },
                                        onUsarContrasena = { motivo ->
                                            val clienteId = credencialesBloqueadas?.clienteId ?: SesionActual.clienteId
                                            emailInicioSesion = credencialesBloqueadas?.email ?: SesionActual.email ?: SesionActual.ultimoEmail
                                            avisoInicioSesion = when (motivo) {
                                                MotivoContrasena.TRES_FALLOS -> R.string.iniciosesion_aviso_tres_fallos
                                                MotivoContrasena.BIOMETRIA_CAMBIO -> R.string.iniciosesion_aviso_cambio
                                                MotivoContrasena.ERROR -> R.string.iniciosesion_aviso_error
                                                MotivoContrasena.ELEGIDA -> null
                                            }
                                            // El token guardado ya no se usa: la sesión nueva nace con la contraseña.
                                            val cambio = viewModel.cambioDeBiometria
                                            SesionActual.cerrar()
                                            credencialesBloqueadas = null
                                            alcance.launch {
                                                almacen.borrar()
                                                // Si cambiaron las biometrías, tras entrar se vuelve a invitar a activar el acceso.
                                                if (cambio && clienteId != null) vistaInvitacion.desmarcar(clienteId)
                                            }
                                            navegar(Paso.INICIO_SESION)
                                        },
                                    )
                                }

                                Paso.CONSENTIMIENTO -> {
                                    // Cada visita empieza con un ViewModel nuevo: si no, `continuar` seguiría en true y se saltaría el consentimiento.
                                    val viewModel: ConsentimientoViewModel = viewModel(key = "consentimiento-$visita")
                                    ConsentimientoScreen(
                                        viewModel = viewModel,
                                        onContinuar = { navegar(pasoTrasConsentimiento) },
                                    )
                                }

                                Paso.VERIFICACION -> {
                                    val viewModel: VerificacionViewModel = viewModel(key = "verificacion-$visita")
                                    VerificacionRoute(
                                        viewModel = viewModel,
                                        onFinalizar = { navegar(Paso.RESULTADO) },
                                    )
                                }

                                Paso.RESULTADO -> {
                                    val viewModel: ResultadoViewModel = viewModel(key = "resultado-$visita") {
                                        ResultadoViewModel(alPerderSesion = {
                                            SesionActual.cerrar()
                                            almacen.borrar()
                                        })
                                    }
                                    ResultadoRoute(
                                        viewModel = viewModel,
                                        onDestino = { destino ->
                                            when (destino) {
                                                // La sesión guardada dejó de valer: se entra con la contraseña, no se vuelve a registrar.
                                                Destino.REGISTRO -> navegar(Paso.INICIO_SESION)
                                                Destino.CONSENTIMIENTO_ANTES_DE_VERIFICAR -> {
                                                    pasoTrasConsentimiento = Paso.VERIFICACION
                                                    navegar(Paso.CONSENTIMIENTO)
                                                }
                                                Destino.CONSENTIMIENTO_TRAS_APROBAR -> {
                                                    pasoTrasConsentimiento = Paso.INVITACION_BIOMETRICA
                                                    navegar(Paso.CONSENTIMIENTO)
                                                }
                                                Destino.VERIFICACION -> navegar(Paso.VERIFICACION)
                                                Destino.INICIO -> navegar(Paso.INVITACION_BIOMETRICA)
                                            }
                                        },
                                        // En revisión o sin intentos: el usuario sale y al volver ve el estado vigente.
                                        onSalir = { finish() },
                                    )
                                }

                                Paso.INICIO -> {
                                    val viewModel: CotizacionViewModel = viewModel(key = "cotizacion-$visitaSeguros")
                                    CotizacionRoute(
                                        viewModel = viewModel,
                                        // Compartir los datos lleva al consentimiento y vuelve a cotizar con el perfil del cliente.
                                        onCompartirDatos = {
                                            pasoTrasConsentimiento = Paso.INICIO
                                            navegar(Paso.CONSENTIMIENTO)
                                        },
                                        onSesionVencida = {
                                            SesionActual.cerrar()
                                            alcance.launch { almacen.borrar() }
                                            navegar(Paso.INICIO_SESION)
                                        },
                                        onAceptarOferta = {
                                            ofertaAComprar = it
                                            navegar(Paso.COMPRA)
                                        },
                                    )
                                }

                                Paso.COMPRA -> {
                                    val oferta = ofertaAComprar
                                    if (oferta == null) {
                                        LaunchedEffect(Unit) { navegar(Paso.INICIO) }
                                    } else {
                                        val viewModel: CompraViewModel = viewModel(key = "compra-${oferta.id}")
                                        LaunchedEffect(oferta.id) { viewModel.iniciar(oferta) }
                                        CompraRoute(
                                            viewModel = viewModel,
                                            // Oferta vencida o rechazada: cotiza de nuevo; terminar también deja un formulario limpio.
                                            onCotizarDeNuevo = { visitaSeguros++; navegar(Paso.INICIO) },
                                            onTerminar = { visitaSeguros++; navegar(Paso.INICIO) },
                                            // Atrás deja el resultado de la cotización tal como estaba.
                                            onVolver = { navegar(Paso.INICIO) },
                                            // Cobro confirmado: lo que falta es firmar la póliza (HU21).
                                            onFirmar = { id ->
                                                suscripcionAFirmar = id
                                                navegar(Paso.FIRMA)
                                            },
                                            onSesionVencida = {
                                                SesionActual.cerrar()
                                                alcance.launch { almacen.borrar() }
                                                navegar(Paso.INICIO_SESION)
                                            },
                                        )
                                    }
                                }

                                Paso.FIRMA -> {
                                    val id = suscripcionAFirmar
                                    if (id == null) {
                                        LaunchedEffect(Unit) { navegar(Paso.INICIO) }
                                    } else {
                                        val viewModel: FirmaViewModel = viewModel(key = "firma-$id") {
                                            FirmaViewModel(dispositivo = {
                                                "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
                                            })
                                        }
                                        LaunchedEffect(id) { viewModel.iniciar(id) }
                                        FirmaRoute(
                                            viewModel = viewModel,
                                            // Firmada o devuelta: se vuelve a un formulario de cotización limpio.
                                            onTerminar = { visitaSeguros++; navegar(Paso.INICIO) },
                                            onCotizarDeNuevo = { visitaSeguros++; navegar(Paso.INICIO) },
                                            onVolver = { navegar(Paso.INICIO) },
                                            onSesionVencida = {
                                                SesionActual.cerrar()
                                                alcance.launch { almacen.borrar() }
                                                navegar(Paso.INICIO_SESION)
                                            },
                                        )
                                    }
                                }

                                Paso.POLIZAS -> PolizasRoute(
                                    viewModel = polizasViewModel,
                                    onCotizar = { navegar(Paso.INICIO) },
                                    onAbrirDetalle = { navegar(Paso.POLIZA_DETALLE) },
                                    onRetomarFirma = { id ->
                                        suscripcionAFirmar = id
                                        navegar(Paso.FIRMA)
                                    },
                                    onSesionVencida = {
                                        SesionActual.cerrar()
                                        alcance.launch { almacen.borrar() }
                                        navegar(Paso.INICIO_SESION)
                                    },
                                )

                                Paso.POLIZA_DETALLE -> PolizaDetalleRoute(
                                    viewModel = polizasViewModel,
                                    onVolver = { navegar(Paso.POLIZAS) },
                                    onRetomarFirma = { id ->
                                        suscripcionAFirmar = id
                                        navegar(Paso.FIRMA)
                                    },
                                    onSesionVencida = {
                                        SesionActual.cerrar()
                                        alcance.launch { almacen.borrar() }
                                        navegar(Paso.INICIO_SESION)
                                    },
                                )

                                Paso.PERFIL -> {
                                    val viewModel: PrivacidadViewModel = viewModel(key = "privacidad-$visita")
                                    PrivacidadRoute(
                                        viewModel = viewModel,
                                        // Volver a compartir reutiliza el consentimiento de HU06 y regresa aquí con el estado al día.
                                        onCompartirDatos = {
                                            pasoTrasConsentimiento = Paso.PERFIL
                                            navegar(Paso.CONSENTIMIENTO)
                                        },
                                        onSesionVencida = {
                                            SesionActual.cerrar()
                                            alcance.launch { almacen.borrar() }
                                            navegar(Paso.INICIO_SESION)
                                        },
                                        onSeguridad = { navegar(Paso.SEGURIDAD) },
                                    )
                                }

                                Paso.INVITACION_BIOMETRICA -> {
                                    val contexto = applicationContext
                                    val viewModel: BiometriaViewModel = viewModel(
                                        key = "invitacion-$visita",
                                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                            @Suppress("UNCHECKED_CAST")
                                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                                                BiometriaViewModel(ProveedorCapacidadAndroid(contexto), ClaveBiometricaKeystore()) as T
                                        },
                                    )
                                    InvitacionRoute(
                                        viewModel = viewModel,
                                        vista = vistaInvitacion,
                                        onContinuar = { navegar(Paso.INICIO) },
                                    )
                                }

                                Paso.SEGURIDAD -> {
                                    val contexto = applicationContext
                                    val viewModel: BiometriaViewModel = viewModel(
                                        key = "biometria-$visita",
                                        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                            @Suppress("UNCHECKED_CAST")
                                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                                                BiometriaViewModel(ProveedorCapacidadAndroid(contexto), ClaveBiometricaKeystore()) as T
                                        },
                                    )
                                    SeguridadRoute(
                                        viewModel = viewModel,
                                        onAtras = { navegar(Paso.PERFIL) },
                                        onSesionVencida = {
                                            SesionActual.cerrar()
                                            alcance.launch { almacen.borrar() }
                                            navegar(Paso.INICIO_SESION)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Identificador de la suscripción cuyo certificado abre la notificación (HU22). */
        const val EXTRA_SUSCRIPCION = "suscripcion_id"

        /** Cada cuánto se consulta la bandeja mientras la app está visible. */
        private const val INTERVALO_SONDEO_MS = 10_000L
    }
}
