package com.example.mtlogger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

enum class Language {
    PT_BR, EN
}

class LanguageManager {
    var currentLanguage by mutableStateOf(Language.PT_BR)

    fun getString(pt: String, en: String): String {
        return if (currentLanguage == Language.PT_BR) pt else en
    }
}

val LocalLanguageManager = compositionLocalOf<LanguageManager> { error("No LanguageManager provided") }

@Composable
fun LanguageProvider(content: @Composable () -> Unit) {
    val manager = remember { LanguageManager() }
    CompositionLocalProvider(LocalLanguageManager provides manager) {
        content()
    }
}
