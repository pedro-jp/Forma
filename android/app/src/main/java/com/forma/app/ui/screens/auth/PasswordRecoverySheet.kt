package com.forma.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forma.app.data.auth.AuthManager
import com.forma.app.ui.components.FormaBanner
import com.forma.app.ui.components.FormaBannerStyle
import com.forma.app.ui.components.FormaModalSheet
import com.forma.app.ui.components.FormaSheetTopBar
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Recuperação de senha em duas etapas (porte de `PasswordRecoverySheet` do iOS):
 * 1) e-mail -> código de 6 dígitos; 2) código + nova senha -> redefine e entra.
 */
@Composable
fun PasswordRecoverySheet(
    auth: AuthManager,
    initialEmail: String = "",
    onDismiss: () -> Unit,
    onRecoverySuccess: () -> Unit
) {
    val colors = FormaTheme.Colors
    val scope = rememberCoroutineScope()

    var emailText by remember { mutableStateOf(initialEmail) }
    var codeText by remember { mutableStateOf("") }
    var newPasswordText by remember { mutableStateOf("") }
    var stepSentCode by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun sendCode() {
        errorMessage = null
        statusMessage = null
        isSubmitting = true
        scope.launch {
            try {
                statusMessage = auth.requestPasswordReset(emailText)
                stepSentCode = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: e.toString()
            }
            isSubmitting = false
        }
    }

    fun confirmReset() {
        errorMessage = null
        isSubmitting = true
        scope.launch {
            try {
                auth.resetPassword(emailText, codeText, newPasswordText)
                isSubmitting = false
                onDismiss()
                onRecoverySuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: e.toString()
                isSubmitting = false
            }
        }
    }

    FormaModalSheet(onDismissRequest = onDismiss) {
        FormaSheetTopBar(title = "Recuperar Senha", cancelTitle = "Cancelar", onCancel = onDismiss)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FormaTheme.Spacing.screenHorizontal, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = if (stepSentCode) "Digite o código de 6 dígitos enviado para seu e-mail e escolha uma nova senha."
                else "Informe o e-mail da sua conta para receber um código de 6 dígitos e criar uma nova senha.",
                color = colors.secondaryText,
                style = FormaTypography.subheadline
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AuthTextField(
                    icon = sfSymbol("envelope.fill"),
                    placeholder = "Seu e-mail cadastrado",
                    value = emailText,
                    onValueChange = { emailText = it },
                    keyboardType = KeyboardType.Email,
                    outlined = false
                )
                if (stepSentCode) {
                    AuthTextField(
                        icon = Icons.Filled.Tag,
                        placeholder = "Código de 6 dígitos",
                        value = codeText,
                        onValueChange = { codeText = it },
                        keyboardType = KeyboardType.Number,
                        outlined = false
                    )
                    AuthTextField(
                        icon = sfSymbol("lock.fill"),
                        placeholder = "Nova senha (mín. 6 caracteres)",
                        value = newPasswordText,
                        onValueChange = { newPasswordText = it },
                        isPassword = true,
                        imeAction = ImeAction.Done,
                        outlined = false
                    )
                }
            }

            statusMessage?.let { FormaBanner(message = it, style = FormaBannerStyle.Info) }
            errorMessage?.let { FormaBanner(message = it, style = FormaBannerStyle.Error) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isSubmitting) 0.5f else 1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.brand)
                    .formaPlainClickable(enabled = !isSubmitting) {
                        if (stepSentCode) confirmReset() else sendCode()
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = if (stepSentCode) "Redefinir Senha e Entrar" else "Enviar Código de Recuperação",
                    color = Color.White,
                    style = FormaTypography.headline
                )
            }

            if (!stepSentCode) {
                Text(
                    text = "Já tenho um código de 6 dígitos",
                    color = colors.brand,
                    style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .formaPlainClickable { stepSentCode = true }
                )
            }
        }
    }
}
