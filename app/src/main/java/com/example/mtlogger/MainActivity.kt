package com.example.mtlogger

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.example.mtlogger.ui.theme.MTLoggerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    // UI State local to Activity
    private var currentScreen by mutableStateOf("main") // "main" or "profile"
    private var userProfile by mutableStateOf(UserProfile())
    private var showExitDialog by mutableStateOf(false)
    private var showStopLoggingDialog by mutableStateOf(false)

    // Selection states (enabled for capture)
    private var accelEnabled by mutableStateOf(true)
    private var gyroEnabled by mutableStateOf(true)
    private var magEnabled by mutableStateOf(true)
    private var pressureEnabled by mutableStateOf(true)
    private var stepEnabled by mutableStateOf(true)
    private var lightEnabled by mutableStateOf(true)
    private var proximityEnabled by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

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
                    accel = SensorService.accelData,
                    gyro = SensorService.gyroData,
                    mag = SensorService.magData,
                    pressure = SensorService.pressureData,
                    stepCount = SensorService.stepData,
                    light = SensorService.lightData,
                    proximity = SensorService.proximityData,
                    accelActive = SensorService.isRunning && accelEnabled,
                    gyroActive = SensorService.isRunning && gyroEnabled,
                    magActive = SensorService.isRunning && magEnabled,
                    pressureActive = SensorService.isRunning && pressureEnabled,
                    stepActive = SensorService.isRunning && stepEnabled,
                    lightActive = SensorService.isRunning && lightEnabled,
                    proximityActive = SensorService.isRunning && proximityEnabled,
                    accelEnabled = accelEnabled,
                    gyroEnabled = gyroEnabled,
                    magEnabled = magEnabled,
                    pressureEnabled = pressureEnabled,
                    stepEnabled = stepEnabled,
                    lightEnabled = lightEnabled,
                    proximityEnabled = proximityEnabled,
                    onToggleSensor = { sensorLabel ->
                        if (!SensorService.isRunning) {
                            when (sensorLabel) {
                                "A" -> accelEnabled = !accelEnabled
                                "G" -> gyroEnabled = !gyroEnabled
                                "M" -> magEnabled = !magEnabled
                                "B" -> pressureEnabled = !pressureEnabled
                                "P" -> stepEnabled = !stepEnabled
                                "L" -> lightEnabled = !lightEnabled
                                "Pr" -> proximityEnabled = !proximityEnabled
                            }
                        }
                    },
                    isLogging = SensorService.isRunning,
                    sampleCount = SensorService.sampleCount,
                    elapsedTime = SensorService.elapsedTime,
                    onProfileClick = { if (!SensorService.isRunning) currentScreen = "profile" },
                    showExitDialog = showExitDialog,
                    onShowExitDialogChange = { showExitDialog = it },
                    showStopLoggingDialog = showStopLoggingDialog,
                    onShowStopLoggingDialogChange = { showStopLoggingDialog = it },
                    onExitApp = { finish() },
                    onToggleLogging = {
                        if (SensorService.isRunning) {
                            showStopLoggingDialog = true
                        } else {
                            startLoggingService()
                        }
                    },
                    onConfirmStopLogging = {
                        showStopLoggingDialog = false
                        stopLoggingService()
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

    private fun startLoggingService() {
        // Update service configuration before starting
        SensorService.accelEnabled = accelEnabled
        SensorService.gyroEnabled = gyroEnabled
        SensorService.magEnabled = magEnabled
        SensorService.pressureEnabled = pressureEnabled
        SensorService.stepEnabled = stepEnabled
        SensorService.lightEnabled = lightEnabled
        SensorService.proximityEnabled = proximityEnabled
        SensorService.userProfile = userProfile

        val intent = Intent(this, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopLoggingService() {
        val intent = Intent(this, SensorService::class.java).apply {
            action = "STOP_SERVICE"
        }
        startService(intent)
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
}

@Composable
fun PermissionRequester(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val permissions = mutableListOf(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
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
