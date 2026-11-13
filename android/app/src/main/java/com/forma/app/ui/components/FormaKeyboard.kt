package com.forma.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.ExperimentalComposeUiApi

/**
 * Equivalente a `.dismissKeyboardOnTap()` (KeyboardDismissModifier.swift): fecha o teclado ao tocar
 * em áreas vazias, sem roubar toques de campos de texto e controles (filhos consomem o toque antes).
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.dismissKeyboardOnTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    pointerInput(Unit) {
        detectTapGestures(onTap = {
            focusManager.clearFocus()
            keyboard?.hide()
        })
    }
}

/** Lambda que fecha o teclado e limpa o foco (equivalente a `resignFirstResponder`). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberKeyboardDismisser(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return {
        focusManager.clearFocus()
        keyboard?.hide()
    }
}
