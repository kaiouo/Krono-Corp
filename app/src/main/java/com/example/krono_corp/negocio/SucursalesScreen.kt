package com.example.krono_corp.negocio

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.krono_corp.data.SucursalesRepository
import kotlinx.coroutines.launch

@Composable
fun SucursalesScreen(
    navController: NavController,
    negocioData: NegocioData,
    negocioId: String,
    // El supervisor solo puede VER las sucursales; el dueño y el gerente pueden agregar/editar/eliminar.
    puedeEditar: Boolean = true,
    // true  = comportamiento anterior (flecha, ajustes, barra inferior del negocio)
    // false = pantalla embebida (la usa el GERENTE dentro de AdminScreen, que ya tiene sus barras)
    mostrarBarras: Boolean = true,
    // Solo para el SUPERVISOR: null = no mostrar nada; "" = aún sin sucursal;
    // cualquier otro valor = ID (o nombre) de la sucursal que le asignó su gerente.
    sucursalAsignada: String? = null
) {
    val sucursales = remember { mutableStateListOf<SucursalItem>() }
    var cargando by remember { mutableStateOf(true) }
    var guardando by remember { mutableStateOf(false) }
    var textoBusqueda by remember { mutableStateOf("") }

    var mostrarDialogo by remember { mutableStateOf(false) }
    var nombreNuevaSucursal by remember { mutableStateOf("") }
    var codigoSucursalDialogo by remember { mutableStateOf("") }
    var estaActivaDialogo by remember { mutableStateOf(true) }
    var idEditando by remember { mutableStateOf<String?>(null) }
    var porEliminar by remember { mutableStateOf<SucursalItem?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun avisar(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    // Mantiene NegocioData al día para las otras pantallas
    fun sincronizar() {
        negocioData.sucursales.clear()
        negocioData.sucursales.addAll(sucursales)
    }

    fun abrirDialogo(s: SucursalItem?) {
        nombreNuevaSucursal = s?.nombre ?: ""
        codigoSucursalDialogo = s?.codigoAcceso ?: ""
        estaActivaDialogo = s?.activa ?: true
        idEditando = s?.id
        mostrarDialogo = true
    }

    fun guardarDialogo() {
        val nombre = nombreNuevaSucursal.trim()
        if (nombre.isEmpty() || guardando) return
        val duplicada = sucursales.any {
            it.id != idEditando && it.nombre.equals(nombre, ignoreCase = true)
        }
        if (duplicada) {
            avisar("Ya existe una sucursal con ese nombre.")
            return
        }
        val codigo = codigoSucursalDialogo.trim().ifEmpty { "1234" }
        val editando = idEditando
        scope.launch {
            guardando = true
            if (editando == null) {
                SucursalesRepository.crear(negocioId, nombre, estaActivaDialogo, codigo)
                    .onSuccess {
                        sucursales.add(it)
                        sincronizar()
                        mostrarDialogo = false
                    }
                    .onFailure { avisar(it.message ?: "No se pudo guardar la sucursal.") }
            } else {
                SucursalesRepository.actualizar(
                    SucursalItem(editando, nombre, estaActivaDialogo, codigo)
                )
                    .onSuccess { nueva ->
                        val i = sucursales.indexOfFirst { it.id == nueva.id }
                        if (i >= 0) sucursales[i] = nueva
                        sincronizar()
                        mostrarDialogo = false
                    }
                    .onFailure { avisar(it.message ?: "No se pudo actualizar la sucursal.") }
            }
            guardando = false
        }
    }

    fun eliminar(s: SucursalItem) {
        scope.launch {
            SucursalesRepository.eliminar(s.id)
                .onSuccess {
                    sucursales.removeAll { it.id == s.id }
                    sincronizar()
                }
                .onFailure { avisar(it.message ?: "No se pudo eliminar la sucursal.") }
        }
    }

    // Carga inicial desde Appwrite
    LaunchedEffect(negocioId) {
        SucursalesRepository.listar(negocioId)
            .onSuccess { lista ->
                sucursales.clear()
                sucursales.addAll(lista)
                sincronizar()
            }
            .onFailure { avisar(it.message ?: "No se pudieron cargar las sucursales.") }
        cargando = false
    }

    val filtradas = sucursales.filter { it.nombre.contains(textoBusqueda, ignoreCase = true) }
    val activas = sucursales.count { it.activa }
    val asignada = sucursalAsignada?.let { v ->
        sucursales.firstOrNull { it.id == v || it.nombre.equals(v, ignoreCase = true) }
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            if (mostrarBarras) {
                KronoBottomBar(navController = navController, currentRoute = Screen.Sucursales.route)
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            if (mostrarBarras) {
                item {
                    // Barra superior original, sin emoji de edificio
                    KronoTopAvatarBar(
                        letraAvatar = "S",
                        onBackClick = {
                            if (!navController.popBackStack()) {
                                navController.navigate(Screen.Home.route)
                            }
                        },
                        onGearClick = { navController.navigate(Screen.Configuracion.route) }
                    )
                }
            }

            // ---------- Encabezado ----------
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Administración de Sucursales",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KronoTextDark,
                        lineHeight = 28.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (sucursales.isEmpty()) "Aún no hay sucursales"
                        else "${sucursales.size} en total · $activas activas",
                        fontSize = 13.sp,
                        color = KronoTextMuted
                    )
                }
            }

            // ---------- Sucursal asignada por el gerente (solo supervisor) ----------
            if (sucursalAsignada != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEBF3FF)),
                        border = BorderStroke(1.dp, Color(0xFFBFD7FF))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "TU SUCURSAL ASIGNADA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = KronoBlue
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = when {
                                    asignada != null -> asignada.nombre
                                    cargando -> "Cargando…"
                                    else -> "Aún sin asignar"
                                },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KronoTextDark
                            )
                            if (asignada == null && !cargando) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Tu gerente todavía no te ha asignado una sucursal.",
                                    fontSize = 12.sp,
                                    color = KronoTextMuted
                                )
                            }
                        }
                    }
                }
            }

            // ---------- Botón agregar (uno solo) ----------
            if (puedeEditar) {
                item {
                    Button(
                        onClick = { abrirDialogo(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KronoBlue)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar sucursal", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // ---------- Búsqueda ----------
            item {
                OutlinedTextField(
                    value = textoBusqueda,
                    onValueChange = { textoBusqueda = it },
                    placeholder = { Text("Buscar sucursales", color = KronoTextMuted, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = KronoTextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = KronoSurfaceVariant,
                        focusedContainerColor = Color.White,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = KronoBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (cargando) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = KronoBlue
                    )
                }
            }

            if (!cargando && filtradas.isEmpty()) {
                item {
                    Text(
                        text = if (sucursales.isEmpty())
                            "Todavía no has creado sucursales. Usa «Agregar sucursal» para crear la primera."
                        else "No hay sucursales que coincidan con la búsqueda.",
                        fontSize = 13.sp,
                        color = KronoTextMuted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // ---------- Lista ----------
            items(filtradas, key = { it.id }) { sucursal ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEBF3FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Storefront,
                                contentDescription = null,
                                tint = KronoBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sucursal.nombre,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = KronoTextDark,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (sucursal.activa) KronoGreenBadge else Color(0xFFFFECEC)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (sucursal.activa) "ACTIVA" else "INACTIVA",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sucursal.activa) KronoGreenText else Color(0xFFEB5757)
                                    )
                                }
                                if (asignada != null && sucursal.id == asignada.id) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFFEBF3FF))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "TU SUCURSAL",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = KronoBlue
                                        )
                                    }
                                }
                                if (puedeEditar) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Código: ${sucursal.codigoAcceso}",
                                        fontSize = 11.sp,
                                        color = KronoTextMuted
                                    )
                                }
                            }
                        }

                        if (puedeEditar) {
                            IconButton(onClick = { abrirDialogo(sucursal) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar",
                                    tint = KronoTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { porEliminar = sucursal }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Eliminar",
                                    tint = Color(0xFFEB5757),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (!puedeEditar) {
                item {
                    Text(
                        "Solo tu gerente puede agregar, editar o eliminar sucursales.",
                        fontSize = 12.sp,
                        color = KronoTextMuted
                    )
                }
            }
        }
    }

    // ---------- Diálogo crear / editar ----------
    if (mostrarDialogo) {
        AlertDialog(
            onDismissRequest = { if (!guardando) mostrarDialogo = false },
            title = {
                Text(
                    text = if (idEditando == null) "Nueva sucursal" else "Editar sucursal",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nombreNuevaSucursal,
                        onValueChange = { nombreNuevaSucursal = it },
                        label = { Text("Nombre de la sucursal") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = codigoSucursalDialogo,
                        onValueChange = { codigoSucursalDialogo = it },
                        label = { Text("Código de acceso") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sucursal activa", fontSize = 14.sp)
                        Switch(
                            checked = estaActivaDialogo,
                            onCheckedChange = { estaActivaDialogo = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { guardarDialogo() },
                    enabled = !guardando,
                    colors = ButtonDefaults.buttonColors(containerColor = KronoBlue)
                ) {
                    Text(if (idEditando == null) "Guardar" else "Actualizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogo = false }, enabled = !guardando) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ---------- Confirmar eliminación ----------
    porEliminar?.let { s ->
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            title = { Text("¿Eliminar ${s.nombre}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("La sucursal dejará de aparecer en Evaluación. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(onClick = {
                    porEliminar = null
                    eliminar(s)
                }) { Text("Eliminar", color = Color(0xFFEB5757)) }
            },
            dismissButton = {
                TextButton(onClick = { porEliminar = null }) { Text("Cancelar") }
            }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SucursalesScreenPreview() {
    MaterialTheme {
        SucursalesScreen(
            navController = rememberNavController(),
            negocioData = NegocioData(),
            negocioId = "preview"
        )
    }
}