package com.example.miniproyecto02


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.miniproyecto02.data.AppDatabase
import com.example.miniproyecto02.data.Tarea
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Filtro { TODAS, PENDIENTES, COMPLETADAS }
enum class Orden { FECHA, ESTADO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TareasScreen() {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).tareaDao() }
    val scope = rememberCoroutineScope()

    // Lista que viene de Room y se actualiza sola cuando cambia la base de datos
    val tareasFlow = remember { dao.obtenerTodas() }
    val tareas by tareasFlow.collectAsState(initial = emptyList())

    var filtro by remember { mutableStateOf(Filtro.TODAS) }
    var orden by remember { mutableStateOf(Orden.FECHA) }
    var menuAbierto by remember { mutableStateOf(false) }
    var mostrarDialogo by remember { mutableStateOf(false) }
    var tareaEnEdicion by remember { mutableStateOf<Tarea?>(null) }

    // Aplica filtro y orden elegidos en el menú
    val filtradas = tareas.filter {
        when (filtro) {
            Filtro.TODAS -> true
            Filtro.PENDIENTES -> !it.completada
            Filtro.COMPLETADAS -> it.completada
        }
    }
    val tareasVisibles = when (orden) {
        Orden.FECHA -> filtradas.sortedByDescending { it.fechaCreacion }
        Orden.ESTADO -> filtradas.sortedWith(
            compareBy<Tarea> { it.completada }.thenByDescending { it.fechaCreacion }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Tareas") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    // Acción directa en la barra: borrar completadas
                    IconButton(onClick = { scope.launch { dao.eliminarCompletadas() } }) {
                        Icon(Icons.Default.Delete, contentDescription = "Borrar completadas")
                    }
                    // Menú desplegable de tres puntos
                    Box {
                        IconButton(onClick = { menuAbierto = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menú")
                        }
                        DropdownMenu(
                            expanded = menuAbierto,
                            onDismissRequest = { menuAbierto = false }
                        ) {
                            Text(
                                "Filtrar",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            DropdownMenuItem(
                                text = { Text(if (filtro == Filtro.TODAS) "✓ Todas" else "Todas") },
                                onClick = { filtro = Filtro.TODAS; menuAbierto = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (filtro == Filtro.PENDIENTES) "✓ Pendientes" else "Pendientes") },
                                onClick = { filtro = Filtro.PENDIENTES; menuAbierto = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (filtro == Filtro.COMPLETADAS) "✓ Completadas" else "Completadas") },
                                onClick = { filtro = Filtro.COMPLETADAS; menuAbierto = false }
                            )
                            HorizontalDivider()
                            Text(
                                "Ordenar por",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            DropdownMenuItem(
                                text = { Text(if (orden == Orden.FECHA) "✓ Fecha" else "Fecha") },
                                onClick = { orden = Orden.FECHA; menuAbierto = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (orden == Orden.ESTADO) "✓ Estado" else "Estado") },
                                onClick = { orden = Orden.ESTADO; menuAbierto = false }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                tareaEnEdicion = null
                mostrarDialogo = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Agregar tarea")
            }
        }
    ) { innerPadding ->
        if (tareasVisibles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay tareas. Presiona + para agregar una.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tareasVisibles, key = { it.id }) { tarea ->
                    TareaItem(
                        tarea = tarea,
                        onToggle = {
                            scope.launch { dao.actualizar(tarea.copy(completada = !tarea.completada)) }
                        },
                        onEditar = {
                            tareaEnEdicion = tarea
                            mostrarDialogo = true
                        },
                        onEliminar = { scope.launch { dao.eliminar(tarea) } }
                    )
                }
            }
        }
    }

    // Diálogo para crear o editar
    if (mostrarDialogo) {
        TareaDialog(
            tarea = tareaEnEdicion,
            onDismiss = { mostrarDialogo = false },
            onGuardar = { titulo, descripcion ->
                scope.launch {
                    val actual = tareaEnEdicion
                    if (actual == null) {
                        dao.insertar(Tarea(titulo = titulo, descripcion = descripcion))
                    } else {
                        dao.actualizar(actual.copy(titulo = titulo, descripcion = descripcion))
                    }
                }
                mostrarDialogo = false
            }
        )
    }
}

@Composable
fun TareaItem(
    tarea: Tarea,
    onToggle: () -> Unit,
    onEditar: () -> Unit,
    onEliminar: () -> Unit
) {
    val formato = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = tarea.completada, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tarea.titulo,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (tarea.completada) TextDecoration.LineThrough else TextDecoration.None
                )
                if (tarea.descripcion.isNotBlank()) {
                    Text(text = tarea.descripcion, fontSize = 14.sp)
                }
                Text(
                    text = formato.format(Date(tarea.fechaCreacion)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = onEditar) {
                Icon(Icons.Default.Edit, contentDescription = "Editar")
            }
            IconButton(onClick = onEliminar) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar")
            }
        }
    }
}

@Composable
fun TareaDialog(
    tarea: Tarea?,
    onDismiss: () -> Unit,
    onGuardar: (String, String) -> Unit
) {
    var titulo by remember { mutableStateOf(tarea?.titulo ?: "") }
    var descripcion by remember { mutableStateOf(tarea?.descripcion ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tarea == null) "Nueva tarea" else "Editar tarea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text("Título") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onGuardar(titulo.trim(), descripcion.trim()) },
                enabled = titulo.isNotBlank()
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}