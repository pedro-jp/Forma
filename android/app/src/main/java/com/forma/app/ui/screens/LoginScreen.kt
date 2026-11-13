package com.forma.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forma.app.R
import com.forma.app.data.auth.GoogleAuthService
import com.forma.app.data.remote.FormaApiException
import com.forma.app.data.repository.FormaRepository
import com.forma.app.ui.components.FormaBanner
import com.forma.app.ui.components.FormaBannerStyle
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.screens.auth.AuthTextField
import com.forma.app.ui.screens.auth.PasswordRecoverySheet
import com.forma.app.ui.screens.auth.findActivity
import com.forma.app.ui.screens.profile.LegalPrivacySheet
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.security.SecureRandom

/** Login social pendente de confirmação de conta existente (PendingSocialSignIn do iOS). */
private data class PendingSocialSignIn(
    val provider: String,
    val providerId: String,
    val email: String?,
    val name: String,
    val idToken: String?,
    val nonce: String?,
    val authorizationCode: String?,
    val oauthRefreshToken: String?,
    val existingEmail: String
)

/**
 * Porte de Forma/Views/Auth/LoginView.swift (Android: sem Sign in with Apple; Google via OAuth 2.0 + PKCE).
 */
@Composable
fun LoginScreen(
    repository: FormaRepository,
    onLoginSuccess: () -> Unit
) {
    val colors = FormaTheme.Colors
    val auth = repository.auth
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isRegisterMode by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf("") }
    var emailText by remember { mutableStateOf("") }
    var passwordText by remember { mutableStateOf("") }

    var localErrorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordSheet by remember { mutableStateOf(false) }
    var showLegalPrivacySheet by remember { mutableStateOf(false) }
    var isGoogleAuthenticating by remember { mutableStateOf(false) }
    var showExistingAccountAlert by remember { mutableStateOf(false) }
    var pendingSocialSignIn by remember { mutableStateOf<PendingSocialSignIn?>(null) }
    var acceptedTermsAndLGPD by remember { mutableStateOf(false) }

    val isAuthenticating by auth.isAuthenticating.collectAsState()
    val authError by auth.errorMessage.collectAsState()

    fun handleEmailAuth() {
        localErrorMessage = null
        if (isRegisterMode && !acceptedTermsAndLGPD) {
            localErrorMessage = "Marque o aceite da Política de Privacidade (LGPD) e dos Termos de Uso para criar sua conta."
            return
        }
        scope.launch {
            try {
                if (isRegisterMode) auth.register(nameText, emailText, passwordText)
                else auth.login(emailText, passwordText)
                onLoginSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localErrorMessage = e.localizedMessage ?: e.toString()
            }
        }
    }

    fun handleGoogleSignIn() {
        val activity = context.findActivity() ?: return
        localErrorMessage = null
        isGoogleAuthenticating = true
        val rawNonce = randomNonceString()

        scope.launch {
            try {
                val userResult = GoogleAuthService.get(context).startSignIn(activity, auth.api, rawNonce)
                val resolvedProviderId = userResult.sub ?: "google_user_${kotlin.math.abs(rawNonce.hashCode())}"
                val resolvedName = userResult.name ?: "Usuário Google"
                val googleRevocableToken =
                    if (!userResult.refreshToken.isNullOrEmpty()) userResult.refreshToken else userResult.accessToken
                try {
                    auth.loginWithSocial(
                        provider = "GOOGLE",
                        providerId = resolvedProviderId,
                        email = userResult.email,
                        name = resolvedName,
                        idToken = userResult.idToken,
                        nonce = rawNonce,
                        isRegisterIntent = isRegisterMode,
                        confirmExisting = false,
                        oauthRefreshToken = googleRevocableToken
                    )
                    onLoginSuccess()
                } catch (e: FormaApiException.AccountAlreadyExists) {
                    pendingSocialSignIn = PendingSocialSignIn(
                        provider = "GOOGLE",
                        providerId = resolvedProviderId,
                        email = userResult.email,
                        name = resolvedName,
                        idToken = userResult.idToken,
                        nonce = rawNonce,
                        authorizationCode = null,
                        oauthRefreshToken = googleRevocableToken,
                        existingEmail = e.email.ifEmpty { userResult.email.orEmpty() }
                    )
                    showExistingAccountAlert = true
                }
            } catch (e: CancellationException) {
                // Usuário fechou a aba de login do Google
            } catch (e: Exception) {
                localErrorMessage = e.localizedMessage ?: e.toString()
            } finally {
                isGoogleAuthenticating = false
            }
        }
    }

    fun confirmPendingSocialSignIn() {
        val pending = pendingSocialSignIn ?: return
        pendingSocialSignIn = null
        localErrorMessage = null
        isGoogleAuthenticating = true
        scope.launch {
            try {
                auth.loginWithSocial(
                    provider = pending.provider,
                    providerId = pending.providerId,
                    email = pending.email,
                    name = pending.name,
                    idToken = pending.idToken,
                    nonce = pending.nonce,
                    isRegisterIntent = false,
                    confirmExisting = true,
                    authorizationCode = pending.authorizationCode,
                    oauthRefreshToken = pending.oauthRefreshToken
                )
                onLoginSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localErrorMessage = e.localizedMessage ?: e.toString()
            } finally {
                isGoogleAuthenticating = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.systemBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Header com Logo e Apresentação
        Column(
            modifier = Modifier.padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Forma",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(72.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = colors.brand.copy(alpha = 0.25f),
                        spotColor = colors.brand.copy(alpha = 0.25f)
                    )
                    .clip(RoundedCornerShape(16.dp))
            )
            Text(
                text = "Forma",
                color = colors.primaryText,
                style = FormaTypography.rounded(28f, FontWeight.ExtraBold)
            )
            Text(
                text = if (isRegisterMode) "Crie sua conta para gerenciar seus dados" else "Entre na sua conta para acessar seus dados",
                color = colors.secondaryText,
                style = FormaTypography.subheadline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        // Seletor de Modo: Entrar vs Criar Conta
        AuthSegmentedPicker(
            options = listOf("Entrar", "Criar Conta"),
            selectedIndex = if (isRegisterMode) 1 else 0,
            onSelect = { isRegisterMode = it == 1 },
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        // Formulário de E-mail / Senha
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnimatedVisibility(visible = isRegisterMode) {
                AuthTextField(
                    icon = sfSymbol("person.fill"),
                    placeholder = "Seu Nome Completo",
                    value = nameText,
                    onValueChange = { nameText = it }
                )
            }
            AuthTextField(
                icon = sfSymbol("envelope.fill"),
                placeholder = "E-mail",
                value = emailText,
                onValueChange = { emailText = it },
                keyboardType = KeyboardType.Email
            )
            AuthTextField(
                icon = sfSymbol("lock.fill"),
                placeholder = "Senha",
                value = passwordText,
                onValueChange = { passwordText = it },
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeAction = { handleEmailAuth() }
            )

            if (!isRegisterMode) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "Esqueci minha senha",
                        color = colors.brand,
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.formaPlainClickable { showForgotPasswordSheet = true }
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (acceptedTermsAndLGPD) Icons.Filled.CheckBox
                        else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (acceptedTermsAndLGPD) colors.brand else colors.secondaryText,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(22.dp)
                            .formaPlainClickable { acceptedTermsAndLGPD = !acceptedTermsAndLGPD }
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Li e aceito a Política de Privacidade (LGPD), os Termos de Uso e o tratamento dos meus dados de bem-estar.",
                            color = colors.secondaryText,
                            style = FormaTypography.caption,
                            modifier = Modifier.formaPlainClickable { acceptedTermsAndLGPD = !acceptedTermsAndLGPD }
                        )
                        Text(
                            text = "Ler Política, Termos (EULA) e Aviso de Saúde",
                            color = colors.brand,
                            style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.formaPlainClickable { showLegalPrivacySheet = true }
                        )
                    }
                }
            }
        }

        // Alerta de Erro
        (localErrorMessage ?: authError)?.let { error ->
            FormaBanner(
                message = error,
                style = FormaBannerStyle.Error,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // Botão Principal (Entrar / Cadastrar)
        val primaryShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .height(50.dp)
                .alpha(if (isAuthenticating) 0.5f else 1f)
                .shadow(
                    elevation = 8.dp,
                    shape = primaryShape,
                    ambientColor = colors.brand.copy(alpha = 0.3f),
                    spotColor = colors.brand.copy(alpha = 0.3f)
                )
                .clip(primaryShape)
                .background(Brush.horizontalGradient(listOf(colors.brand, colors.green)))
                .formaPlainClickable(enabled = !isAuthenticating) { handleEmailAuth() },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isAuthenticating) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
            Text(
                text = if (isRegisterMode) "Criar Minha Conta" else "Entrar com E-mail",
                color = Color.White,
                style = FormaTypography.system(16f, FontWeight.Bold)
            )
        }

        // Divisor "ou continue com"
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(colors.secondaryText.copy(alpha = 0.2f)))
            Text("ou continue com", color = colors.secondaryText, style = FormaTypography.caption)
            Box(Modifier.weight(1f).height(1.dp).background(colors.secondaryText.copy(alpha = 0.2f)))
        }

        // Botões de autenticação social (Apple Sign-In não existe no Android)
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val googleEnabled = !(isGoogleAuthenticating || isAuthenticating)
            val googleShape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .alpha(if (googleEnabled) 1f else 0.5f)
                    .clip(googleShape)
                    .background(colors.cardSurface)
                    .border(1.dp, colors.primary.copy(alpha = 0.12f), googleShape)
                    .formaPlainClickable(enabled = googleEnabled) { handleGoogleSignIn() },
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isGoogleAuthenticating) {
                    CircularProgressIndicator(color = colors.blue, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    // g.circle.fill
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(colors.blue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("g", color = Color.White, style = FormaTypography.system(15f, FontWeight.Bold))
                    }
                }
                Text(
                    text = if (isGoogleAuthenticating) "Conectando com Google..." else "Continuar com Google",
                    color = colors.primaryText,
                    style = FormaTypography.system(16f, FontWeight.SemiBold)
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .formaPlainClickable { showLegalPrivacySheet = true },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Recursos de IA na nuvem utilizam o Google Gemini (Google LLC) mediante sua autorização.",
                color = colors.secondaryText,
                style = FormaTypography.caption2,
                textAlign = TextAlign.Center
            )
            Text(
                "Ao continuar, você concorda com a Política de Privacidade (LGPD), Termos de Uso & Aviso de Saúde",
                color = colors.brand,
                style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showForgotPasswordSheet) {
        PasswordRecoverySheet(
            auth = auth,
            initialEmail = emailText,
            onDismiss = { showForgotPasswordSheet = false },
            onRecoverySuccess = onLoginSuccess
        )
    }
    if (showLegalPrivacySheet) {
        LegalPrivacySheet(onDismiss = { showLegalPrivacySheet = false })
    }
    if (showExistingAccountAlert) {
        AlertDialog(
            onDismissRequest = {
                showExistingAccountAlert = false
                pendingSocialSignIn = null
            },
            title = { Text("Conta já existente") },
            text = {
                Text("Já existe uma conta cadastrada com o e-mail ${pendingSocialSignIn?.existingEmail ?: "informado"}. Deseja entrar nessa conta?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showExistingAccountAlert = false
                    confirmPendingSocialSignIn()
                }) { Text("Sim, Entrar", color = colors.brand) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExistingAccountAlert = false
                    pendingSocialSignIn = null
                }) { Text("Cancelar", color = colors.brand) }
            },
            containerColor = colors.cardSurface,
            titleContentColor = colors.primaryText,
            textContentColor = colors.secondaryText
        )
    }
}

/** `Picker(.segmented)` do iOS: trilho `tertiaryFill`, seletor branco (claro) / cinza (escuro). */
@Composable
private fun AuthSegmentedPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val thumb = if (colors.isDark) Color(0xFF636366) else Color.White
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(colors.tertiaryFill)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .then(
                        if (selected) Modifier
                            .shadow(1.dp, RoundedCornerShape(7.dp))
                            .background(thumb, RoundedCornerShape(7.dp))
                        else Modifier
                    )
                    .formaPlainClickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = colors.primaryText,
                    style = FormaTypography.system(13f, if (selected) FontWeight.SemiBold else FontWeight.Normal)
                )
            }
        }
    }
}

private fun randomNonceString(length: Int = 32): String {
    val charset = "0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._"
    val random = SecureRandom()
    val bytes = ByteArray(length).also { random.nextBytes(it) }
    return buildString { bytes.forEach { append(charset[(it.toInt() and 0xFF) % charset.length]) } }
}
