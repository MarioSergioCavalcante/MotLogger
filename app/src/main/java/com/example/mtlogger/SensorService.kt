package com.example.mtlogger

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var loggingOutputStream: BufferedOutputStream? = null
    private var loggingFileStartTime = 0L
    private var logStartTime = 0L

    // Non-state variables for high-frequency updates
    private var rawAccel = floatArrayOf(0f, 0f, 0f)
    private var rawGyro = floatArrayOf(0f, 0f, 0f)
    private var rawMag = floatArrayOf(0f, 0f, 0f)
    private var rawPressure = 0f
    private var rawStepCount = 0f
    private var rawLight = 0f
    private var rawProximity = 0f
    private var internalSampleCount = 0

    // Notification constants
    private val CHANNEL_ID = "SensorServiceChannel"
    private val NOTIFICATION_ID = 1

    companion object {
        var isRunning by mutableStateOf(false)
        var sampleCount by mutableStateOf(0)
        var elapsedTime by mutableStateOf(0L)
        
        // Data for UI (updated at lower frequency)
        var accelData by mutableStateOf(floatArrayOf(0f, 0f, 0f))
        var gyroData by mutableStateOf(floatArrayOf(0f, 0f, 0f))
        var magData by mutableStateOf(floatArrayOf(0f, 0f, 0f))
        var pressureData by mutableStateOf(0f)
        var stepData by mutableStateOf(0f)
        var lightData by mutableStateOf(0f)
        var proximityData by mutableStateOf(0f)

        // Enabled sensors (passed from Activity)
        var accelEnabled = true
        var gyroEnabled = true
        var magEnabled = true
        var pressureEnabled = true
        var stepEnabled = true
        var lightEnabled = true
        var proximityEnabled = true
        
        var userProfile = UserProfile()
    }

    private var lastUIUpdateTimestamp = 0L
    private val UI_UPDATE_INTERVAL_MS = 100L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        startSensorThread()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_SERVICE") {
            stopLogging()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        startLogging()
        return START_STICKY
    }

    private fun startSensorThread() {
        sensorThread = HandlerThread("SensorThread").apply { start() }
        sensorHandler = Handler(sensorThread!!.looper)
    }

    private fun startLogging() {
        val currentTime = System.currentTimeMillis()
        val timeStamp = SimpleDateFormat("HH_mm_ss_dd_MM_yyyy", Locale.getDefault()).format(Date())
        val cleanSubjectId = userProfile.subjectId.trim().replace("\\s+".toRegex(), "_")
        val prefix = if (cleanSubjectId.isNotEmpty()) "${cleanSubjectId}_" else "unnamed_"
        val fileName = "${prefix}${timeStamp}_MAX.csv"

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
                loggingOutputStream = BufferedOutputStream(outputStream)
                val headerParts = mutableListOf("timestamp", "system_uptime_nanos", "seconds")
                if (accelEnabled) headerParts.addAll(listOf("acc_x", "acc_y", "acc_z"))
                if (gyroEnabled) headerParts.addAll(listOf("gyr_x", "gyr_y", "gyr_z"))
                if (magEnabled) headerParts.addAll(listOf("mag_x", "mag_y", "mag_z"))
                if (pressureEnabled) headerParts.add("pressure")
                if (stepEnabled) headerParts.add("step_count")
                if (lightEnabled) headerParts.add("light")
                if (proximityEnabled) headerParts.add("proximity")
                loggingOutputStream?.write((headerParts.joinToString(",") + "\n").toByteArray())

                loggingFileStartTime = currentTime
                logStartTime = currentTime
                internalSampleCount = 0
                sampleCount = 0
                elapsedTime = 0L
                isRunning = true

                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MTLogger::LoggingLock").apply {
                    acquire(10 * 60 * 60 * 1000L /* 10 hours timeout */)
                }

                registerSensors(SensorManager.SENSOR_DELAY_FASTEST)
            }
        } catch (e: Exception) {
            Log.e("SensorService", "Error starting log", e)
        }
    }

    private fun stopLogging() {
        isRunning = false
        sensorManager.unregisterListener(this)
        
        sensorHandler?.post {
            try {
                loggingOutputStream?.flush()
                loggingOutputStream?.close()
                loggingOutputStream = null
            } catch (e: Exception) {
                Log.e("SensorService", "Error closing log file", e)
            }
        }

        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun registerSensors(rate: Int) {
        sensorHandler?.post {
            sensorManager.unregisterListener(this)
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
        val values = event.values

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> rawAccel = values.clone()
            Sensor.TYPE_GYROSCOPE -> rawGyro = values.clone()
            Sensor.TYPE_MAGNETIC_FIELD -> rawMag = values.clone()
            Sensor.TYPE_PRESSURE -> rawPressure = values[0]
            Sensor.TYPE_STEP_COUNTER -> rawStepCount = values[0]
            Sensor.TYPE_LIGHT -> rawLight = values[0]
            Sensor.TYPE_PROXIMITY -> rawProximity = values[0]
        }

        if (isRunning) {
            try {
                loggingOutputStream?.let { stream ->
                    internalSampleCount++
                    val line = fastFormatLine(currentTime)
                    stream.write(line.toByteArray())

                    val now = System.currentTimeMillis()
                    if (now - lastUIUpdateTimestamp > UI_UPDATE_INTERVAL_MS) {
                        lastUIUpdateTimestamp = now
                        val count = internalSampleCount
                        val elapsed = now - logStartTime
                        Handler(Looper.getMainLooper()).post {
                            sampleCount = count
                            elapsedTime = elapsed
                            accelData = rawAccel
                            gyroData = rawGyro
                            magData = rawMag
                            pressureData = rawPressure
                            stepData = rawStepCount
                            lightData = rawLight
                            proximityData = rawProximity
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SensorService", "Error writing to log", e)
            }
        }
    }

    private fun fastFormatLine(currentTime: Long): String {
        val sb = StringBuilder()
        val uptimeNanos = SystemClock.elapsedRealtimeNanos()
        val seconds = (currentTime - loggingFileStartTime) / 1000.0
        
        sb.append(currentTime).append(",")
        sb.append(uptimeNanos).append(",")
        sb.append(String.format(Locale.US, "%.3f", seconds))

        if (accelEnabled) sb.append(",").append(rawAccel[0]).append(",").append(rawAccel[1]).append(",").append(rawAccel[2])
        if (gyroEnabled) sb.append(",").append(rawGyro[0]).append(",").append(rawGyro[1]).append(",").append(rawGyro[2])
        if (magEnabled) sb.append(",").append(rawMag[0]).append(",").append(rawMag[1]).append(",").append(rawMag[2])
        if (pressureEnabled) sb.append(",").append(rawPressure)
        if (stepEnabled) sb.append(",").append(rawStepCount)
        if (lightEnabled) sb.append(",").append(rawLight)
        if (proximityEnabled) sb.append(",").append(rawProximity)

        sb.append("\n")
        return sb.toString()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "MTLogger Sensor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, SensorService::class.java).apply { action = "STOP_SERVICE" }
        val stopPendingIntent =
            PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MTLogger Gravando")
            .setContentText("Capturando dados dos sensores em alta frequência...")
            .setSmallIcon(R.drawable.meu_logo)
            .addAction(R.drawable.meu_logo, "Parar", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        stopLogging()
        sensorThread?.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
