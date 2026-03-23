package com.example.mtlogger

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.mtlogger.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    
    // States for sensor data
    private var accelData by mutableStateOf(floatArrayOf(0f, 0f, 0f))
    private var gyroData by mutableStateOf(floatArrayOf(0f, 0f, 0f))
    private var magData by mutableStateOf(floatArrayOf(0f, 0f, 0f))
    private var pressure by mutableStateOf(0f)
    private var stepCount by mutableStateOf(0f)

    // Activity states for badges
    private var accelActive by mutableStateOf(false)
    private var gyroActive by mutableStateOf(false)
    private var magActive by mutableStateOf(false)
    private var pressureActive by mutableStateOf(false)
    private var stepActive by mutableStateOf(false)

    private var isLogging by mutableStateOf(false)
    private var samplingRate by mutableStateOf(SensorManager.SENSOR_DELAY_UI)
    private var sampleCount by mutableStateOf(0)
    private var logStartTime by mutableStateOf(0L)
    private var elapsedTime by mutableStateOf(0L)
    
    // Temporary storage for log data
    private val logBuffer = mutableListOf<String>()
    private var lastTimestamp = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        enableEdgeToEdge()
        setContent {
            MTLoggerTheme(darkTheme = true) {
                PermissionRequester {
                    SensorLoggerScreen(
                        accel = accelData,
                        gyro = gyroData,
                        mag = magData,
                        pressure = pressure,
                        stepCount = stepCount,
                        accelActive = accelActive,
                        gyroActive = gyroActive,
                        magActive = magActive,
                        pressureActive = pressureActive,
                        stepActive = stepActive,
                        isLogging = isLogging,
                        sampleCount = sampleCount,
                        elapsedTime = elapsedTime,
                        currentRate = samplingRate,
                        onRateChange = { newRate ->
                            samplingRate = newRate
                            registerSensors()
                        },
                        onToggleLogging = { toggleLogging() },
                        onSaveFile = { saveLogFile() }
                    )
                }
            }
        }
    }

    private fun toggleLogging() {
        if (isLogging) {
            isLogging = false
        } else {
            logBuffer.clear()
            logBuffer.add("timestamp,acc_x,acc_y,acc_z,gyr_x,gyr_y,gyr_z,mag_x,mag_y,mag_z,pressure,step_count\n")
            sampleCount = 0
            logStartTime = System.currentTimeMillis()
            elapsedTime = 0L
            lastTimestamp = 0L
            isLogging = true
        }
    }

    private fun saveLogFile() {
        if (logBuffer.isEmpty() || logBuffer.size <= 1) {
            Toast.makeText(this, "Nenhum dado para salvar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val timeStamp = SimpleDateFormat("HH_mm_ss_dd_MM_yyyy", Locale.getDefault()).format(Date())
            val fileName = "$timeStamp.csv"
            var outputStream: OutputStream? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/MTLogger")
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (uri != null) {
                    outputStream = resolver.openOutputStream(uri)
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "MTLogger")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                outputStream = FileOutputStream(file)
            }

            outputStream?.use { stream ->
                logBuffer.forEach { line ->
                    stream.write(line.toByteArray())
                }
            }
            
            Toast.makeText(this, "Salvo em Documentos/MTLogger", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MTLogger", "Error saving log", e)
            Toast.makeText(this, "Erro ao salvar arquivo", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        registerSensors()
    }

    override fun onPause() {
        super.onPause()
        if (!isLogging) {
            sensorManager.unregisterListener(this)
            resetActiveStates()
        }
    }

    private fun resetActiveStates() {
        accelActive = false
        gyroActive = false
        magActive = false
        pressureActive = false
        stepActive = false
    }

    private fun registerSensors() {
        sensorManager.unregisterListener(this)
        resetActiveStates()
        val sensors = listOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_STEP_COUNTER
        )
        sensors.forEach { type ->
            sensorManager.getDefaultSensor(type)?.let { sensor ->
                sensorManager.registerListener(this, sensor, samplingRate)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val currentTime = System.currentTimeMillis()
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelData = event.values.clone()
                accelActive = true
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroData = event.values.clone()
                gyroActive = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magData = event.values.clone()
                magActive = true
            }
            Sensor.TYPE_PRESSURE -> {
                pressure = event.values[0]
                pressureActive = true
            }
            Sensor.TYPE_STEP_COUNTER -> {
                stepCount = event.values[0]
                stepActive = true
            }
        }

        if (isLogging) {
            val intervalMs = when (samplingRate) {
                SensorManager.SENSOR_DELAY_FASTEST -> 0L
                10000 -> 10L // 100Hz
                SensorManager.SENSOR_DELAY_GAME -> 20L // 50Hz
                SensorManager.SENSOR_DELAY_UI -> 66L // 15Hz
                SensorManager.SENSOR_DELAY_NORMAL -> 200L // 5Hz
                else -> 0L
            }

            if (currentTime - lastTimestamp >= intervalMs) {
                sampleCount++
                elapsedTime = currentTime - logStartTime
                lastTimestamp = currentTime
                
                // Construct one row with all current sensor values
                val line = String.format(
                    Locale.US,
                    "%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.4f,%.0f\n",
                    currentTime,
                    accelData[0], accelData[1], accelData[2],
                    gyroData[0], gyroData[1], gyroData[2],
                    magData[0], magData[1], magData[2],
                    pressure,
                    stepCount
                )
                logBuffer.add(line)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun PermissionRequester(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val permissions = mutableListOf(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    var permissionsGranted by remember { 
        mutableStateOf(permissions.all { 
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
        }) 
    }
    
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        permissionsGranted = results.values.all { it }
    }
    
    LaunchedEffect(Unit) { 
        if (!permissionsGranted) launcher.launch(permissions.toTypedArray()) 
    }
    
    if (permissionsGranted) content()
}

@Composable
fun SensorLoggerScreen(
    accel: FloatArray,
    gyro: FloatArray,
    mag: FloatArray,
    pressure: Float,
    stepCount: Float,
    accelActive: Boolean,
    gyroActive: Boolean,
    magActive: Boolean,
    pressureActive: Boolean,
    stepActive: Boolean,
    isLogging: Boolean,
    sampleCount: Int,
    elapsedTime: Long,
    currentRate: Int,
    onRateChange: (Int) -> Unit,
    onToggleLogging: () -> Unit,
    onSaveFile: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(48.dp))
            HeaderSection(accelActive, gyroActive, magActive, pressureActive, stepActive)
            Spacer(modifier = Modifier.height(24.dp))
            SamplingRateSelector(currentRate, onRateChange)
            Spacer(modifier = Modifier.height(16.dp))
            RecordingCard(isLogging, sampleCount, elapsedTime, onToggleLogging, onSaveFile)
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SensorDataCard("ACELERÔMETRO", "m/s²", accel, AccentBlue) }
                item { SensorDataCard("GIROSCÓPIO", "rad/s", gyro, AccentGreen) }
                item { SensorDataCard("MAGNETÔMETRO", "µT", mag, AccentPurple) }
                item { 
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            SingleSensorCard("BARÔMETRO", "hPa", pressure, AccentRed)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            SingleSensorCard("PEDÔMETRO", "passos", stepCount, Color.Yellow)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun SamplingRateSelector(currentRate: Int, onRateChange: (Int) -> Unit) {
    val rates = listOf(
        "Max" to SensorManager.SENSOR_DELAY_FASTEST,
        "100Hz" to 10000,
        "50Hz" to SensorManager.SENSOR_DELAY_GAME,
        "15Hz" to SensorManager.SENSOR_DELAY_UI,
        "5Hz" to SensorManager.SENSOR_DELAY_NORMAL
    )

    Column {
        Text("Taxa de Amostragem", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardBg)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            rates.forEach { (label, rate) ->
                val isSelected = currentRate == rate
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color(0xFF1E293B) else Color.Transparent)
                        .clickable { onRateChange(rate) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderSection(
    accelActive: Boolean,
    gyroActive: Boolean,
    magActive: Boolean,
    pressureActive: Boolean,
    stepActive: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardBg),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.meu_logo),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("MTLogger", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("IMU + Baro", color = TextSecondary, fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge("A", if (accelActive) AccentGreen else CardBg, if (accelActive) TextPrimary else TextSecondary)
            StatusBadge("G", if (gyroActive) AccentGreen else CardBg, if (gyroActive) TextPrimary else TextSecondary)
            StatusBadge("M", if (magActive) AccentGreen else CardBg, if (magActive) TextPrimary else TextSecondary)
            StatusBadge("B", if (pressureActive) AccentGreen else CardBg, if (pressureActive) TextPrimary else TextSecondary)
            StatusBadge("P", if (stepActive) AccentGreen else CardBg, if (stepActive) TextPrimary else TextSecondary)
        }
    }
}

@Composable
fun StatusBadge(label: String, bgColor: Color, textColor: Color = TextPrimary) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (bgColor == CardBg) bgColor else bgColor.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, 
            color = if (bgColor == CardBg) textColor else bgColor, 
            fontWeight = FontWeight.Bold, 
            fontSize = 12.sp
        )
    }
}

@Composable
fun RecordingCard(isLogging: Boolean, sampleCount: Int, elapsedTime: Long, onToggle: () -> Unit, onSave: () -> Unit) {
    val seconds = (elapsedTime / 1000) % 60
    val minutes = (elapsedTime / (1000 * 60)) % 60
    val timeString = String.format("%02d:%02d", minutes, seconds)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (isLogging) "Gravando..." else "Pronto", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("$timeString   $sampleCount amostras", color = TextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.weight(3f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isLogging) AccentPurple else AccentRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(if (isLogging) Icons.Default.Stop else Icons.Default.PlayArrow, null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isLogging) "Parar" else "Gravar", color = Color.White, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = onSave,
                    modifier = Modifier.weight(1f).height(56.dp).background(Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                    enabled = !isLogging
                ) {
                    Icon(Icons.Default.Download, null, tint = if (isLogging) TextSecondary else TextPrimary)
                }
            }
        }
    }
}

@Composable
fun SensorDataCard(title: String, unit: String, values: FloatArray, accentColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(unit, color = TextSecondary, fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SensorValueColumn("X", values[0])
                SensorValueColumn("Y", values[1])
                SensorValueColumn("Z", values[2])
            }
        }
    }
}

@Composable
fun SingleSensorCard(title: String, unit: String, value: Float, accentColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (title == "PEDÔMETRO") String.format("%.0f", value) else String.format("%.2f", value),
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(unit, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
fun SensorValueColumn(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(String.format("%.2f", value), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
