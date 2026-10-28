package com.example.krono_corp.data

import android.content.Context
import io.appwrite.Client
import io.appwrite.services.Account
import io.appwrite.services.TablesDB

/**
 * Único punto de configuración de Appwrite.
 * Se inicializa UNA vez en MainActivity.onCreate().
 */
object AppwriteClient {

    // Cópialos desde Appwrite Console -> tu proyecto -> Settings
    private const val ENDPOINT = "https://nyc.cloud.appwrite.io/v1"
    private const val PROJECT_ID = "6ad2861b0017d9d56db2"

    const val DATABASE_ID = "6ad3bb32001fdd27fe62"
    const val TABLE_ID = "6ad3bb57003dfd51b5ec"

    private lateinit var client: Client

    lateinit var account: Account
        private set

    lateinit var tablesDB: TablesDB
        private set

    fun init(context: Context) {
        if (::client.isInitialized) return
        client = Client(context)
            .setEndpoint(ENDPOINT)
            .setProject(PROJECT_ID)
        account = Account(client)
        tablesDB = TablesDB(client)
    }
}