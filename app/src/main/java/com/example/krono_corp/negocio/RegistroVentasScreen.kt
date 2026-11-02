package com.example.krono_corp.negocio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.krono_corp.data.NegocioRepository
import com.example.krono_corp.data.ProductoInventarioRemoto
import com.example.krono_corp.data.VentaRemota
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Registro real de ventas e inventario del negocio: lo que lee el Asistente para responder
// con datos reales en vez de datos de ejemplo.

private val formatoFechaVenta = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Composable
fun RegistroVentasScreen(
    navController: NavController,
    negocioData: NegocioData
) {
    var pestana by remember { mutableStateOf(0) } // 0 = ventas, 1 = inventario
    var ventas by remember { mutableStateOf<List<VentaRemota>>(emptyList()) }
    var inventario by remember { mutableStateOf<List<ProductoInventarioRemoto>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun recargar() {
        if (negocioData.id.isEmpty()) return
        NegocioRepository.listarVentas(negocioData.id).onSuccess { ventas = it }
        NegocioRepository.listarInventario(negocioData.id).onSuccess { inventario = it }
    }

    LaunchedEffect(negocioData.id) {
        cargando = true
        recargar()
        cargando = false
    }

    Scaffold(
        containerColor = KronoBackground,
        bottomBar = {
            KronoBottomBar(navController = navController, currentRoute = Screen.Ventas.route)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            KronoTopAvatarBar(
                letraAvatar = "💵",
                onBackClick = {
                    if (!navController.popBackStack()) navController.navigate(Screen.Home.route)
                },
                onGearClick = { navController.navigate(Screen.Configuracion.route) }
            )

            Text(
                text = "Ventas e inventario",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = KronoTextDark,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = "Esto es lo que el Asistente usa para responder sobre tu negocio",
                fontSize = 12.sp,
                color = KronoTextMuted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            TabRow(selectedTabIndex = pestana, modifier = Modifier.padding(top = 8.dp)) {
                Tab(selected = pestana == 0, onClick = { pestana = 0 }, text = { Text("Ventas") })
                Tab(selected = pestana == 1, onClick = { pestana = 1 }, text = { Text("Inventario") })
            }

            if (cargando) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KronoBlue)
                }
            } else if (pestana == 0) {
                PestanaVentas(
                    ventas = ventas,
                    onRegistrar = { producto, cantidad, total ->
                        scope.launch {
                            NegocioRepository.registrarVenta(
                                negocioId = negocioData.id,
                                ownerId = negocioData.ownerId,
                                producto = producto,
                                cantidad = cantidad,
                                total = total,
                                fecha = LocalDateTime.now().format(formatoFechaVenta)
                            )
                            recargar()
                        }
                    }
                )
            } else {
                PestanaInventario(
                    inventario = inventario,
                    onGuardar = { producto, stock ->
                        scope.launch {
                            NegocioRepository.guardarProductoInventario(
                                negocioId = negocioData.id,
                                ownerId = negocioData.ownerId,
                                nombre = producto,
                                stock = stock
                            )
                            recargar()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PestanaVentas(
    ventas: List<VentaRemota>,
    onRegistrar: (String, Int, Double) -> Unit
) {
    var producto by remember { mutableStateOf("") }
    var cantidad by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Registrar una venta", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = producto, onValueChange = { producto = it },
                        label = { Text("Producto") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cantidad, onValueChange = { cantidad = it.filter { c -> c.isDigit() } },
                            label = { Text("Cantidad") }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = total, onValueChange = { total = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Total (US$)") }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            val c = cantidad.toIntOrNull()
                            val t = total.toDoubleOrNull()
                            if (producto.isNotBlank() && c != null && c > 0 && t != null && t >= 0) {
                                onRegistrar(producto.trim(), c, t)
                                producto = ""; cantidad = ""; total = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = KronoBlue)
                    ) { Text("Registrar venta") }
                }
            }
        }

        item {
            Text("Ventas registradas", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }

        if (ventas.isEmpty()) {
            item { Text("Aún no hay ventas registradas.", color = KronoTextMuted) }
        }

        items(ventas, key = { it.id }) { venta ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.PointOfSale, contentDescription = null, tint = KronoBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${venta.producto} · ${venta.cantidad} unidades", fontSize = 13.sp, color = KronoTextDark)
                    Text(venta.fecha, fontSize = 11.sp, color = KronoTextMuted)
                }
                Text("$${"%.2f".format(venta.total)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PestanaInventario(
    inventario: List<ProductoInventarioRemoto>,
    onGuardar: (String, Int) -> Unit
) {
    var producto by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Agregar o actualizar producto", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = producto, onValueChange = { producto = it },
                        label = { Text("Producto") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = stock, onValueChange = { stock = it.filter { c -> c.isDigit() } },
                        label = { Text("Stock") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val s = stock.toIntOrNull()
                            if (producto.isNotBlank() && s != null && s >= 0) {
                                onGuardar(producto.trim(), s)
                                producto = ""; stock = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = KronoBlue)
                    ) { Text("Guardar") }
                }
            }
        }

        item {
            Text("Inventario actual", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }

        if (inventario.isEmpty()) {
            item { Text("Aún no hay productos en el inventario.", color = KronoTextMuted) }
        }

        items(inventario, key = { it.id }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = KronoBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(item.nombre, modifier = Modifier.weight(1f), fontSize = 13.sp, color = KronoTextDark)
                Text("${item.stock} unidades", fontSize = 13.sp, color = KronoTextMuted)
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun RegistroVentasScreenPreview() {
    MaterialTheme {
        RegistroVentasScreen(navController = rememberNavController(), negocioData = NegocioData())
    }
}
