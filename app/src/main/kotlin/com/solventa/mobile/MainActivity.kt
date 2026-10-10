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
import com.solventa.mobile.registro.RegistroScreen
import com.solventa.mobile.registro.RegistroViewModel
import com.solventa.mobile.verificacion.VerificacionPendienteScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    var registroCompletado by remember { mutableStateOf(false) }
                    if (registroCompletado) {
                        VerificacionPendienteScreen()
                    } else {
                        val viewModel: RegistroViewModel = viewModel()
                        RegistroScreen(
                            viewModel = viewModel,
                            onRegistroExitoso = { registroCompletado = true },
                        )
                    }
                }
            }
        }
    }
}
