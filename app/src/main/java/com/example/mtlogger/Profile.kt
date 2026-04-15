package com.example.mtlogger

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mtlogger.ui.theme.*

data class UserProfile(
    val equipment: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val subjectId: String = "",
    val event: String = "Walking Indoor",
    val intensity: String = "Moderado",
    val gender: String = "Male",
    val age: String = "",
    val height: String = "",
    val weight: String = "",
    val position: String = "Pocket Right"
)

@Composable
fun UserProfileScreen(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit,
    onBack: () -> Unit,
    onSaveProfile: () -> Unit
) {
    val lang = LocalLanguageManager.current

    Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = lang.getString("Voltar", "Back"), tint = TextPrimary)
                }
                Text(lang.getString("Perfil do Usuário", "User Profile"), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))

            ProfileInfoItem(lang.getString("Equipamento", "Equipment"), profile.equipment)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = CardBg)

            ProfileTextField("Subject ID", profile.subjectId) { onProfileChange(profile.copy(subjectId = it)) }

            ProfileRadioGroup(
                title = lang.getString("Evento", "Event"),
                options = listOf("Running indoor", "Running Outdoor", "Walking Indoor", "Walking Outdoor"),
                selectedOption = profile.event,
                onOptionSelected = { onProfileChange(profile.copy(event = it)) }
            )

            ProfileRadioGroup(
                title = lang.getString("Intensidade", "Intensity"),
                options = if (lang.currentLanguage == Language.PT_BR) 
                    listOf("Vigoroso", "Moderado", "Leve", "Nenhum")
                else 
                    listOf("Vigorous", "Moderate", "Light", "None"),
                selectedOption = profile.intensity,
                onOptionSelected = { onProfileChange(profile.copy(intensity = it)) }
            )

            ProfileRadioGroup(
                title = lang.getString("Gênero", "Gender"),
                options = if (lang.currentLanguage == Language.PT_BR) 
                    listOf("Masculino", "Feminino") 
                else 
                    listOf("Male", "Female"),
                selectedOption = profile.gender,
                onOptionSelected = { onProfileChange(profile.copy(gender = it)) }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileNumberField(lang.getString("Idade", "Age"), profile.age, Modifier.weight(1f)) { onProfileChange(profile.copy(age = it)) }
                ProfileNumberField(lang.getString("Altura (cm)", "Height (cm)"), profile.height, Modifier.weight(1f)) { onProfileChange(profile.copy(height = it)) }
                ProfileNumberField(lang.getString("Peso (kg)", "Weight (kg)"), profile.weight, Modifier.weight(1f)) { onProfileChange(profile.copy(weight = it)) }
            }

            ProfileRadioGroup(
                title = lang.getString("Posição", "Position"),
                options = listOf("HandRight", "HandLeft", "Pocket Right", "Pocket Left"),
                selectedOption = profile.position,
                onOptionSelected = { onProfileChange(profile.copy(position = it)) }
            )

            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onSaveProfile,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(lang.getString("Salvar Perfil", "Save Profile"), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun ProfileInfoItem(label: String, value: String) {
    Column {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 16.sp)
    }
}

@Composable
private fun ProfileTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = CardBg,
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun ProfileNumberField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(label, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = CardBg,
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun ProfileRadioGroup(title: String, options: List<String>, selectedOption: String, onOptionSelected: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        options.chunked(2).forEach { rowOptions ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowOptions.forEach { option ->
                    Row(
                        modifier = Modifier.weight(1f).clickable { onOptionSelected(option) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == option,
                            onClick = { onOptionSelected(option) },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentBlue)
                        )
                        Text(option, color = TextPrimary, fontSize = 14.sp)
                    }
                }
                if (rowOptions.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
