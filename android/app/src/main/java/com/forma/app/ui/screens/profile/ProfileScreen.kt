package com.forma.app.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.forma.app.R
import com.forma.app.data.model.FitnessGoal
import com.forma.app.data.model.FocusMetric
import com.forma.app.data.model.Macros
import com.forma.app.data.model.UserProfile
import com.forma.app.data.repository.FormaRepository
import com.forma.app.ui.components.FormaBadge
import com.forma.app.ui.components.FormaBanner
import com.forma.app.ui.components.FormaBannerStyle
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.formaHumanizeErrorMessage
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.dismissKeyboardOnTap
import com.forma.app.ui.components.rememberFormaHaptics
import com.forma.app.ui.island.DynamicIslandState
import com.forma.app.ui.screens.evolution.ProfileInputValidator
import com.forma.app.ui.screens.evolution.fmt1
import com.forma.app.ui.screens.evolution.fmt2
import com.forma.app.ui.screens.evolution.swiftRound
import com.forma.app.ui.screens.evolution.symbolFor
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Porte de Forma/Views/ProfileView.swift
// Aba Perfil & Ajustes Fisiológicos com validação flexível, recálculo contextual e gestão de conta.

@Composable
fun ProfileScreen(
    repository: FormaRepository,
    islandState: DynamicIslandState,
    onLogout: () -> Unit
) {
    val colors = FormaTheme.Colors
    val scope = rememberCoroutineScope()
    val haptics = rememberFormaHaptics()
    val focusManager = LocalFocusManager.current

    val repoProfile by repository.userProfile.collectAsState()
    val weightEntries by repository.weightEntries.collectAsState()
    val workoutEntries by repository.workoutEntries.collectAsState()
    val routines by repository.workoutRoutines.collectAsState()
    val isAuthenticated by repository.isAuthenticated.collectAsState()
    val currentUserName by repository.auth.currentUserName.collectAsState()
    val currentUserEmail by repository.auth.currentUserEmail.collectAsState()
    val currentAuthProvider by repository.auth.currentAuthProvider.collectAsState()

    // Rascunho em memória do perfil (equivalente ao @Model Profile mutável do SwiftData).
    var draft by remember { mutableStateOf(repoProfile) }
    var nameText by remember { mutableStateOf(repoProfile.name) }
    var nameFocused by remember { mutableStateOf(false) }
    var weightInputText by remember { mutableStateOf(fmt1(repoProfile.currentWeight)) }
    var heightInputText by remember { mutableStateOf(fmt2(repoProfile.heightMeters)) }
    var isWeightFocused by remember { mutableStateOf(false) }
    var isHeightFocused by remember { mutableStateOf(false) }
    val weightFocusRequester = remember { FocusRequester() }

    var savedToastMessage by remember { mutableStateOf<String?>(null) }
    var toastToken by remember { mutableIntStateOf(0) }
    var isLoggingOut by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var showDeleteAccountAlert by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
    var showLegalPrivacySheet by remember { mutableStateOf(false) }
    var showChangePasswordSheet by remember { mutableStateOf(false) }

    // .task { ensureSingleProfile; syncInputStrings }
    LaunchedEffect(Unit) {
        val active = repository.ensureSingleProfile()
        draft = active
        if (!nameFocused) nameText = active.name
        if (!isWeightFocused) weightInputText = fmt1(active.currentWeight)
        if (!isHeightFocused) heightInputText = fmt2(active.heightMeters)
    }
    // Mudanças externas (recálculo, pesagem remota, sync) refletem no rascunho.
    LaunchedEffect(repoProfile) {
        draft = repoProfile
        if (!nameFocused) nameText = repoProfile.name
    }
    // .onChange(of: profile.currentWeight / profile.height)
    LaunchedEffect(draft.currentWeight) {
        if (!isWeightFocused) weightInputText = fmt1(draft.currentWeight)
    }
    LaunchedEffect(draft.heightMeters) {
        if (!isHeightFocused) heightInputText = fmt2(draft.heightMeters)
    }
    // Toast some após 3s.
    LaunchedEffect(toastToken) {
        if (savedToastMessage != null) {
            delay(3000)
            savedToastMessage = null
        }
    }

    // Persiste edições pendentes ao sair da aba (SwiftData salva continuamente no iOS).
    val latestDraft by rememberUpdatedState(draft)
    val latestName by rememberUpdatedState(nameText)
    DisposableEffect(Unit) {
        onDispose {
            if (repository.auth.isAuthenticated.value) {
                val pending = latestDraft.copy(name = latestName)
                if (pending != repository.userProfile.value) repository.updateProfile(pending)
            }
        }
    }

    val fullCtx = remember(repoProfile, weightEntries, workoutEntries, routines) { repository.buildRecalculationContext() }
    val contextualTdee = draft.contextualMetrics(context = fullCtx).contextualTdee

    // MARK: - Helpers

    fun showToast(text: String) {
        haptics.success()
        savedToastMessage = text
        toastToken += 1
    }

    fun commit(p: UserProfile) {
        draft = p
        repository.updateProfile(p)
    }

    fun recalculateIfNeededAndSave(base: UserProfile) {
        val p = base.copy(autoRecalculateGoals = true)
        val updated = repository.recalculateProfileWithFullContext(profile = p)
        if (updated != null) draft = updated else commit(p)
        repository.syncTodayData()
    }

    fun syncTodayWeightEntry(weight: Double) {
        val latest = repository.weightEntries.value.firstOrNull()?.weight
        if (latest != null && abs(latest - weight) < 0.05) return
        scope.launch {
            try {
                repository.createWeightEntryRemoteFirst(weight, System.currentTimeMillis(), "Atualizado nos ajustes")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // try? do iOS
            }
        }
    }

    fun clampWeight(w: Double) = max(30.0, min(300.0, swiftRound(w * 10.0) / 10.0))

    /** Pré-visualização ao vivo (digitação/arraste): atualiza o rascunho e as metas sem persistir. */
    fun previewWeight(w: Double) {
        val p = draft.copy(currentWeight = w, autoRecalculateGoals = true)
        draft = if (p.onboardingCompleted) p.recalculateDailyGoals(w, fullCtx) else p
    }

    fun applyWeightChange(newWeight: Double, syncText: Boolean, recordEntry: Boolean) {
        val clamped = clampWeight(newWeight)
        val p = draft.copy(currentWeight = clamped, autoRecalculateGoals = true)
        if (syncText) weightInputText = fmt1(clamped)
        if (recordEntry) syncTodayWeightEntry(clamped)
        val updated = repository.recalculateProfileWithFullContext(clamped, p)
        if (updated != null) draft = updated else commit(p)
        repository.syncTodayData()
    }

    fun previewHeight(h: Double) {
        val clamped = max(1.0, min(2.5, swiftRound(h * 100.0) / 100.0))
        val p = draft.copy(heightMeters = clamped, autoRecalculateGoals = true)
        draft = if (p.onboardingCompleted) p.recalculateDailyGoals(null, fullCtx) else p
    }

    fun applyHeightChange(newHeight: Double, syncText: Boolean) {
        val clamped = max(1.0, min(2.5, swiftRound(newHeight * 100.0) / 100.0))
        val p = draft.copy(heightMeters = clamped, autoRecalculateGoals = true)
        if (syncText) heightInputText = fmt2(clamped)
        val updated = repository.recalculateProfileWithFullContext(profile = p)
        if (updated != null) draft = updated else commit(p)
        repository.syncTodayData()
    }

    fun suggestOptimalMacros() {
        val metrics = draft.contextualMetrics(context = fullCtx)
        val activeCalories = if (draft.targetCalorieIntake > 0) draft.targetCalorieIntake else metrics.suggestedCalories
        val protein = metrics.proteinGrams
        val fat = metrics.fatGrams
        val minCarbs = when (draft.goal) {
            FitnessGoal.GAIN -> 120.0
            FitnessGoal.LOSE -> 60.0
            FitnessGoal.MAINTAIN -> 80.0
        }
        val remainingCalories = max(0.0, activeCalories - (protein * 4.0) - (fat * 9.0))
        val carbs = max(minCarbs, swiftRound(remainingCalories / 4.0))
        val macros = Macros(protein = protein, fat = fat, carbohydrates = carbs)
        commit(draft.copy(targetProtein = macros.protein, targetFat = macros.fat, targetCarbohydrates = macros.carbohydrates))
        repository.syncTodayData()
    }

    fun saveSettings() {
        var p = draft.copy(name = nameText)
        ProfileInputValidator.parseFlexibleHeight(heightInputText)?.let { parsedH ->
            val clamped = max(1.0, min(2.5, swiftRound(parsedH * 100.0) / 100.0))
            p = p.copy(heightMeters = clamped, autoRecalculateGoals = true)
            heightInputText = fmt2(clamped)
            repository.recalculateProfileWithFullContext(profile = p)?.let { p = it.copy(name = nameText) }
        }
        var weightChangedTo: Double? = null
        ProfileInputValidator.parseFlexibleWeight(weightInputText)?.let { parsedW ->
            val clamped = max(30.0, min(300.0, swiftRound(parsedW * 10.0) / 10.0))
            val hadDiff = abs(p.currentWeight - clamped) >= 0.05
            p = p.copy(currentWeight = clamped, autoRecalculateGoals = true)
            weightInputText = fmt1(clamped)
            repository.recalculateProfileWithFullContext(clamped, p)?.let { p = it.copy(name = nameText) }
            weightChangedTo = if (hadDiff) clamped else null
        }
        draft = p
        repository.updateProfile(p)

        focusManager.clearFocus()

        val newW = weightChangedTo
        scope.launch {
            try {
                if (newW != null) {
                    repository.createWeightEntryRemoteFirst(newW, System.currentTimeMillis(), "Atualizado nos ajustes")
                }
                val latest = repository.userProfile.value.copy(name = nameText)
                repository.saveProfileRemoteFirst(latest)
                showToast("Configurações salvas no banco e sincronizadas!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Erro ao sincronizar: ${formaHumanizeErrorMessage(e.message.orEmpty())}")
            }
        }
    }

    fun performLogout() {
        if (isLoggingOut || isDeletingAccount) return
        isLoggingOut = true
        scope.launch {
            if (isAuthenticated) {
                repository.sync.cancelPendingSyncs()
                try {
                    repository.sync.sync()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // a sincronização trata/loga seus próprios erros (iOS: await syncManager.sync)
                }
            }
            isLoggingOut = false
            repository.auth.logout()
            onLogout()
        }
    }

    fun performPermanentAccountDeletion() {
        if (isDeletingAccount) return
        deleteErrorMessage = null
        isDeletingAccount = true
        scope.launch {
            try {
                repository.auth.deleteAccount()
                isDeletingAccount = false
                onLogout()
            } catch (e: CancellationException) {
                isDeletingAccount = false
                throw e
            } catch (e: Exception) {
                deleteErrorMessage = e.message.orEmpty()
                isDeletingAccount = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
    ) {
        AnimatedVisibility(
            visible = savedToastMessage != null,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.energyGreen)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(symbolFor("checkmark.circle.fill"), null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    text = savedToastMessage.orEmpty(),
                    color = Color.White,
                    style = FormaTypography.system(13f, FontWeight.Bold)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .dismissKeyboardOnTap()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FormaTheme.Spacing.screenHorizontal)
                .padding(top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(FormaTheme.Spacing.sectionGap)
        ) {
            // profileHeaderCard
            FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.profile),
                        contentDescription = "Foto",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, colors.primary.copy(alpha = 0.08f), CircleShape)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        BasicTextField(
                            value = nameText,
                            onValueChange = { nameText = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            textStyle = FormaTypography.rounded(22f, FontWeight.Bold).copy(color = colors.primaryText),
                            cursorBrush = SolidColor(colors.brand),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { state ->
                                    val was = nameFocused
                                    nameFocused = state.isFocused
                                    if (was && !state.isFocused && nameText != draft.name) {
                                        commit(draft.copy(name = nameText))
                                    }
                                },
                            decorationBox = { inner ->
                                if (nameText.isEmpty()) {
                                    Text(
                                        text = "Seu Nome",
                                        color = colors.tertiaryText,
                                        style = FormaTypography.rounded(22f, FontWeight.Bold)
                                    )
                                }
                                inner()
                            }
                        )
                        Text(
                            text = if (draft.email.isEmpty()) (currentUserEmail ?: "Conta Forma") else draft.email,
                            color = colors.secondaryText,
                            style = FormaTypography.subheadline
                        )
                    }
                }
            }

            // fitnessGoalCard
            FormaCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(symbolFor("target"), null, tint = colors.brand, modifier = Modifier.size(20.dp))
                        Text(text = "Objetivo Corporal", color = colors.primaryText, style = FormaTypography.headline)
                    }

                    FormaSegmentedPicker(
                        options = FitnessGoal.values().map { it.title },
                        selectedIndex = draft.goal.ordinal,
                        onSelect = { index ->
                            val goal = FitnessGoal.values()[index]
                            if (goal != draft.goal) recalculateIfNeededAndSave(draft.copy(goal = goal))
                        }
                    )

                    Text(text = draft.goal.subtitle, color = colors.secondaryText, style = FormaTypography.caption)

                    HorizontalDivider(color = colors.primary.copy(alpha = 0.06f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Foco na Tela Inicial",
                            color = colors.primaryText,
                            style = FormaTypography.subheadline.copy(fontWeight = FontWeight.SemiBold)
                        )
                        FormaSegmentedPicker(
                            options = FocusMetric.values().map { it.title },
                            selectedIndex = draft.effectiveFocus.ordinal,
                            onSelect = { index -> commit(draft.copy(preferredFocus = FocusMetric.values()[index])) }
                        )
                        if (draft.goal == FitnessGoal.GAIN) {
                            Text(
                                text = buildAnnotatedString {
                                    append("Recomendamos manter o foco em ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Consumidas") }
                                    append(" para bater sua meta diária de ganho.")
                                },
                                color = colors.brand,
                                style = FormaTypography.caption
                            )
                        }
                    }
                }
            }

            ProfileTargetWeightCard(
                profile = draft,
                onProfileChange = { draft = it },
                onAdjustCurrentWeight = { value, syncText, record ->
                    val clamped = clampWeight(value)
                    if (record) {
                        applyWeightChange(clamped, syncText, true)
                    } else {
                        if (syncText) weightInputText = fmt1(clamped)
                        previewWeight(clamped)
                    }
                },
                onFinishWeightSlider = {
                    val w = draft.currentWeight
                    syncTodayWeightEntry(w)
                    val updated = repository.recalculateProfileWithFullContext(w, draft)
                    if (updated != null) draft = updated else commit(draft)
                    repository.syncTodayData()
                },
                onFinishTargetSlider = { recalculateIfNeededAndSave(draft) },
                onFocusWeightField = { weightFocusRequester.requestFocus() }
            )

            ProfileBiometricsCard(
                profile = draft,
                heightInputText = heightInputText,
                onHeightInputTextChange = { heightInputText = it },
                weightInputText = weightInputText,
                onWeightInputTextChange = { weightInputText = it },
                weightFocusRequester = weightFocusRequester,
                contextualTdee = contextualTdee,
                onApplyHeight = { parsed, syncText ->
                    if (syncText) applyHeightChange(parsed, true) else previewHeight(parsed)
                },
                onApplyWeight = { parsed, syncText, record ->
                    if (syncText || record) applyWeightChange(parsed, syncText, record) else previewWeight(clampWeight(parsed))
                },
                onHeightFocusChange = { isHeightFocused = it },
                onWeightFocusChange = { isWeightFocused = it },
                onRecalculateTap = {
                    val updated = repository.recalculateProfileWithFullContext(profile = draft)
                    if (updated != null) draft = updated
                    repository.syncTodayData()
                    showToast("Metas recalculadas pela Inteligência Forma!")
                }
            )

            ProfileDailyGoalsCard(
                profile = draft,
                onProfileChange = { draft = it },
                onContextSliderEnded = { recalculateIfNeededAndSave(draft) },
                onValueChangeFinished = { commit(draft) }
            )

            ProfileMacroGoalsCard(
                profile = draft,
                onProfileChange = { draft = it },
                onSuggestTap = {
                    suggestOptimalMacros()
                    showToast("Proporção ideal de macronutrientes aplicada!")
                },
                onValueChangeFinished = { commit(draft) }
            )

            // accountCard
            AccountCard(
                isAuthenticated = isAuthenticated,
                provider = currentAuthProvider,
                userName = currentUserName,
                userEmail = currentUserEmail,
                deleteErrorMessage = deleteErrorMessage,
                isLoggingOut = isLoggingOut,
                isDeletingAccount = isDeletingAccount,
                onLegal = { showLegalPrivacySheet = true },
                onChangePassword = { showChangePasswordSheet = true },
                onLogout = { performLogout() },
                onDelete = { showDeleteAccountAlert = true }
            )

            // saveSettingsButton
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.brand)
                    .formaPlainClickable { saveSettings() }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(symbolFor("checkmark"), null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(text = "Salvar Configurações", color = Color.White, style = FormaTypography.headline)
            }
        }
    }

    if (showLegalPrivacySheet) {
        LegalPrivacySheet(onDismiss = { showLegalPrivacySheet = false })
    }

    if (showChangePasswordSheet) {
        ChangePasswordSheet(
            repository = repository,
            onDismiss = { showChangePasswordSheet = false },
            onSaved = { showToast("Senha alterada com sucesso!") }
        )
    }

    if (showDeleteAccountAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountAlert = false },
            title = { Text("Excluir conta permanentemente?") },
            text = {
                Text(
                    "Esta ação não pode ser desfeita. Seu cadastro, refeições, treinos, fichas, pesagens e registros de água serão apagados definitivamente do Forma e da nuvem, e os tokens de acesso do Sign in with Apple e Google serão revogados."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAccountAlert = false
                    performPermanentAccountDeletion()
                }) { Text("Excluir Minha Conta", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountAlert = false }) { Text("Cancelar", color = colors.brand) }
            }
        )
    }
}

