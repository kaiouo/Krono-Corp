package com.example.krono_corp.data

import android.util.Log
import io.appwrite.ID
import io.appwrite.Permission
import io.appwrite.Query
import io.appwrite.Role
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Datos de un negocio tal como viven en Appwrite (coleccion "negocios").
 */
data class NegocioRemoto(
    val id: String,
    val ownerId: String,
    val nombreNegocio: String,
    val preguntasIA: List<String>
)

data class SucursalRemota(
    val id: String,
    val nombre: String,
    val activa: Boolean,
    val codigoAcceso: String
)

data class ReporteRemoto(
    val id: String,
    val titulo: String,
    val fecha: String,
    val contenido: String
)

data class GraficoRemoto(
    val id: String,
    val titulo: String,
    val fecha: String,
    // Codificado como "etiqueta1:valor1,etiqueta2:valor2,..." (coleccion sin tipo JSON dedicado)
    val datosCodificados: String
)

data class CuentaAdministracionRemota(
    val id: String,
    val nombre: String,
    val password: String
)

data class VentaRemota(
    val id: String,
    val producto: String,
    val cantidad: Int,
    val total: Double,
    val fecha: String
)

data class ProductoInventarioRemoto(
    val id: String,
    val nombre: String,
    val stock: Int
)

/**
 * Repositorio de Appwrite para el modulo de negocio: el negocio en si, sus sucursales,
 * y lo que genera el Asistente (Modulo de Persona 4: historial de reportes, historial de
 * graficos y cuentas de administracion adicionales).
 *
 * Mismo patron que AuthRepository: `safe {}` envuelve cada llamada y traduce el error a un
 * mensaje en español; el permiso de lectura es para cualquier usuario con sesion (asi el
 * Gerente General puede ver reportes y graficos tambien), y solo el dueño puede escribir.
 */
object NegocioRepository {

    private const val TAG = "KronoNegocio"

    private val databases get() = AppwriteClient.databases
    private const val DB = AppwriteClient.DATABASE_ID

    // ---------- Negocio ----------

