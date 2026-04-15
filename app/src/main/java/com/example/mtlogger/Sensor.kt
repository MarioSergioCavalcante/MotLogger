package com.example.mtlogger

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mtlogger.ui.theme.*

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
    sampleCount: Int,
    elapsedTime: Long,
    onProfileClick: () -> Unit,
    showExitDialog: Boolean,
    onShowExitDialogChange: (Boolean) -> Unit,
    showStopLoggingDialog: Boolean,
    onShowStopLoggingDialogChange: (Boolean) -> Unit,
    onExitApp: () -> Unit,
    onToggleLogging: () -> Unit,
    onConfirmStopLogging: () -> Unit
) {
    val lang = LocalLanguageManager.current

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { onShowExitDialogChange(false) },
            title = { Text(lang.getString("Sair da Aplicação", "Exit Application")) },
            text = { Text(lang.getString("Deseja realmente fechar o MTLogger?", "Do you really want to close MTLogger?")) },
            confirmButton = {
                Button(
                    onClick = onExitApp,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text(lang.getString("Sair", "Exit"))
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowExitDialogChange(false) }) {
                    Text(lang.getString("Continuar", "Continue"))
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
            title = { Text(lang.getString("Encerrar Captura", "End Capture")) },
            text = { Text(lang.getString("Deseja finalizar a coleta de dados atual?", "Do you want to end the current data collection?")) },
            confirmButton = {
                Button(
                    onClick = onConfirmStopLogging,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
                ) {
                    Text(lang.getString("Finalizar", "Finish"))
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowStopLoggingDialogChange(false) }) {
                    Text(lang.getString("Manter Gravando", "Keep Recording"))
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

            // Profile Button
            Button(
                onClick = onProfileClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLogging,
                colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = AccentBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(lang.getString("Cadastrar Perfil do Usuário", "Register User Profile"), color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            RecordingCard(
                isLogging,
                sampleCount,
                elapsedTime,
                onToggleLogging
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (accelEnabled) item { SensorDataCard(lang.getString("ACELERÔMETRO", "ACCELEROMETER"), "m/s²", accel, AccentBlue) }
                if (gyroEnabled) item { SensorDataCard(lang.getString("GIROSCÓPIO", "GYROSCOPE"), "rad/s", gyro, AccentGreen) }
                if (magEnabled) item { SensorDataCard(lang.getString("MAGNETÔMETRO", "MAGNETOMETER"), "µT", mag, AccentPurple) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (lightEnabled) Box(modifier = Modifier.weight(1f)) {
                            SingleSensorCard(lang.getString("LUZ", "LIGHT"), "lux", light, Color(0xFFFFD700))
                        }
                        if (proximityEnabled) Box(modifier = Modifier.weight(1f)) {
                            SingleSensorCard(lang.getString("PROXIMIDADE", "PROXIMITY"), "cm", proximity, Color(0xFFE91E63))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (pressureEnabled) Box(modifier = Modifier.weight(1f)) {
                            SingleSensorCard(lang.getString("BARÔMETRO", "BAROMETER"), "hPa", pressure, AccentRed)
                        }
                        if (stepEnabled) Box(modifier = Modifier.weight(1f)) {
                            SingleSensorCard(lang.getString("PEDÔMETRO", "PEDOMETER"), lang.getString("passos", "steps"), stepCount, Color.Yellow)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
private fun HeaderSection(
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
    val lang = LocalLanguageManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    lang.currentLanguage = if (lang.currentLanguage == Language.PT_BR) Language.EN else Language.PT_BR
                }
                .padding(bottom = 8.dp)
        ) {
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = lang.getString("Português", "English"),
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

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
                Text(lang.getString("Multisensor", "Multisensor"), color = TextSecondary, fontSize = 14.sp)
            }

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
            }
        }
    }
}

@Composable
private fun StatusBadge(
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
private fun RecordingCard(
    isLogging: Boolean,
    sampleCount: Int,
    elapsedTime: Long,
    onToggle: () -> Unit
) {
    val lang = LocalLanguageManager.current
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
                    if (isLogging) lang.getString("Coletando (MAX)...", "Collecting (MAX)...") else lang.getString("Pronto", "Ready"),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text("$timeString   $sampleCount pts", color = TextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().height(56.dp),
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
                    if (isLogging) lang.getString("Parar", "Stop") else lang.getString("Gravar", "Record"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SensorDataCard(title: String, unit: String, values: FloatArray, accentColor: Color) {
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
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
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
private fun SingleSensorCard(title: String, unit: String, value: Float, accentColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (title == "PEDÔMETRO" || title == "PEDOMETER") String.format("%.0f", value) else String.format("%.2f", value),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(unit, color = TextSecondary, fontSize = 9.sp)
        }
    }
}

@Composable
private fun SensorValueColumn(label: String, value: Float) {
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
