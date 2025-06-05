package com.example.myapp.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcelable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myapp.MainActivity
import com.example.myapp.api.ApiClient
import com.example.myapp.api.HealthTip
import com.example.myapp.api.WeatherData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import com.example.myapp.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.Manifest

@Parcelize
data class FluidEntry(
    val amount: Float,
    val unit: String,
    val drinkType: String,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

data class DrinkInfo(
    val name: String,
    val caloriesPer100ml: Float,
    val hydrationFactor: Float // 1.0 = stejná hydratace jako voda, 0.5 = poloviční hydratace
)

private val drinkInfoMap = mapOf(
    "Voda" to DrinkInfo("Voda", 0f, 1.0f),
    "Čaj" to DrinkInfo("Čaj", 1f, 0.9f),
    "Káva" to DrinkInfo("Káva", 2f, 0.8f),
    "Mléko" to DrinkInfo("Mléko", 60f, 0.9f),
    "Džus" to DrinkInfo("Džus", 45f, 0.7f),
    "Soda" to DrinkInfo("Soda", 40f, 0.6f),
    "Energetický nápoj" to DrinkInfo("Energetický nápoj", 45f, 0.5f),
    "Alkoholický nápoj" to DrinkInfo("Alkoholický nápoj", 70f, 0.3f),
    "Sportovní nápoj" to DrinkInfo("Sportovní nápoj", 30f, 0.8f),
    "Koktejl" to DrinkInfo("Koktejl", 150f, 0.4f)
)

// Seznam běžných nápojů
private val commonDrinks = listOf(
    "Voda",
    "Čaj",
    "Káva",
    "Mléko",
    "Džus",
    "Soda",
    "Energetický nápoj",
    "Alkoholický nápoj",
    "Sportovní nápoj",
    "Koktejl"
)

@Composable
fun WaterGlass(
    currentAmount: Float,
    targetAmount: Float,
    modifier: Modifier = Modifier
) {
    val progress = (currentAmount / targetAmount).coerceIn(0f, 1f)
    val glassColor = Color(0xFFE0E0E0)
    val waterColor = Color(0xFF2196F3)
    
    Canvas(modifier = modifier.size(150.dp)) {
        val width = size.width
        val height = size.height
        
        // Draw glass outline
        val glassPath = Path().apply {
            moveTo(width * 0.2f, height * 0.1f)
            lineTo(width * 0.2f, height * 0.9f)
            lineTo(width * 0.4f, height)
            lineTo(width * 0.6f, height)
            lineTo(width * 0.8f, height * 0.9f)
            lineTo(width * 0.8f, height * 0.1f)
            close()
        }
        
        // Draw water
        val waterHeight = height * 0.8f * progress
        val waterPath = Path().apply {
            moveTo(width * 0.2f, height * 0.9f - waterHeight)
            lineTo(width * 0.8f, height * 0.9f - waterHeight)
            lineTo(width * 0.8f, height * 0.9f)
            lineTo(width * 0.6f, height)
            lineTo(width * 0.4f, height)
            lineTo(width * 0.2f, height * 0.9f)
            close()
        }
        
        drawPath(waterPath, waterColor)
        drawPath(glassPath, glassColor, style = Stroke(width = 4f))
    }
}

@Composable
fun FluidIntakeScreen(
    onLogout: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var selectedDrinkType by remember { mutableStateOf("Voda") }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDrinkTypeDropdown by remember { mutableStateOf(false) }
    var showWeather by remember { mutableStateOf(false) }
    var weatherData by remember { mutableStateOf<WeatherData?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val entries = remember {
        val prefs = context.getSharedPreferences("fluid_intake", Context.MODE_PRIVATE)
        val gson = Gson()
        val type = object : TypeToken<List<FluidEntry>>() {}.type
        val savedEntries = prefs.getString("entries", "[]")
        mutableStateListOf<FluidEntry>().apply {
            addAll(gson.fromJson(savedEntries, type))
        }
    }
    
    // Save data when entries change
    LaunchedEffect(entries.size) {
        val prefs = context.getSharedPreferences("fluid_intake", Context.MODE_PRIVATE)
        val gson = Gson()
        prefs.edit().putString("entries", gson.toJson(entries.toList())).apply()
    }
    
    val targetAmount = remember {
        val prefs = context.getSharedPreferences("fluid_intake", Context.MODE_PRIVATE)
        prefs.getFloat("daily_goal", 2000f)
    }
    
    var currentAmount by remember { mutableStateOf(0f) }
    var totalCalories by remember { mutableStateOf(0f) }
    var totalHydration by remember { mutableStateOf(0f) }
    
    // Update currentAmount and totals when entries change
    LaunchedEffect(entries.size) {
        currentAmount = entries.sumOf { it.amount.toDouble() }.toFloat()
        totalCalories = entries.sumOf { entry ->
            val info = drinkInfoMap[entry.drinkType] ?: drinkInfoMap["Voda"]!!
            (entry.amount * info.caloriesPer100ml / 100).toDouble()
        }.toFloat()
        totalHydration = entries.sumOf { entry ->
            val info = drinkInfoMap[entry.drinkType] ?: drinkInfoMap["Voda"]!!
            (entry.amount * info.hydrationFactor).toDouble()
        }.toFloat()
    }
    
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showTimePicker = true
        }
    }
    
    val bluetoothManager = remember { BluetoothManager(context) }
    val isScanning by bluetoothManager.isScanning.collectAsState()
    val devices by bluetoothManager.devices.collectAsState()
    val connectedDevice by bluetoothManager.connectedDevice.collectAsState()
    val waterIntake by bluetoothManager.waterIntake.collectAsState()
    
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            bluetoothManager.startScan()
        }
    }
    
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            val response = ApiClient.weatherApi.getCurrentWeather()
            weatherData = response
        } catch (e: Exception) {
            error = "Chyba při načítání počasí: ${e.message}\nKód chyby: ${(e as? retrofit2.HttpException)?.code() ?: "Neznámý"}\nDetail: ${e.localizedMessage}"
        } finally {
            isLoading = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sledování příjmu tekutin",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Odhlásit se",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Progress section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WaterGlass(
                    currentAmount = currentAmount,
                    targetAmount = targetAmount,
                    modifier = Modifier.size(150.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "${currentAmount.toInt()} / ${targetAmount.toInt()} ml",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Hydration progress
                LinearProgressIndicator(
                    progress = (totalHydration / targetAmount).coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Hydratace: ${(totalHydration / targetAmount * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Kalorie: ${totalCalories.toInt()} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Bluetooth section
                if (!bluetoothManager.isBluetoothSupported()) {
                    Text(
                        text = "Bluetooth není podporován na tomto zařízení",
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (!bluetoothManager.isBluetoothEnabled()) {
                    Button(
                        onClick = {
                            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Zapnout Bluetooth")
                    }
                } else if (!bluetoothManager.hasRequiredPermissions()) {
                    Button(
                        onClick = {
                            bluetoothPermissionLauncher.launch(arrayOf(
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Povolit Bluetooth oprávnění")
                    }
                } else {
                    if (connectedDevice == null) {
                        Button(
                            onClick = { bluetoothManager.startScan() },
                            enabled = !isScanning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Text(if (isScanning) "Vyhledávání..." else "Hledat zařízení")
                        }
                        
                        if (isScanning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        LazyColumn {
                            items(devices) { device ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(device.name ?: "Neznámé zařízení")
                                            Text(
                                                device.address,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Button(
                                            onClick = { bluetoothManager.connectToDevice(device) }
                                        ) {
                                            Text("Připojit")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Připojeno k: ${connectedDevice?.name ?: "Neznámé zařízení"}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Příjem z chytré láhve: ${waterIntake.toInt()} ml",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { bluetoothManager.disconnectDevice() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Odpojit")
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Input section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Množství (ml)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Box {
                        Button(
                            onClick = { showDrinkTypeDropdown = true },
                            modifier = Modifier.width(120.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(selectedDrinkType)
                        }
                        
                        DropdownMenu(
                            expanded = showDrinkTypeDropdown,
                            onDismissRequest = { showDrinkTypeDropdown = false }
                        ) {
                            commonDrinks.forEach { drink ->
                                DropdownMenuItem(
                                    text = { Text(drink) },
                                    onClick = {
                                        selectedDrinkType = drink
                                        showDrinkTypeDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    
                    Button(
                        onClick = {
                            amount.toFloatOrNull()?.let { value ->
                                entries.add(FluidEntry(value, "ml", selectedDrinkType))
                                amount = ""
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Přidat")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Weather and reminder section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        when {
                            ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED -> {
                                showTimePicker = true
                            }
                            else -> {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    } else {
                        showTimePicker = true
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Připomenutí")
            }
            
            Button(
                onClick = { showWeather = !showWeather },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(if (showWeather) "Skrýt počasí" else "Počasí")
            }
        }
        
        if (showTimePicker) {
            TimePickerDialog(
                onDismissRequest = { showTimePicker = false },
                onTimeSelected = { hours, minutes ->
                    showTimePicker = false
                    scheduleNotification(context, hours, minutes)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (showWeather) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.CenterHorizontally)
                )
            } else if (error != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Chyba při načítání počasí",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (weatherData != null) {
                val currentForecast = weatherData!!.list.firstOrNull()
                val city = weatherData!!.city
                
                if (currentForecast != null) {
                    val temp = currentForecast.main.temp
                    val humidity = currentForecast.main.humidity
                    val description = currentForecast.weather.firstOrNull()?.description ?: ""
                    val time = currentForecast.dt_txt
                    
                    val recommendation = when {
                        temp > 30 -> "V Liberci je velmi horko! Pijte o 50% více než obvykle a vyhněte se fyzické aktivitě v poledních hodinách."
                        temp > 25 -> "V Liberci je teplo, pijte o 25% více než obvykle. Nezapomeňte na pravidelný příjem tekutin."
                        temp < 10 -> "V Liberci je chladno, ale stále je důležité pít dostatek tekutin. Teplé nápoje jsou vhodnou volbou."
                        else -> "Dodržujte svůj běžný pitný režim. V Liberci je příjemná teplota."
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Předpověď počasí pro ${city.name}, ${city.country}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Čas: ${time}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Teplota: ${String.format("%.1f", temp)}°C",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Vlhkost: ${humidity}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Stav: ${description.capitalize()}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = recommendation,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(entries) { entry ->
                val drinkInfo = drinkInfoMap[entry.drinkType] ?: drinkInfoMap["Voda"]!!
                val entryCalories = entry.amount * drinkInfo.caloriesPer100ml / 100
                val entryHydration = entry.amount * drinkInfo.hydrationFactor
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${entry.amount} ${entry.unit}")
                            Text(
                                entry.drinkType,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${entryCalories.toInt()} kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Hydratace: ${(entryHydration / entry.amount * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                LocalDateTime.ofEpochSecond(entry.timestamp / 1000, 0, java.time.ZoneOffset.UTC)
                                    .format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = MaterialTheme.typography.bodySmall
                            )
                            IconButton(
                                onClick = { entries.remove(entry) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Smazat záznam"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimePickerDialog(
    onDismissRequest: () -> Unit,
    onTimeSelected: (hours: Int, minutes: Int) -> Unit
) {
    var hours by remember { mutableStateOf(0) }
    var minutes by remember { mutableStateOf(0) }
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Nastavit připomenutí") },
        text = {
            Column {
                Text("Za kolik hodin:")
                Slider(
                    value = hours.toFloat(),
                    onValueChange = { hours = it.toInt() },
                    valueRange = 0f..24f,
                    steps = 23
                )
                Text("$hours hodin")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Za kolik minut:")
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt() },
                    valueRange = 0f..59f,
                    steps = 58
                )
                Text("$minutes minut")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onTimeSelected(hours, minutes) }
            ) {
                Text("Nastavit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Zrušit")
            }
        }
    )
}

private fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Připomenutí pití"
        val descriptionText = "Připomenutí pro pití vody"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("drink_reminder", name, importance).apply {
            description = descriptionText
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

private fun scheduleNotification(context: Context, hours: Int, minutes: Int) {
    createNotificationChannel(context)
    
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE
    )
    
    val notification = NotificationCompat.Builder(context, "drink_reminder")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Je čas se napít!")
        .setContentText("Nezapomeňte na pravidelný příjem tekutin.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()
    
    val delayMillis = TimeUnit.HOURS.toMillis(hours.toLong()) + TimeUnit.MINUTES.toMillis(minutes.toLong())
    
    android.os.Handler(context.mainLooper).postDelayed({
        notificationManager.notify(1, notification)
    }, delayMillis)
} 