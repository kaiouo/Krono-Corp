package com.example.krono_corp.data

import android.content.Context
import io.appwrite.Client
import io.appwrite.services.Account
import io.appwrite.services.Databases

/**
 * Único punto de configuración de Appwrite.
 * Se inicializa UNA vez en MainActivity.onCreate().
 */
object AppwriteClient {

    // Cópialos desde Appwrite Console -> tu proyecto -> Settings
    private const val ENDPOINT = "https://nyc.cloud.appwrite.io/v1"
    private const val PROJECT_ID = "6ad2861b0017d9d56db2"

    const val DATABASE_ID = "6ad3bb32001fdd27fe62"

    // Colecciones dentro de DATABASE_ID. Deben crearse en Appwrite Console con estos IDs
    // exactos (ver README para los atributos de cada una).
    const val COLECCION_USUARIOS = "6ad3bb57003dfd51b5ec"
    const val COLECCION_NEGOCIOS = "negocios"
    const val COLECCION_SUCURSALES = "sucursales"
    const val COLECCION_REPORTES = "reportes"
    const val COLECCION_GRAFICOS = "graficos"
    const val COLECCION_CUENTAS_ADMINISTRACION = "cuentas_administracion"
    const val COLECCION_VENTAS = "ventas"
    const val COLECCION_INVENTARIO = "inventario"

    private lateinit var client: Client

    lateinit var account: Account
        private set

    lateinit var databases: Databases
        private set

    fun init(context: Context) {
        if (::client.isInitialized) return
        client = Client(context)
            .setEndpoint(ENDPOINT)
            .setProject(PROJECT_ID)
        account = Account(client)
        databases = Databases(client)
    }
}
