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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.mtlogger.ui.theme.MTLoggerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // File logging members
    private var loggingOutputStream: BufferedOutputStream? = null
    private var loggingFileStartTime = 0L

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
    private var sampleCount by mutableIntStateOf(0)
    private var logStartTime by mutableLongStateOf(0L)
    private var elapsedTime by mutableLongStateOf(0L)

    // Profile state
    private var userProfile by mutableStateOf(UserProfile())
    private var currentScreen by mutableStateOf("main") // "main" or "profile"

    // Internal non-state count for high-frequency updates
    private var internalSampleCount = 0

    // Last capture to avoid redundant writes
    private var lastCaptureTimestamp = 0L

    // Throttling for UI updates
    private var lastUIUpdateTimestamp = 0L
    private val UI_UPDATE_INTERVAL_MS = 100L // 10Hz UI updates

    // Dialog states
    private var showExitDialog by mutableStateOf(false)
    private var showStopLoggingDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        startSensorThread()

        enableEdgeToEdge()
        setContent {
            LanguageProvider {
                MTLoggerTheme(darkTheme = true) {
                    AppNavigation()
                }
            }
        }
    }

    @Composable
    private fun AppNavigation() {
        val lang = LocalLanguageManager.current
        BackHandler {
            if (currentScreen == "profile") {
                currentScreen = "main"
            } else {
                showExitDialog = true
            }
        }

        PermissionRequester {
            if (currentScreen == "main") {
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
                    sampleCount = sampleCount,
                    elapsedTime = elapsedTime,
                    onProfileClick = { if (!isLogging) currentScreen = "profile" },
                    showExitDialog = showExitDialog,
                    onShowExitDialogChange = { showExitDialog = it },
                    showStopLoggingDialog = showStopLoggingDialog,
                    onShowStopLoggingDialogChange = { showStopLoggingDialog = it },
                    onExitApp = { finish() },
                    onToggleLogging = {
                        if (isLogging) {
                            showStopLoggingDialog = true
                        } else {
                            toggleLogging()
                        }
                    },
                    onConfirmStopLogging = {
                        showStopLoggingDialog = false
                        toggleLogging()
                    }
                )
            } else {
                UserProfileScreen(
                    profile = userProfile,
                    onProfileChange = { userProfile = it },
                    onBack = { currentScreen = "main" },
                    onSaveProfile = { saveProfileToCsv(lang) }
                )
            }
        }
    }

    private fun saveProfileToCsv(lang: LanguageManager) {
        val timeStamp = SimpleDateFormat("HH_mm_ss_dd_MM_yyyy", Locale.getDefault()).format(Date())
        val fileName = "profile_${userProfile.subjectId}_${timeStamp}.csv"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var outputStream: OutputStream? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOCUMENTS + "/MTLogger/Profiles"
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
                        "MTLogger/Profiles"
                    )
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, fileName)
                    outputStream = FileOutputStream(file)
                }

                outputStream?.use { stream ->
                    val header = "Equipment,SubjectID,Event,Intensity,Gender,Age,Height,Weight,Position\n"
                    val data = "${userProfile.equipment},${userProfile.subjectId},${userProfile.event},${userProfile.intensity},${userProfile.gender},${userProfile.age},${userProfile.height},${userProfile.weight},${userProfile.position}\n"
                    stream.write(header.toByteArray())
                    stream.write(data.toByteArray())
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        lang.getString("Perfil salvo com sucesso!", "Profile saved successfully!"),
                        Toast.LENGTH_SHORT
                    ).show()
                    currentScreen = "main"
                }
            } catch (e: Exception) {
                Log.e("MTLogger", "Error saving profile", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        lang.getString("Erro ao salvar perfil", "Error saving profile"),
                        Toast.LENGTH_SHORT
                    ).show()
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
            stopContinuousLogging()
        } else {
            startContinuousLogging()
        }
    }

    private fun startContinuousLogging() {
        val currentTime = System.currentTimeMillis()
        val timeStamp = SimpleDateFormat("HH_mm_ss_dd_MM_yyyy", Locale.getDefault()).format(Date())
        val cleanSubjectId = userProfile.subjectId.trim().replace("\\s+".toRegex(), "_")
        val prefix = if (cleanSubjectId.isNotEmpty()) "${cleanSubjectId}_" else "unnamed_"
        val fileName = "${prefix}${timeStamp}_MAX.csv"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
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

                if (outputStream != null) {
                    val bufferedStream = BufferedOutputStream(outputStream)
                    val headerParts = mutableListOf("timestamp", "seconds")
                    if (accelEnabled) headerParts.addAll(listOf("acc_x", "acc_y", "acc_z"))
                    if (gyroEnabled) headerParts.addAll(listOf("gyr_x", "gyr_y", "gyr_z"))
                    if (magEnabled) headerParts.addAll(listOf("mag_x", "mag_y", "mag_z"))
                    if (pressureEnabled) headerParts.add("pressure")
                    if (stepEnabled) headerParts.add("step_count")
                    if (lightEnabled) headerParts.add("light")
                    if (proximityEnabled) headerParts.add("proximity")
                    bufferedStream.write((headerParts.joinToString(",") + "\n").toByteArray())

                    withContext(Dispatchers.Main) {
                        loggingOutputStream = bufferedStream
                        loggingFileStartTime = currentTime
                        internalSampleCount = 0
                        sampleCount = 0
                        logStartTime = currentTime
                        elapsedTime = 0L
                        lastCaptureTimestamp = 0L
                        isLogging = true
                        registerSensors(SensorManager.SENSOR_DELAY_FASTEST)
                    }
                }
            } catch (e: Exception) {
                Log.e("MTLogger", "Error starting log", e)
            }
        }
    }

    private fun stopContinuousLogging() {
        isLogging = false
        registerSensors(SensorManager.SENSOR_DELAY_UI)

        sensorHandler?.post {
            try {
                loggingOutputStream?.flush()
                loggingOutputStream?.close()
                loggingOutputStream = null
                mainHandler.post {
                    Toast.makeText(this, "Arquivo salvo em Documentos/MTLogger", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MTLogger", "Error closing log file", e)
            }
        }
    }

    private fun formatLine(currentTime: Long): String {
        val seconds = (currentTime - loggingFileStartTime) / 1000.0
        val parts = mutableListOf(currentTime.toString(), String.format(Locale.US, "%.3f", seconds))

        if (accelEnabled) parts.addAll(accelData.map { String.format(Locale.US, "%.6f", it) })
        if (gyroEnabled) parts.addAll(gyroData.map { String.format(Locale.US, "%.6f", it) })
        if (magEnabled) parts.addAll(magData.map { String.format(Locale.US, "%.6f", it) })
        if (pressureEnabled) parts.add(String.format(Locale.US, "%.4f", pressure))
        if (stepEnabled) parts.add(String.format(Locale.US, "%.0f", stepCount))
        if (lightEnabled) parts.add(String.format(Locale.US, "%.2f", lightData))
        if (proximityEnabled) parts.add(String.format(Locale.US, "%.1f", proximityData))

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
        try { loggingOutputStream?.close() } catch (e: Exception) {}
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
        val values = event.values.clone()

        var updateUI = false
        if (currentTime - lastUIUpdateTimestamp > UI_UPDATE_INTERVAL_MS) {
            updateUI = true
            lastUIUpdateTimestamp = currentTime
        }

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelData = values
                if (updateUI) mainHandler.post { accelActive = true }
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroData = values
                if (updateUI) mainHandler.post { gyroActive = true }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magData = values
                if (updateUI) mainHandler.post { magActive = true }
            }
            Sensor.TYPE_PRESSURE -> {
                pressure = values[0]
                if (updateUI) mainHandler.post { pressureActive = true }
            }
            Sensor.TYPE_STEP_COUNTER -> {
                stepCount = values[0]
                if (updateUI) mainHandler.post { stepActive = true }
            }
            Sensor.TYPE_LIGHT -> {
                lightData = values[0]
                if (updateUI) mainHandler.post { lightActive = true }
            }
            Sensor.TYPE_PROXIMITY -> {
                proximityData = values[0]
                if (updateUI) mainHandler.post { proximityActive = true }
            }
        }

        if (isLogging) {
            try {
                loggingOutputStream?.let { stream ->
                    internalSampleCount++
                    val line = formatLine(currentTime)
                    stream.write(line.toByteArray())
                    
                    if (updateUI) {
                        val count = internalSampleCount
                        val elapsed = currentTime - logStartTime
                        mainHandler.post {
                            sampleCount = count
                            elapsedTime = elapsed
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MTLogger", "Error writing to log file", e)
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
