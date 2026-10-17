package com.example.krono_corp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.krono_corp.negocio.*
import com.example.krono_corp.ui.theme.KronoCorpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KronoCorpTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KronoCorpAppNav()
                }
            }
        }
    }
}

@Composable
fun KronoCorpAppNav() {
    val navController = rememberNavController()
    // Estado compartido del negocio para reflejar cambios entre pantallas
    val negocioDataState = remember { NegocioData() }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                navController = navController,
                negocioData = negocioDataState
            )
        }
        composable(Screen.MiNegocio.route) {
            MiNegocioScreen(
                navController = navController,
                negocioData = negocioDataState,
                onGuardarCambios = { nuevoNombre, nuevasPreguntas ->
                    negocioDataState.nombreNegocio = nuevoNombre
                    negocioDataState.preguntasIA.clear()
                    negocioDataState.preguntasIA.addAll(nuevasPreguntas)
                }
            )
        }
        composable(Screen.Sucursales.route) {
            SucursalesScreen(
                navController = navController,
                negocioData = negocioDataState,
                onGuardarSucursales = { listaActualizada ->
                    negocioDataState.sucursales.clear()
                    negocioDataState.sucursales.addAll(listaActualizada)
                }
            )
        }
        composable(Screen.Configuracion.route) {
            ConfiguracionScreen(
                navController = navController,
                negocioData = negocioDataState
            )
        }
    }
}