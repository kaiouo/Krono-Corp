package com.example.krono_corp.data

import com.example.krono_corp.negocio.SucursalItem
import io.appwrite.ID
import io.appwrite.Query
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.Row
import kotlinx.coroutines.CancellationException
import java.io.IOException

object SucursalesRepository {

    const val TABLE_SUCURSALES = "6ae910640008d07f4116"

    private val db get() = AppwriteClient.tablesDB
    private val dbId get() = AppwriteClient.DATABASE_ID

    suspend fun listar(negocioId: String): Result<List<SucursalItem>> = safe {
        db.listRows(
            databaseId = dbId,
            tableId = TABLE_SUCURSALES,
            queries = listOf(
                Query.equal("negocioId", negocioId),
                Query.limit(100)
            )
        ).rows.map { it.toSucursal() }
    }

    suspend fun crear(
        negocioId: String,
        nombre: String,
        activa: Boolean,
        codigoAcceso: String
    ): Result<SucursalItem> = safe {
        db.createRow(
            databaseId = dbId,
            tableId = TABLE_SUCURSALES,
            rowId = ID.unique(),
            data = mapOf(
                "negocioId" to negocioId,
                "nombre" to nombre,
                "activa" to activa,
                "codigoAcceso" to codigoAcceso
            )
        ).toSucursal()
    }

    suspend fun actualizar(sucursal: SucursalItem): Result<SucursalItem> = safe {
        db.updateRow(
            databaseId = dbId,
            tableId = TABLE_SUCURSALES,
            rowId = sucursal.id,
            data = mapOf(
                "nombre" to sucursal.nombre,
                "activa" to sucursal.activa,
                "codigoAcceso" to sucursal.codigoAcceso
            )
        ).toSucursal()
    }

    suspend fun eliminar(id: String): Result<Unit> = safe {
        db.deleteRow(databaseId = dbId, tableId = TABLE_SUCURSALES, rowId = id)
        Unit
    }

    // ---------- helpers ----------

    private fun Row<Map<String, Any>>.toSucursal() = SucursalItem(
        id = id,
        nombre = data["nombre"] as? String ?: "",
        activa = data["activa"] as? Boolean ?: true,
        codigoAcceso = data["codigoAcceso"] as? String ?: "1234"
    )

    private suspend fun <T> safe(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.toMensaje()))
    }

    private fun Exception.toMensaje(): String = when (this) {
        is AppwriteException -> when (code) {
            401, 403 -> "Sin permisos en la tabla de sucursales. Revisa Settings > Permissions en Appwrite."
            404 -> "No se encontró la tabla de sucursales. Revisa el ID de la tabla."
            else -> message ?: "Error del servidor (código $code)."
        }
        is IOException -> "Sin conexión a internet."
        else -> message ?: "Ocurrió un error inesperado."
    }
}