    /** Busca el negocio del propietario; si no existe todavia, lo crea con datos iniciales. */
    suspend fun obtenerOCrearNegocio(ownerId: String, propietarioEmail: String): Result<NegocioRemoto> = safe {
        val existentes = databases.listDocuments(
            databaseId = DB,
            collectionId = AppwriteClient.COLECCION_NEGOCIOS,
            queries = listOf(Query.equal("ownerId", ownerId), Query.limit(1))
        )
        val doc = existentes.documents.firstOrNull()
        if (doc != null) {
            return@safe NegocioRemoto(
                id = doc.id,
                ownerId = ownerId,
                nombreNegocio = doc.data["nombreNegocio"] as? String ?: "Mi Negocio",
                preguntasIA = (doc.data["preguntasIA"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }

        val nuevo = databases.createDocument(
            databaseId = DB,
            collectionId = AppwriteClient.COLECCION_NEGOCIOS,
            documentId = ID.unique(),
            data = mapOf(
                "ownerId" to ownerId,
                "nombreNegocio" to "Mi Negocio",
                "preguntasIA" to listOf(
                    "Sobre Productos: ¿Cuál es el producto más vendido?",
                    "Sobre Ventas: ¿Cuáles son los meses con mayores ventas?"
                )
            ),
            permissions = listOf(
                Permission.read(Role.users()),
                Permission.update(Role.user(ownerId)),
                Permission.delete(Role.user(ownerId))
            )
        )
        Log.i(TAG, "Negocio creado: ${nuevo.id}")
        NegocioRemoto(
            id = nuevo.id,
            ownerId = ownerId,
            nombreNegocio = "Mi Negocio",
            preguntasIA = listOf(
                "Sobre Productos: ¿Cuál es el producto más vendido?",
                "Sobre Ventas: ¿Cuáles son los meses con mayores ventas?"
            )
        )
    }

    suspend fun actualizarNegocio(negocioId: String, nombreNegocio: String, preguntasIA: List<String>): Result<Unit> = safe {
        databases.updateDocument(
            databaseId = DB,
            collectionId = AppwriteClient.COLECCION_NEGOCIOS,
            documentId = negocioId,
            data = mapOf(
                "nombreNegocio" to nombreNegocio,
                "preguntasIA" to preguntasIA
            )
        )
        Unit
    }

    // ---------- Sucursales ----------

    suspend fun listarSucursales(negocioId: String): Result<List<SucursalRemota>> = safe {
        databases.listDocuments(
            databaseId = DB,
            collectionId = AppwriteClient.COLECCION_SUCURSALES,
            queries = listOf(Query.equal("negocioId", negocioId), Query.limit(100))
        ).documents.map {
            SucursalRemota(
                id = it.id,
                nombre = it.data["nombre"] as? String ?: "",
                activa = it.data["activa"] as? Boolean ?: true,
                codigoAcceso = it.data["codigoAcceso"] as? String ?: "1234"
            )
        }
    }

    /**
     * Reemplaza todas las sucursales del negocio por la lista dada (borra, actualiza y crea
     * segun haga falta). Mas simple que calcular un diff fino y suficiente para una lista corta.
     */
    suspend fun reemplazarSucursales(
        negocioId: String,
        ownerId: String,
        nuevas: List<Triple<String, String, Pair<Boolean, String>>> // id ("" si es nueva), nombre, (activa, codigoAcceso)
    ): Result<Unit> = safe {
        val actuales = databases.listDocuments(
            databaseId = DB,
            collectionId = AppwriteClient.COLECCION_SUCURSALES,
            queries = listOf(Query.equal("negocioId", negocioId), Query.limit(100))
        ).documents.map { it.id }.toSet()

        val idsNuevos = nuevas.map { it.first }.filter { it.isNotEmpty() }.toSet()

        // Borra las que ya no estan en la lista nueva
        (actuales - idsNuevos).forEach { idBorrar ->
            databases.deleteDocument(DB, AppwriteClient.COLECCION_SUCURSALES, idBorrar)
        }

        nuevas.forEach { (id, nombre, estado) ->
            val (activa, codigo) = estado
            if (id.isNotEmpty() && actuales.contains(id)) {
                databases.updateDocument(
                    databaseId = DB,
                    collectionId = AppwriteClient.COLECCION_SUCURSALES,
                    documentId = id,
                    data = mapOf("nombre" to nombre, "activa" to activa, "codigoAcceso" to codigo)
                )
            } else {
                databases.createDocument(
                    databaseId = DB,
                    collectionId = AppwriteClient.COLECCION_SUCURSALES,
                    documentId = ID.unique(),
                    data = mapOf(
                        "negocioId" to negocioId,
                        "nombre" to nombre,
                        "activa" to activa,
                        "codigoAcceso" to codigo
                    ),
                    permissions = listOf(
                        Permission.read(Role.users()),
                        Permission.update(Role.user(ownerId)),
                        Permission.delete(Role.user(ownerId))
                    )
                )
            }
        }
        Unit
    }

    // ---------- Historial de reportes y graficos (Modulo de Persona 4) ----------

    /** scope: "negocio" (lo genera el dueño/supervisor) o "gerente". */
    suspend fun crearReporte(scope: String, ownerId: String, titulo: String, fecha: String, contenido: String): Result<ReporteRemoto> = safe {
        val doc = databases.createDocument(
            databaseId = DB,
            collectionId = AppwriteClient.COLECCION_REPORTES,
            documentId = ID.unique(),
            data = mapOf("scope" to scope, "ownerId" to ownerId, "titulo" to titulo, "fecha" to fecha, "contenido" to contenido),
            permissions = listOf(Permission.read(Role.users()), Permission.update(Role.user(ownerId)), Permission.delete(Role.user(ownerId)))
        )
        ReporteRemoto(doc.id, titulo, fecha, contenido)
    }

    suspend fun listarReportes(scope: String, ownerId: String? = null): Result<List<ReporteRemoto>> = safe {
        val filtros = mutableListOf(Query.equal("scope", scope), Query.orderDesc("\$createdAt"), Query.limit(50))
        if (ownerId != null) filtros.add(Query.equal("ownerId", ownerId))
        databases.listDocuments(DB, AppwriteClient.COLECCION_REPORTES, filtros).documents.map {
            ReporteRemoto(
                id = it.id,
                titulo = it.data["titulo"] as? String ?: "",
                fecha = it.data["fecha"] as? String ?: "",
                contenido = it.data["contenido"] as? String ?: ""
            )
        }
    }

    suspend fun crearGrafico(scope: String, ownerId: String, titulo: String, fecha: String, datosCodificados: String): Result<GraficoRemoto> = safe {
        val doc = databases.createDocument(
            databaseId = DB,
            collectionId = AppwriteClient.COLECCION_GRAFICOS,
            documentId = ID.unique(),
            data = mapOf("scope" to scope, "ownerId" to ownerId, "titulo" to titulo, "fecha" to fecha, "datos" to datosCodificados),
            permissions = listOf(Permission.read(Role.users()), Permission.update(Role.user(ownerId)), Permission.delete(Role.user(ownerId)))
        )
        GraficoRemoto(doc.id, titulo, fecha, datosCodificados)
    }

    suspend fun listarGraficos(scope: String, ownerId: String? = null): Result<List<GraficoRemoto>> = safe {
        val filtros = mutableListOf(Query.equal("scope", scope), Query.orderDesc("\$createdAt"), Query.limit(50))
        if (ownerId != null) filtros.add(Query.equal("ownerId", ownerId))
        databases.listDocuments(DB, AppwriteClient.COLECCION_GRAFICOS, filtros).documents.map {
            GraficoRemoto(
                id = it.id,
                titulo = it.data["titulo"] as? String ?: "",
                fecha = it.data["fecha"] as? String ?: "",
                datosCodificados = it.data["datos"] as? String ?: ""
            )
        }
    }

    // ---------- Nueva cuenta de administracion (Modulo de Persona 4) ----------

    suspend fun listarCuentasAdministracion(negocioId: String): Result<List<CuentaAdministracionRemota>> = safe {
        databases.listDocuments(
            DB, AppwriteClient.COLECCION_CUENTAS_ADMINISTRACION,
            listOf(Query.equal("negocioId", negocioId), Query.limit(50))
        ).documents.map {
            CuentaAdministracionRemota(
                id = it.id,
                nombre = it.data["nombre"] as? String ?: "",
                password = it.data["password"] as? String ?: ""
            )
        }
    }

    suspend fun crearCuentaAdministracion(negocioId: String, ownerId: String, nombre: String, password: String): Result<CuentaAdministracionRemota> = safe {
        val doc = databases.createDocument(
            databaseId = DB,
            collectionId = AppwriteClient.COLECCION_CUENTAS_ADMINISTRACION,
            documentId = ID.unique(),
            data = mapOf("negocioId" to negocioId, "nombre" to nombre, "password" to password),
            permissions = listOf(Permission.read(Role.users()), Permission.update(Role.user(ownerId)), Permission.delete(Role.user(ownerId)))
        )
        CuentaAdministracionRemota(doc.id, nombre, password)
    }

    // ---------- Ventas e inventario (datos reales del negocio, los usa el Asistente) ----------

    suspend fun registrarVenta(negocioId: String, ownerId: String, producto: String, cantidad: Int, total: Double, fecha: String): Result<VentaRemota> = safe {
        val doc = databases.createDocument(
            databaseId = DB,
            collectionId = AppwriteClient.COLECCION_VENTAS,
            documentId = ID.unique(),
            data = mapOf("negocioId" to negocioId, "producto" to producto, "cantidad" to cantidad, "total" to total, "fecha" to fecha),
            permissions = listOf(Permission.read(Role.users()), Permission.update(Role.user(ownerId)), Permission.delete(Role.user(ownerId)))
        )
        VentaRemota(doc.id, producto, cantidad, total, fecha)
    }

    suspend fun listarVentas(negocioId: String): Result<List<VentaRemota>> = safe {
        databases.listDocuments(
            DB, AppwriteClient.COLECCION_VENTAS,
            listOf(Query.equal("negocioId", negocioId), Query.orderDesc("\$createdAt"), Query.limit(200))
        ).documents.map {
            VentaRemota(
                id = it.id,
                producto = it.data["producto"] as? String ?: "",
                cantidad = (it.data["cantidad"] as? Number)?.toInt() ?: 0,
                total = (it.data["total"] as? Number)?.toDouble() ?: 0.0,
                fecha = it.data["fecha"] as? String ?: ""
            )
        }
    }

    suspend fun listarInventario(negocioId: String): Result<List<ProductoInventarioRemoto>> = safe {
        databases.listDocuments(
            DB, AppwriteClient.COLECCION_INVENTARIO,
            listOf(Query.equal("negocioId", negocioId), Query.limit(200))
        ).documents.map {
            ProductoInventarioRemoto(
                id = it.id,
                nombre = it.data["nombre"] as? String ?: "",
                stock = (it.data["stock"] as? Number)?.toInt() ?: 0
            )
        }
    }

    /** Crea el producto si no existe (por nombre) o actualiza su stock si ya existe. */
    suspend fun guardarProductoInventario(negocioId: String, ownerId: String, nombre: String, stock: Int): Result<ProductoInventarioRemoto> = safe {
        val existentes = databases.listDocuments(
            DB, AppwriteClient.COLECCION_INVENTARIO,
            listOf(Query.equal("negocioId", negocioId), Query.equal("nombre", nombre), Query.limit(1))
        ).documents
        val doc = if (existentes.isNotEmpty()) {
            databases.updateDocument(DB, AppwriteClient.COLECCION_INVENTARIO, existentes.first().id, mapOf("stock" to stock))
        } else {
            databases.createDocument(
                databaseId = DB,
                collectionId = AppwriteClient.COLECCION_INVENTARIO,
                documentId = ID.unique(),
                data = mapOf("negocioId" to negocioId, "nombre" to nombre, "stock" to stock),
                permissions = listOf(Permission.read(Role.users()), Permission.update(Role.user(ownerId)), Permission.delete(Role.user(ownerId)))
            )
        }
        ProductoInventarioRemoto(doc.id, nombre, stock)
    }

    // ---------- helpers ----------

    private suspend fun <T> safe(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val aw = e as? AppwriteException
        Log.e(TAG, "FALLO ${e::class.simpleName} code=${aw?.code} type=${aw?.type} msg=${e.message}", e)
        Result.failure(IllegalStateException(e.toMensaje()))
    }

    private fun Exception.toMensaje(): String = when (this) {
        is AppwriteException -> when (type) {
            "user_unauthorized", "general_unauthorized_scope" ->
                "Sin permisos en la base de datos. Revisa Settings > Permissions en Appwrite."
            "general_rate_limit_exceeded" -> "Demasiados intentos. Espera un momento."
            "collection_not_found", "database_not_found" ->
                "Falta crear la coleccion en Appwrite. Revisa el README."
            else -> message ?: "Error del servidor (código $code)."
        }
        is IOException -> "Sin conexión a internet."
        else -> message ?: "Ocurrió un error inesperado."
    }
}
