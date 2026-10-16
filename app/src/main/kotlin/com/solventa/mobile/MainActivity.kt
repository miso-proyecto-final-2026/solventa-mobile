package com.solventa.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solventa.mobile.consentimiento.ConsentimientoScreen
import com.solventa.mobile.consentimiento.ConsentimientoViewModel
import com.solventa.mobile.registro.RegistroScreen
import com.solventa.mobile.registro.RegistroViewModel
import com.solventa.mobile.verificacion.VerificacionRoute
import com.solventa.mobile.verificacion.VerificacionViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    // Registro (HU01) → consentimiento (HU06) → verificación de identidad (HU02).
                    var paso by remember { mutableStateOf(Paso.REGISTRO) }
                    when (paso) {
                        Paso.REGISTRO -> {
                            val viewModel: RegistroViewModel = viewModel()
                            RegistroScreen(
                                viewModel = viewModel,
                                onRegistroExitoso = { paso = Paso.CONSENTIMIENTO },
                            )
                        }

                        Paso.CONSENTIMIENTO -> {
                            val viewModel: ConsentimientoViewModel = viewModel()
                            ConsentimientoScreen(
                                viewModel = viewModel,
                                onContinuar = { paso = Paso.VERIFICACION },
                            )
                        }

                        Paso.VERIFICACION -> {
                            val viewModel: VerificacionViewModel = viewModel()
                            VerificacionRoute(
                                viewModel = viewModel,
                                onFinalizar = { paso = Paso.INICIO },
                            )
                        }

                        Paso.INICIO -> InicioScreen()
                    }
                }
            }
        }
    }
}

private enum class Paso { REGISTRO, CONSENTIMIENTO, VERIFICACION, INICIO }
