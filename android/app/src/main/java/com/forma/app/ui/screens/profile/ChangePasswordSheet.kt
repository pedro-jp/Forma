package com.forma.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.forma.app.data.repository.FormaRepository
import com.forma.app.ui.components.FormaBanner
import com.forma.app.ui.components.FormaBannerStyle
import com.forma.app.ui.components.FormaModalSheet
import com.forma.app.ui.components.FormaSheetTopBar
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Porte de `ChangePasswordSheet` (LegalPrivacySheet.swift) — somente contas EMAIL. */
@Composable
fun ChangePasswordSheet(
    repository: FormaRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val colors = FormaTheme.Colors
    val scope = rememberCoroutineScope()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    fun saveNewPassword() {
        errorMessage = null
        isSaving = true
        scope.launch {
            try {
                repository.auth.changePassword(currentPassword, newPassword)
                isSaving = false
                onDismiss()
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message.orEmpty()
                isSaving = false
            }
        }
    }

    FormaModalSheet(onDismissRequest = onDismiss) {
        FormaSheetTopBar(title = "Alterar Senha", onCancel = onDismiss)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FormaTheme.Spacing.screenHorizontal, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Atualize a senha de acesso da sua conta Forma.",
                color = colors.secondaryText,
                style = FormaTypography.subheadline
            )

            PasswordField(value = currentPassword, onValueChange = { currentPassword = it }, placeholder = "Senha atual")
            PasswordField(
                value = newPassword,
                onValueChange = { newPassword = it },
                placeholder = "Nova senha (mín. 6 caracteres)"
            )

            errorMessage?.let { FormaBanner(message = it, style = FormaBannerStyle.Error) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isSaving) 0.5f else 1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.brand)
                    .formaPlainClickable(enabled = !isSaving) { saveNewPassword() }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                }
                Text(text = "Salvar Nova Senha", color = Color.White, style = FormaTypography.headline)
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val colors = FormaTheme.Colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.cardSurface)
            .padding(14.dp)
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, color = colors.tertiaryText, style = FormaTypography.body)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            textStyle = FormaTypography.body.copy(color = colors.primaryText),
            cursorBrush = SolidColor(colors.brand),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
