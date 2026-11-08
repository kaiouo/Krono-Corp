package com.example.krono_corp.data

import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Query
import io.appwrite.Role
import io.appwrite.models.Row
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Lo mínimo de una venta que necesita el GERENTE para medir el rendimiento por sucursal. */
data class VentaDeSucursal(
    val usuarioId: String,
    val sucursalId: String,  // ID de la sucursal (vacío en ventas antiguas)
    val sucursal: String,    // nombre de la sucursal tal como se registró
    val cantidad: Int,
    val total: Double
)

/** Ventas que registra el SUPERVISOR o el DUENO desde el chat con la IA. */
object VentasRepository {

    const val TABLE = "6ae91a26000743892eda"
    private const val MAX_FILAS = 2000

    private val db get() = AppwriteClient.tablesDB
    private val dbId get() = AppwriteClient.DATABASE_ID

    suspend fun guardar(
        usuario: AppUser,
        v: NuevaVenta,
        sucursalId: String = ""
    ): Result<VentaRegistrada> =
        appwriteSafe(TABLE) {
            // El dueño de la venta puede leerla y eliminarla (para corregir errores).
            // El gerente la lee por el permiso de la etiqueta «gerente» a nivel de tabla:
            // Appwrite no deja que un usuario otorgue permisos a roles que él no tiene.
            val permisos = listOf(
                Permission.read(Role.user(usuario.id)),
                Permission.delete(Role.user(usuario.id))
            )
            val fila = db.createRow(
                databaseId = dbId,
                tableId = TABLE,
                rowId = ID.unique(),
                data = mapOf(
                    "usuarioId" to usuario.id,
                    "usuarioNombre" to usuario.alias,
                    "fechaVenta" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "producto" to v.producto,
                    "cantidad" to v.cantidad,
                    "precioUnitario" to v.precioUnitario,
                    "descuento" to v.descuento,
                    "total" to v.total,
                    "sucursal" to v.sucursal,
                    "sucursalId" to sucursalId,
                    "metodoPago" to v.metodoPago,
                    "nota" to v.nota,
                    "extras" to JSONObject(v.extras).toString()
                ),
                permissions = permisos
            )
            fila.toVenta()
        }

    /** Todas las ventas del usuario, de la más reciente a la más antigua. */
    suspend fun listar(usuarioId: String): Result<List<VentaRegistrada>> = appwriteSafe(TABLE) {
        val todas = mutableListOf<VentaRegistrada>()
        var cursor: String? = null
        while (todas.size < MAX_FILAS) {
            val consultas = mutableListOf(Query.orderDesc("\$createdAt"), Query.limit(100))
            cursor?.let { consultas.add(Query.cursorAfter(it)) }
            val pagina = db.listRows(databaseId = dbId, tableId = TABLE, queries = consultas).rows
            todas += pagina.map { it.toVenta() }
            if (pagina.size < 100) break
            cursor = pagina.last().id
        }
        todas.filter { it.usuarioId == usuarioId }
    }

    /**
     * Ventas que el gerente puede leer (las de sus supervisores, por permiso de fila).
     * El gerente filtra después por sus sucursales y supervisores.
     */
    suspend fun listarParaGerente(): Result<List<VentaDeSucursal>> = appwriteSafe(TABLE) {
        val todas = mutableListOf<VentaDeSucursal>()
        var cursor: String? = null
        while (todas.size < MAX_FILAS) {
            val consultas = mutableListOf(Query.limit(100))
            cursor?.let { consultas.add(Query.cursorAfter(it)) }
            val pagina = db.listRows(databaseId = dbId, tableId = TABLE, queries = consultas).rows
            todas += pagina.map {
                VentaDeSucursal(
                    usuarioId = it.data["usuarioId"] as? String ?: "",
                    sucursalId = it.data["sucursalId"] as? String ?: "",
                    sucursal = it.data["sucursal"] as? String ?: "",
                    cantidad = (it.data["cantidad"] as? Number)?.toInt() ?: 0,
                    total = (it.data["total"] as? Number)?.toDouble() ?: 0.0
                )
            }
            if (pagina.size < 100) break
            cursor = pagina.last().id
        }
        todas
    }

    suspend fun eliminar(id: String): Result<Unit> = appwriteSafe(TABLE) {
        db.deleteRow(databaseId = dbId, tableId = TABLE, rowId = id)
        Unit
    }

    private fun Row<Map<String, Any>>.toVenta(): VentaRegistrada {
        val extras = try {
            val o = JSONObject(data["extras"] as? String ?: "{}")
            o.keys().asSequence().associateWith { o.optString(it, "") }
        } catch (e: Exception) {
            emptyMap()
        }
        return VentaRegistrada(
            id = id,
            usuarioId = data["usuarioId"] as? String ?: "",
            usuarioNombre = data["usuarioNombre"] as? String ?: "",
            fecha = data["fechaVenta"] as? String ?: "",
            producto = data["producto"] as? String ?: "",
            cantidad = (data["cantidad"] as? Number)?.toInt() ?: 0,
            precioUnitario = (data["precioUnitario"] as? Number)?.toDouble() ?: 0.0,
            descuento = (data["descuento"] as? Number)?.toDouble() ?: 0.0,
            total = (data["total"] as? Number)?.toDouble() ?: 0.0,
            sucursal = data["sucursal"] as? String ?: "",
            metodoPago = data["metodoPago"] as? String ?: "",
            nota = data["nota"] as? String ?: "",
            extras = extras
        )
    }
}