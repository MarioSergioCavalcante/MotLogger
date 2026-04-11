package com.example.mtlogger

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.mtlogger.ui.theme.AccentBlue
import com.example.mtlogger.ui.theme.AccentGreen
import com.example.mtlogger.ui.theme.AccentPurple
import com.example.mtlogger.ui.theme.AccentRed
import com.example.mtlogger.ui.theme.CardBg
import com.example.mtlogger.ui.theme.DarkBg
import com.example.mtlogger.ui.theme.MTLoggerTheme
import com.example.mtlogger.ui.theme.TextPrimary
import com.example.mtlogger.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

data class SensorSnapshot(
    val timestamp: Long,
    val acc: FloatArray,
    val gyr: FloatArray,
    val mag: FloatArray,
    val pressure: Float,
    val steps: Float,
    val light: Float,
    val proximity: Float
)

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // States for sensor data
    private var accelData by mutableStateOf(floatArrayOf(0f, 0f, 0f))
    private var gyroData by mutableStateOf(floatArrayOf(0f, 0f, 0f))
    private var magData by mutableStateOf(floatArrayOf(0f, 0f, 0f))
    private var pressure by mutableStateOf(0f)
    private var stepCount by mutableStateOf(0f)
    private var lightData by mutableStateOf(0f)
    private var proximityData by mutableStateOf(0f)

    // Activity states for badges (receiving data)
    private var accelActive by mutableStateOf(false)
    private var gyroActive by mutableStateOf(false)
    private var magActive by mutableStateOf(false)
    private var pressureActive by mutableStateOf(false)
    private var stepActive by mutableStateOf(false)
    private var lightActive by mutableStateOf(false)
    private var proximityActive by mutableStateOf(false)

    // Selection states (enabled for capture)
    private var accelEnabled by mutableStateOf(true)
    private var gyroEnabled by mutableStateOf(true)
    private var magEnabled by mutableStateOf(true)
    private var pressureEnabled by mutableStateOf(true)
    private var stepEnabled by mutableStateOf(true)
    private var lightEnabled by mutableStateOf(true)
    private var proximityEnabled by mutableStateOf(true)

    private var isLogging by mutableStateOf(false)
    private var hasRecordedData by mutableStateOf(false)
    private var samplingRate by mutableStateOf(100) // Default export rate: 100 Hz
    private var sampleCount by mutableStateOf(0)
    private var logStartTime by mutableStateOf(0L)
    private var elapsedTime by mutableStateOf(0L)

    // TAG state
    private var tag by mutableStateOf("")

    // Internal buffer for high-resolution data (Thread-safe)
    private val rawDataBuffer = Collections.synchronizedList(mutableListOf<SensorSnapshot>())
    private var lastCaptureTimestamp = 0L

    // Throttling for UI updates
    private var lastUIUpdateTimestamp = 0L
    private val UI_UPDATE_INTERVAL_MS = 100L // 10Hz UI updates

    // Dialog states
    private var showRateDialog by mutableStateOf(false)
    private var showExitDialog by mutableStateOf(false)
    private var showStopLoggingDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        startSensorThread()

        enableEdgeToEdge()
        setContent {
            MTLoggerTheme(darkTheme = true) {
                // Back button handler for exiting app
                BackHandler {
                    showExitDialog = true
                }

                PermissionRequester {
                    SensorLoggerScreen(
                        accel = accelData,
                        gyro = gyroData,
                        mag = magData,
                        pressure = pressure,
                        stepCount = stepCount,
                        light = lightData,
                        proximity = proximityData,
                        accelActive = accelActive,
                        gyroActive = gyroActive,
                        magActive = magActive,
                        pressureActive = pressureActive,
                        stepActive = stepActive,
                        lightActive = lightActive,
                        proximityActive = proximityActive,
                        accelEnabled = accelEnabled,
                        gyroEnabled = gyroEnabled,
                        magEnabled = magEnabled,
                        pressureEnabled = pressureEnabled,
                        stepEnabled = stepEnabled,
                        lightEnabled = lightEnabled,
                        proximityEnabled = proximityEnabled,
                        onToggleSensor = { sensorLabel ->
                            if (!isLogging) {
                                when (sensorLabel) {
                                    "A" -> accelEnabled = !accelEnabled
                                    "G" -> gyroEnabled = !gyroEnabled
                                    "M" -> magEnabled = !magEnabled
                                    "B" -> pressureEnabled = !pressureEnabled
                                    "P" -> stepEnabled = !stepEnabled
                                    "L" -> lightEnabled = !lightEnabled
                                    "Pr" -> proximityEnabled = !proximityEnabled
                                }
                                registerSensors(SensorManager.SENSOR_DELAY_UI)
                            }
                        },
                        isLogging = isLogging,
                        hasRecordedData = hasRecordedData,
                        sampleCount = sampleCount,
                        elapsedTime = elapsedTime,
                        tag = tag,
                        onTagChange = { tag = it },
                        showRateDialog = showRateDialog,
                        onShowRateDialogChange = { showRateDialog = it },
                        showExitDialog = showExitDialog,
                        onShowExitDialogChange = { showExitDialog = it },
                        showStopLoggingDialog = showStopLoggingDialog,
                        onShowStopLoggingDialogChange = { showStopLoggingDialog = it },
                        onExitApp = { finish() },
                        currentRate = samplingRate,
                        onRateChange = { newRate ->
                            samplingRate = newRate
                        },
                        onToggleLogging = {
                            if (isLogging) {
                                showStopLoggingDialog = true
                            } else {
                                // Start logging with a small delay to allow IME to hide safely
                                lifecycleScope.launch {
                                    // Give time for keyboard to hide if it was open
                                    delay(200)
                                    toggleLogging()
                                }
                            }
                        },
                        onConfirmStopLogging = {
                            showStopLoggingDialog = false
                            toggleLogging()
                        },
                        onSaveFile = { saveAndResampleLogFile() }
                    )
                }
            }
        }
    }

    private fun startSensorThread() {
        if (sensorThread == null) {
            sensorThread = HandlerThread("SensorThread").apply { start() }
            sensorHandler = Handler(sensorThread!!.looper)
        }
    }

    private fun toggleLogging() {
        if (isLogging) {
            isLogging = false
            hasRecordedData = true
            // Revert to UI sampling rate to save battery/CPU
            registerSensors(SensorManager.SENSOR_DELAY_UI)
        } else {
            rawDataBuffer.clear()
            sampleCount = 0
            logStartTime = System.currentTimeMillis()
            elapsedTime = 0L
            lastCaptureTimestamp = 0L
            hasRecordedData = false
            isLogging = true
            // Start high-speed sampling
            registerSensors(SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    private fun saveAndResampleLogFile() {
        if (rawDataBuffer.isEmpty()) {
            Toast.makeText(this, "Nenhum dado para salvar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intervalMs = when (samplingRate) {
                100 -> 10L // 100 Hz
                50 -> 20L  // 50 Hz
                20 -> 50L  // 20 Hz
                else -> 0L // Original (Max)
            }

            val timeStamp =
                SimpleDateFormat("HH_mm_ss_dd_MM_yyyy", Locale.getDefault()).format(Date())
            val cleanTag = tag.trim().replace("\\s+".toRegex(), "_")
            val prefix = if (cleanTag.isNotEmpty()) "${cleanTag}_" else ""
            val rateSuffix = if (samplingRate == 0) "MAX" else "${samplingRate}Hz"
            val fileName = "${prefix}${timeStamp}_${rateSuffix}.csv"
            var outputStream: OutputStream? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOCUMENTS + "/MTLogger"
                    )
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (uri != null) {
                    outputStream = resolver.openOutputStream(uri)
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "MTLogger"
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                outputStream = FileOutputStream(file)
            }

            outputStream?.use { stream ->
                // Dynamically build header based on enabled sensors
                val headerParts = mutableListOf("timestamp", "seconds")
                if (accelEnabled) headerParts.addAll(listOf("acc_x", "acc_y", "acc_z"))
                if (gyroEnabled) headerParts.addAll(listOf("gyr_x", "gyr_y", "gyr_z"))
                if (magEnabled) headerParts.addAll(listOf("mag_x", "mag_y", "mag_z"))
                if (pressureEnabled) headerParts.add("pressure")
                if (stepEnabled) headerParts.add("step_count")
                if (lightEnabled) headerParts.add("light")
                if (proximityEnabled) headerParts.add("proximity")

                stream.write((headerParts.joinToString(",") + "\n").toByteArray())

                // Copy buffer to avoid ConcurrentModificationException
                val bufferCopy = rawDataBuffer.toList()
                val actualStartTime = bufferCopy.first().timestamp

                if (intervalMs == 0L) {
                    bufferCopy.forEach { snap ->
                        stream.write(formatSnapshotDynamic(snap, actualStartTime).toByteArray())
                    }
                } else {
                    val endTime = bufferCopy.last().timestamp
                    var currentTargetTime = actualStartTime
                    var bufferIndex = 0

                    while (currentTargetTime <= endTime) {
                        while (bufferIndex < bufferCopy.size - 1 &&
                            bufferCopy[bufferIndex + 1].timestamp < currentTargetTime
                        ) {
                            bufferIndex++
                        }

                        val selectedSnap = if (bufferIndex < bufferCopy.size - 1) {
                            val d1 = currentTargetTime - bufferCopy[bufferIndex].timestamp
                            val d2 = bufferCopy[bufferIndex + 1].timestamp - currentTargetTime
                            if (d2 < d1) bufferCopy[bufferIndex + 1] else bufferCopy[bufferIndex]
                        } else {
                            bufferCopy[bufferIndex]
                        }

                        val resampledLine =
                            formatSnapshotDynamicWithTime(selectedSnap, currentTargetTime, actualStartTime)
                        stream.write(resampledLine.toByteArray())

                        currentTargetTime += intervalMs
                    }
                }
            }

            Toast.makeText(this, "Salvo em Documentos/MTLogger", Toast.LENGTH_SHORT).show()
            hasRecordedData = false
            registerSensors(SensorManager.SENSOR_DELAY_UI)
        } catch (e: Exception) {
            Log.e("MTLogger", "Error saving log", e)
            Toast.makeText(this, "Erro ao salvar arquivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatSnapshotDynamic(snap: SensorSnapshot, startTime: Long): String {
        return formatSnapshotDynamicWithTime(snap, snap.timestamp, startTime)
    }

    private fun formatSnapshotDynamicWithTime(snap: SensorSnapshot, time: Long, startTime: Long): String {
        val seconds = (time - startTime) / 1000.0
        val parts = mutableListOf(time.toString(), String.format(Locale.US, "%.3f", seconds))

        if (accelEnabled) parts.addAll(snap.acc.map { String.format(Locale.US, "%.6f", it) })
        if (gyroEnabled) parts.addAll(snap.gyr.map { String.format(Locale.US, "%.6f", it) })
        if (magEnabled) parts.addAll(snap.mag.map { String.format(Locale.US, "%.6f", it) })
        if (pressureEnabled) parts.add(String.format(Locale.US, "%.4f", snap.pressure))
        if (stepEnabled) parts.add(String.format(Locale.US, "%.0f", snap.steps))
        if (lightEnabled) parts.add(String.format(Locale.US, "%.2f", snap.light))
        if (proximityEnabled) parts.add(String.format(Locale.US, "%.1f", snap.proximity))

        return parts.joinToString(",") + "\n"
    }

    override fun onResume() {
        super.onResume()
        startSensorThread()
        registerSensors(if (isLogging) SensorManager.SENSOR_DELAY_FASTEST else SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        if (!isLogging) {
            sensorManager.unregisterListener(this)
            resetActiveStates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorThread?.quitSafely()
        sensorThread = null
    }

    private fun resetActiveStates() {
        accelActive = false
        gyroActive = false
        magActive = false
        pressureActive = false
        stepActive = false
        lightActive = false
        proximityActive = false
    }

    private fun registerSensors(rate: Int) {
        sensorHandler?.post {
            sensorManager.unregisterListener(this)
            mainHandler.post { resetActiveStates() }

            val sensorMap = mapOf(
                Sensor.TYPE_ACCELEROMETER to accelEnabled,
                Sensor.TYPE_GYROSCOPE to gyroEnabled,
                Sensor.TYPE_MAGNETIC_FIELD to magEnabled,
                Sensor.TYPE_PRESSURE to pressureEnabled,
                Sensor.TYPE_STEP_COUNTER to stepEnabled,
                Sensor.TYPE_LIGHT to lightEnabled,
                Sensor.TYPE_PROXIMITY to proximityEnabled
            )

            sensorMap.forEach { (type, enabled) ->
                if (enabled) {
                    sensorManager.getDefaultSensor(type)?.let { sensor ->
                        sensorManager.registerListener(this, sensor, rate, sensorHandler)
                    }
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val currentTime = System.currentTimeMillis()

        // Cache values to update UI throttled
        var updateUI = false
        if (currentTime - lastUIUpdateTimestamp > UI_UPDATE_INTERVAL_MS) {
            updateUI = true
            lastUIUpdateTimestamp = currentTime
        }

        val values = event.values.clone()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                if (updateUI) mainHandler.post { accelData = values; accelActive = true }
            }

            Sensor.TYPE_GYROSCOPE -> {
                if (updateUI) mainHandler.post { gyroData = values; gyroActive = true }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (updateUI) mainHandler.post { magData = values; magActive = true }
            }

            Sensor.TYPE_PRESSURE -> {
                if (updateUI) mainHandler.post { pressure = values[0]; pressureActive = true }
            }

            Sensor.TYPE_STEP_COUNTER -> {
                if (updateUI) mainHandler.post { stepCount = values[0]; stepActive = true }
            }

            Sensor.TYPE_LIGHT -> {
                if (updateUI) mainHandler.post { lightData = values[0]; lightActive = true }
            }

            Sensor.TYPE_PROXIMITY -> {
                if (updateUI) mainHandler.post { proximityData = values[0]; proximityActive = true }
            }
        }

        if (isLogging) {
            if (currentTime != lastCaptureTimestamp) {
                lastCaptureTimestamp = currentTime
                
                // Add to buffer (Thread-safe list)
                rawDataBuffer.add(
                    SensorSnapshot(
                        timestamp = currentTime,
                        acc = if (accelEnabled && event.sensor.type == Sensor.TYPE_ACCELEROMETER) values else accelData,
                        gyr = if (gyroEnabled && event.sensor.type == Sensor.TYPE_GYROSCOPE) values else gyroData,
                        mag = if (magEnabled && event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) values else magData,
                        pressure = if (pressureEnabled && event.sensor.type == Sensor.TYPE_PRESSURE) values[0] else pressure,
                        steps = if (stepEnabled && event.sensor.type == Sensor.TYPE_STEP_COUNTER) values[0] else stepCount,
                        light = if (lightEnabled && event.sensor.type == Sensor.TYPE_LIGHT) values[0] else lightData,
                        proximity = if (proximityEnabled && event.sensor.type == Sensor.TYPE_PROXIMITY) values[0] else proximityData
                    )
                )
                
                // Update count and time on UI thread throttled
                if (updateUI) {
                    val count = rawDataBuffer.size
                    val elapsed = currentTime - logStartTime
                    mainHandler.post {
                        sampleCount = count
                        elapsedTime = elapsed
                    }
                }
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

    val launcher =
        androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
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
    light: Float,
    proximity: Float,
    accelActive: Boolean,
    gyroActive: Boolean,
    magActive: Boolean,
    pressureActive: Boolean,
    stepActive: Boolean,
    lightActive: Boolean,
    proximityActive: Boolean,
    accelEnabled: Boolean,
    gyroEnabled: Boolean,
    magEnabled: Boolean,
    pressureEnabled: Boolean,
    stepEnabled: Boolean,
    lightEnabled: Boolean,
    proximityEnabled: Boolean,
    onToggleSensor: (String) -> Unit,
    isLogging: Boolean,
    hasRecordedData: Boolean,
    sampleCount: Int,
    elapsedTime: Long,
    tag: String,
    onTagChange: (String) -> Unit,
    showRateDialog: Boolean,
    onShowRateDialogChange: (Boolean) -> Unit,
    showExitDialog: Boolean,
    onShowExitDialogChange: (Boolean) -> Unit,
    showStopLoggingDialog: Boolean,
    onShowStopLoggingDialogChange: (Boolean) -> Unit,
    onExitApp: () -> Unit,
    currentRate: Int,
    onRateChange: (Int) -> Unit,
    onToggleLogging: () -> Unit,
    onConfirmStopLogging: () -> Unit,
    onSaveFile: () -> Unit
) {
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { onShowExitDialogChange(false) },
            title = { Text("Sair da Aplicação") },
            text = { Text("Deseja realmente fechar o MTLogger?") },
            confirmButton = {
                Button(
                    onClick = onExitApp,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Sair")
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowExitDialogChange(false) }) {
                    Text("Continuar")
                }
            },
            containerColor = CardBg,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    if (showStopLoggingDialog) {
        AlertDialog(
            onDismissRequest = { onShowStopLoggingDialogChange(false) },
            title = { Text("Encerrar Captura") },
            text = { Text("Deseja finalizar a coleta de dados atual?") },
            confirmButton = {
                Button(
                    onClick = onConfirmStopLogging,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                ) {
                    Text("Finalizar")
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowStopLoggingDialogChange(false) }) {
                    Text("Manter Gravando")
                }
            },
            containerColor = CardBg,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    if (showRateDialog) {
        AlertDialog(
            onDismissRequest = { onShowRateDialogChange(false) },
            title = { Text("Taxa de Amostragem") },
            text = {
                Column {
                    Text(
                        "Selecione a taxa de reamostragem para exportação do CSV:",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SamplingRateOptions(currentRate, onRateChange)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onShowRateDialogChange(false)
                        onSaveFile()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Salvar CSV")
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowRateDialogChange(false) }) {
                    Text("Cancelar")
                }
            },
            containerColor = CardBg,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(48.dp))
            HeaderSection(
                accelActive,
                gyroActive,
                magActive,
                pressureActive,
                stepActive,
                lightActive,
                proximityActive,
                accelEnabled,
                gyroEnabled,
                magEnabled,
                pressureEnabled,
                stepEnabled,
                lightEnabled,
                proximityEnabled,
                onToggleSensor,
                isLogging
            )
            Spacer(modifier = Modifier.height(24.dp))

            // TAG Input Field
            TagInputField(tag, onTagChange, isLogging)

            Spacer(modifier = Modifier.height(12.dp))

            RecordingCard(
                isLogging,
                hasRecordedData,
                sampleCount,
                elapsedTime,
                onToggleLogging,
                { onShowRateDialogChange(true) })
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (accelEnabled) item { SensorDataCard("ACELERÔMETRO", "m/s²", accel, AccentBlue) }
                if (gyroEnabled) item { SensorDataCard("GIROSCÓPIO", "rad/s", gyro, AccentGreen) }
                if (magEnabled) item { SensorDataCard("MAGNETÔMETRO", "µT", mag, AccentPurple) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (lightEnabled) Box(modifier = Modifier.weight(1f)) {
                            SingleSensorCard("LUZ", "lux", light, Color(0xFFFFD700))
                        }
                        if (proximityEnabled) Box(modifier = Modifier.weight(1f)) {
                            SingleSensorCard("PROXIMIDADE", "cm", proximity, Color(0xFFE91E63))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (pressureEnabled) Box(modifier = Modifier.weight(1f)) {
                            SingleSensorCard("BARÔMETRO", "hPa", pressure, AccentRed)
                        }
                        if (stepEnabled) Box(modifier = Modifier.weight(1f)) {
                            SingleSensorCard("PEDÔMETRO", "passos", stepCount, Color.Yellow)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun TagInputField(
    tag: String,
    onTagChange: (String) -> Unit,
    isLogging: Boolean
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isLogging) {
        if (isLogging) {
            // Dismiss keyboard and clear focus BEFORE high-frequency logging starts
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    OutlinedTextField(
        value = tag,
        onValueChange = onTagChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Digite a TAG", color = TextSecondary.copy(alpha = 0.5f)) },
        enabled = !isLogging,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AccentBlue,
            focusedBorderColor = AccentBlue,
            unfocusedBorderColor = CardBg,
            focusedContainerColor = CardBg,
            unfocusedContainerColor = CardBg
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun SamplingRateOptions(currentRate: Int, onRateChange: (Int) -> Unit) {
    val rates = listOf(
        "Original (Max)" to 0,
        "100 Hz" to 100,
        "50 Hz" to 50,
        "20 Hz" to 20
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rates.forEach { (label, rate) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (currentRate == rate) Color(0xFF1E293B) else Color.Transparent)
                    .clickable { onRateChange(rate) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentRate == rate,
                    onClick = { onRateChange(rate) },
                    colors = RadioButtonDefaults.colors(selectedColor = AccentBlue)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, color = if (currentRate == rate) TextPrimary else TextSecondary)
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
    stepActive: Boolean,
    lightActive: Boolean,
    proximityActive: Boolean,
    accelEnabled: Boolean,
    gyroEnabled: Boolean,
    magEnabled: Boolean,
    pressureEnabled: Boolean,
    stepEnabled: Boolean,
    lightEnabled: Boolean,
    proximityEnabled: Boolean,
    onToggleSensor: (String) -> Unit,
    isLogging: Boolean
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
            Text("Multisensor", color = TextSecondary, fontSize = 14.sp)
        }
        
        // Grid 4x4 para os Badges (Ajustado para o tamanho dos ícones)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusBadge("A", accelEnabled, accelActive, isLogging) { onToggleSensor("A") }
                StatusBadge("G", gyroEnabled, gyroActive, isLogging) { onToggleSensor("G") }
                StatusBadge("M", magEnabled, magActive, isLogging) { onToggleSensor("M") }
                StatusBadge("B", pressureEnabled, pressureActive, isLogging) { onToggleSensor("B") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusBadge("L", lightEnabled, lightActive, isLogging) { onToggleSensor("L") }
                StatusBadge("Pr", proximityEnabled, proximityActive, isLogging) { onToggleSensor("Pr") }
                StatusBadge("P", stepEnabled, stepActive, isLogging) { onToggleSensor("P") }
                Spacer(modifier = Modifier.size(36.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.size(36.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
fun StatusBadge(
    label: String,
    enabled: Boolean,
    active: Boolean,
    isLogging: Boolean,
    onToggle: () -> Unit
) {
    val bgColor = if (enabled) AccentGreen.copy(alpha = 0.15f) else CardBg
    val textColor = if (enabled) AccentGreen else TextSecondary.copy(alpha = 0.3f)

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable(enabled = !isLogging) { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun RecordingCard(
    isLogging: Boolean,
    hasRecordedData: Boolean,
    sampleCount: Int,
    elapsedTime: Long,
    onToggle: () -> Unit,
    onSaveClick: () -> Unit
) {
    val seconds = (elapsedTime / 1000) % 60
    val minutes = (elapsedTime / (1000 * 60)) % 60
    val timeString = String.format("%02d:%02d", minutes, seconds)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (isLogging) "Coletando (MAX)..." else if (hasRecordedData) "Coleta Finalizada" else "Pronto",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text("$timeString   $sampleCount pts", color = TextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier
                        .weight(3f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isLogging) AccentPurple else AccentRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (isLogging) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isLogging) "Parar" else "Gravar",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onSaveClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(
                            if (hasRecordedData && !isLogging) Color(0xFF10B981) else Color(
                                0xFF1E293B
                            ),
                            RoundedCornerShape(12.dp)
                        ),
                    enabled = hasRecordedData && !isLogging
                ) {
                    Icon(
                        Icons.Default.Download,
                        null,
                        tint = if (hasRecordedData && !isLogging) Color.White else TextSecondary
                    )
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(unit, color = TextSecondary, fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
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
                Box(modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (title == "PEDÔMETRO") String.format(
                    "%.0f",
                    value
                ) else String.format("%.2f", value),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(unit, color = TextSecondary, fontSize = 9.sp)
        }
    }
}

@Composable
fun SensorValueColumn(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextSecondary, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            String.format("%.2f", value),
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