@Composable
private fun AccountCard(
    isAuthenticated: Boolean,
    provider: String?,
    userName: String?,
    userEmail: String?,
    deleteErrorMessage: String?,
    isLoggingOut: Boolean,
    isDeletingAccount: Boolean,
    onLegal: () -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = FormaTheme.Colors
    FormaCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(symbolFor("person.crop.circle.fill"), null, tint = colors.brand, modifier = Modifier.size(20.dp))
                Text(
                    text = "Conta",
                    color = colors.primaryText,
                    style = FormaTypography.headline,
                    modifier = Modifier.weight(1f)
                )
                if (isAuthenticated) {
                    FormaBadge(text = "Ativa", tint = colors.energyGreen)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.brandSoft),
                    contentAlignment = Alignment.Center
                ) {
                    if (provider == "GOOGLE") {
                        // g.circle.fill
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colors.brand),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "g",
                                color = Color.White,
                                style = FormaTypography.system(15f, FontWeight.Bold),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Icon(symbolFor("person.fill"), null, tint = colors.brand, modifier = Modifier.size(22.dp))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = userName ?: "Usuário Forma",
                        color = colors.primaryText,
                        style = FormaTypography.system(16f, FontWeight.SemiBold)
                    )
                    if (!userEmail.isNullOrEmpty()) {
                        Text(text = userEmail, color = colors.secondaryText, style = FormaTypography.caption)
                    }
                }
            }

            HorizontalDivider(color = colors.separator, modifier = Modifier.padding(vertical = 2.dp))

            AccountRow(icon = "hand.raised.shield.fill", title = "Privacidade, Saúde & Termos de Uso", onClick = onLegal)

            if ((provider ?: "EMAIL") == "EMAIL") {
                AccountRow(icon = "key.fill", title = "Alterar Senha", onClick = onChangePassword)
            }

            deleteErrorMessage?.let { FormaBanner(message = it, style = FormaBannerStyle.Error) }

            HorizontalDivider(color = colors.separator, modifier = Modifier.padding(vertical = 2.dp))

            val busy = isLoggingOut || isDeletingAccount
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (busy) 0.5f else 1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.danger.copy(alpha = 0.08f))
                    .formaPlainClickable(enabled = !busy, onClick = onLogout)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoggingOut) {
                    CircularProgressIndicator(color = colors.danger, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Sincronizando e saindo...",
                        color = colors.danger,
                        style = FormaTypography.subheadline.copy(fontWeight = FontWeight.SemiBold)
                    )
                } else {
                    Icon(symbolFor("rectangle.portrait.and.arrow.right"), null, tint = colors.danger, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Sair da Conta",
                        color = colors.danger,
                        style = FormaTypography.subheadline.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (busy) 0.5f else 1f)
                    .formaPlainClickable(enabled = !busy, onClick = onDelete)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDeletingAccount) {
                    CircularProgressIndicator(color = colors.danger, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    Text(
                        text = "Excluindo conta...",
                        color = colors.danger.copy(alpha = 0.85f),
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold)
                    )
                } else {
                    Icon(symbolFor("trash"), null, tint = colors.danger.copy(alpha = 0.85f), modifier = Modifier.size(13.dp))
                    Text(
                        text = "Excluir Minha Conta",
                        color = colors.danger.copy(alpha = 0.85f),
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountRow(icon: String, title: String, onClick: () -> Unit) {
    val colors = FormaTheme.Colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .formaPlainClickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(symbolFor(icon), null, tint = colors.brand, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            color = colors.primaryText,
            style = FormaTypography.subheadline.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f)
        )
        Icon(symbolFor("chevron.right"), null, tint = colors.secondaryText, modifier = Modifier.size(16.dp))
    }
}
